package com.kimjisub.launchpad.unipack.runner

import android.os.SystemClock
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

class LedRunner(
	private val unipack: UniPack,
	private val listener: Listener,
	private val chain: ChainObserver,
	private val loopDelay: Long = 4L,
) {
	companion object {
		private const val CIRCULAR_LED_COUNT = 36
	}

	private val btnLed: Array<Array<Led?>> = Array(unipack.buttonX) { arrayOfNulls<Led>(unipack.buttonY) }
	private val cirLed: Array<Led?> = arrayOfNulls<Led>(CIRCULAR_LED_COUNT)
	private var ledAnimationStates: MutableList<LedAnimationState> = mutableListOf()
	private val ledAnimationStatesAdd: MutableList<LedAnimationState> = mutableListOf()

	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	private var job: Job? = null
	private var pausedAt: Long? = null

	val active: Boolean
		get() = job?.isActive == true

	interface Listener {
		fun onPadLedTurnOn(x: Int, y: Int, color: Int, velocity: Int)
		fun onPadLedTurnOff(x: Int, y: Int)
		fun onChainLedTurnOn(c: Int, color: Int, velocity: Int)
		fun onChainLedTurnOff(c: Int)
		/** A keyLED `c` event. Called on the runner thread; the listener decides where to apply it. */
		fun onChainChange(c: Int)

		/**
		 * One tick's changes in play order, called on the runner thread after its lock is released so a
		 * slow receiver never keeps eventOn/eventOff (the UI thread) waiting for the lock.
		 */
		fun onLedChanges(changes: List<LedChange>) {
			for (change in changes) change.dispatchTo(this)
		}
	}

	sealed interface LedChange {
		fun dispatchTo(listener: Listener)

		data class PadOn(val x: Int, val y: Int, val color: Int, val velocity: Int) : LedChange {
			override fun dispatchTo(listener: Listener) = listener.onPadLedTurnOn(x, y, color, velocity)
		}

		data class PadOff(val x: Int, val y: Int) : LedChange {
			override fun dispatchTo(listener: Listener) = listener.onPadLedTurnOff(x, y)
		}

		data class ChainLedOn(val c: Int, val color: Int, val velocity: Int) : LedChange {
			override fun dispatchTo(listener: Listener) = listener.onChainLedTurnOn(c, color, velocity)
		}

		data class ChainLedOff(val c: Int) : LedChange {
			override fun dispatchTo(listener: Listener) = listener.onChainLedTurnOff(c)
		}

		data class ChainChange(val c: Int) : LedChange {
			override fun dispatchTo(listener: Listener) = listener.onChainChange(c)
		}
	}

	private fun loop() {
		val changes = ArrayList<LedChange>()
		synchronized(this) {
			val currTime = SystemClock.elapsedRealtime()
			for (state in ledAnimationStates) {
				if (state.isPlaying && !state.isShutdown) {
					// Init if First
					if (state.delay == 0L) state.delay = currTime
					var processed = 0
					while (true) {
						// Counting Up Loop Progress
						val ledEvents = state.ledAnimation?.ledEvents ?: break
						// An empty keyLED file gives an animation with no events; with loop 0 the old code
						// spun into ledEvents[0] on an empty list (Crashlytics a1376611).
						if (ledEvents.isEmpty()) {
							state.isPlaying = false
							break
						}
						// An animation with no delay lines never pushes state.delay past currTime, so
						// this loop would run forever while holding the monitor and the next padTouch
						// would block into an ANR. One tick may consume at most one full pass per loop.
						// Only endless (loop 0) animations reach the cap: they yield to the next tick and
						// keep playing, so eventOff still shuts them down and turns their LEDs off. The
						// backlog is dropped, otherwise a strobe shorter than a tick falls further behind
						// real time every tick and a GC stall leaves it lagging for many ticks.
						val loopCount = state.ledAnimation.loop.coerceAtLeast(1)
						if (++processed > ledEvents.size * loopCount + 1) {
							state.delay = currTime
							break
						}
						if (state.index >= ledEvents.size) {
							state.loopProgress++
							state.index = 0
						}
						// Stop if Loop is Done
						if (state.ledAnimation.loop != 0 && state.ledAnimation.loop <= state.loopProgress) {
							state.isPlaying = false
							break
						}
						if (state.delay <= currTime) {
							try {
								when (val event = state.ledAnimation.ledEvents[state.index]) {
									is LedAnimation.LedEvent.On -> {
										val x = event.x
										val y = event.y
										val color = event.color
										val velocity = event.velocity

										// Recorded after the table write, which rejects a coordinate outside the grid:
										// the change is applied later, outside the catch below.
										if (x != -1) {
											btnLed[x][y] = Led(state.buttonX, state.buttonY, state.chainAtCreation)
											changes.add(LedChange.PadOn(x, y, color, velocity))
										} else {
											cirLed[y] = Led(state.buttonX, state.buttonY, state.chainAtCreation)
											changes.add(LedChange.ChainLedOn(y, color, velocity))
										}
									}

									is LedAnimation.LedEvent.Off -> {
										val x = event.x
										val y = event.y

										if (x != -1) {
											if (btnLed[x][y]?.equal(state.buttonX, state.buttonY, state.chainAtCreation) == true) {
												changes.add(LedChange.PadOff(x, y))
												btnLed[x][y] = null
											}
										} else {
											if (cirLed[y]?.equal(state.buttonX, state.buttonY, state.chainAtCreation) == true) {
												changes.add(LedChange.ChainLedOff(y))
												cirLed[y] = null
											}
										}
									}

									is LedAnimation.LedEvent.Delay -> {
										state.delay += event.delay.toLong()
									}

									is LedAnimation.LedEvent.Chain -> {
										// Not chain.value here: ChainObserver runs its observers synchronously and
										// they touch UI state, so the listener applies the change on main.
										changes.add(LedChange.ChainChange(event.chain))
									}
								}
							} catch (ex: IndexOutOfBoundsException) {
								Log.err("LED event index out of bounds", ex)
							}
						} else break
						state.index++
					}
				} else if (state.isShutdown) {
					for (x in 0 until unipack.buttonX) {
						for (y in 0 until unipack.buttonY) {
							if (btnLed[x][y]?.equal(state.buttonX, state.buttonY, state.chainAtCreation) == true) {
								changes.add(LedChange.PadOff(x, y))
								btnLed[x][y] = null
							}
						}
					}
					for (y in cirLed.indices) {
						if (cirLed[y]?.equal(state.buttonX, state.buttonY, state.chainAtCreation) == true) {
							changes.add(LedChange.ChainLedOff(y))
							cirLed[y] = null
						}
					}
					state.remove = true
				} else {
					state.remove = true
				}
			}
			for (item in ledAnimationStatesAdd)
				ledAnimationStates.add(item)
			ledAnimationStatesAdd.clear()
			ledAnimationStates.removeAll { it.remove }
		}
		if (changes.isNotEmpty()) listener.onLedChanges(changes)
	}


	fun launch() {
		Log.thread("[Led] 1. Request Coroutine")
		if (job?.isActive != true) {
			// Finite animations survive stop() (eventOffAll only shuts loop 0 down). Their `delay` is an
			// absolute deadline, so it is pushed back by the paused time: resuming neither replays what was
			// scheduled while stopped in one burst nor cuts short a wait that was still pending.
			synchronized(this) {
				pausedAt?.let { since ->
					val pausedFor = SystemClock.elapsedRealtime() - since
					for (state in ledAnimationStates) if (state.delay != 0L) state.delay += pausedFor
				}
				pausedAt = null
			}
			job = scope.launch {
				Log.thread("[Led] 2. Start Coroutine")
				while (isActive) {
					val millis = measureTimeMillis {
						loop()
					}
					delay((loopDelay - millis).coerceAtLeast(0))
				}
				Log.thread("[Led] 4. End Coroutine")
			}
		}
	}

	fun stop() {
		Log.thread("[Led] 3. Request Stop")
		val wasActive = active
		job?.cancel()
		job = null
		synchronized(this) {
			if (wasActive) pausedAt = SystemClock.elapsedRealtime()
			ledAnimationStatesAdd.clear()
		}
	}

	// Functions

	private fun searchEvent(x: Int, y: Int, chain: Int): LedAnimationState? {
		synchronized(this) {
			for (state in ledAnimationStates) {
				if (state.equal(x, y, chain)) {
					return state
				}
			}
			return null
		}
	}

	fun isEventExist(x: Int, y: Int, chain: Int): Boolean = searchEvent(x, y, chain) != null

	fun isEventExist(x: Int, y: Int): Boolean {
		synchronized(this) {
			return ledAnimationStates.any { it.buttonX == x && it.buttonY == y }
		}
	}

	fun eventOn(x: Int, y: Int) {
		if (active) {
			synchronized(this) {
				val currentChain = chain.value
				for (state in ledAnimationStates) {
					if (state.equal(x, y, currentChain)) {
						state.isShutdown = true
					}
				}
				val state = LedAnimationState(x, y)
				if (state.noError) ledAnimationStatesAdd.add(state)
			}
		}
	}

	fun eventOff(x: Int, y: Int) {
		if (active) {
			synchronized(this) {
				val currentChain = chain.value
				for (state in ledAnimationStates) {
					if (state.equal(x, y, currentChain) && state.ledAnimation?.loop == 0) {
						state.isShutdown = true
					}
				}
			}
		}
	}

	fun eventOffAll(x: Int, y: Int) {
		// No `active` guard: ledInit() calls this right after stop() to shut the looping
		// animations down; with the guard they stayed isPlaying and replayed in a burst on relaunch.
		synchronized(this) {
			for (state in ledAnimationStates) {
				if (state.buttonX == x && state.buttonY == y && state.ledAnimation?.loop == 0) {
					state.isShutdown = true
				}
			}
		}
	}

	// Represents a single illuminated LED.
	inner class Led(
		val buttonX: Int,
		val buttonY: Int,
		val chain: Int,
	) {
		fun equal(buttonX: Int, buttonY: Int, chain: Int): Boolean {
			return this.buttonX == buttonX && this.buttonY == buttonY && this.chain == chain
		}
	}

	// Tracks the execution state of a LedAnimation (a collection of LED events).
	inner class LedAnimationState(val buttonX: Int, val buttonY: Int) {
		var index = 0
		var delay: Long = 0
		var isPlaying = true
		var isShutdown = false
		var remove = false
		var loopProgress = 0

		val chainAtCreation: Int = chain.value

		val ledAnimation: LedAnimation?
		val noError
			get() = ledAnimation != null

		fun equal(buttonX: Int, buttonY: Int, chain: Int): Boolean {
			return this.buttonX == buttonX && this.buttonY == buttonY && this.chainAtCreation == chain
		}

		init {
			val animation: LedAnimation? = unipack.ledGet(chain.value, buttonX, buttonY)
			unipack.ledPush(chain.value, buttonX, buttonY)

			ledAnimation = animation
		}
	}
}

/**
 * Carries [LedRunner.LedChange]s from the runner thread to one consumer thread in order, with at most
 * one drain scheduled at a time however many changes pile up. Posting a task per change let a pack
 * with hundreds of thousands of changes in one tick bury the main thread's queue for seconds.
 */
class LedChangeQueue(private val scheduleDrain: () -> Unit) {
	private val pending = ArrayDeque<LedRunner.LedChange>()
	private var drainScheduled = false // guarded by pending

	fun offer(changes: List<LedRunner.LedChange>) {
		val schedule = synchronized(pending) {
			pending.addAll(changes)
			!drainScheduled.also { drainScheduled = true }
		}
		if (schedule) scheduleDrain()
	}

	/**
	 * Removes up to [max] changes in the order they were offered. When some remain another drain is
	 * scheduled, so the consumer thread can handle input between the portions of a large backlog.
	 */
	fun drain(max: Int = Int.MAX_VALUE): List<LedRunner.LedChange> {
		val taken: List<LedRunner.LedChange>
		val more: Boolean
		synchronized(pending) {
			val count = minOf(max, pending.size)
			taken = ArrayList<LedRunner.LedChange>(count).apply { repeat(count) { add(pending.removeFirst()) } }
			more = pending.isNotEmpty()
			drainScheduled = more
		}
		if (more) scheduleDrain()
		return taken
	}
}
