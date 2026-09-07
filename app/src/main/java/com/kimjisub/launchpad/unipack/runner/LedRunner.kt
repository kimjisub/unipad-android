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

	val active: Boolean
		get() = job?.isActive == true

	interface Listener {
		fun onPadLedTurnOn(x: Int, y: Int, color: Int, velocity: Int)
		fun onPadLedTurnOff(x: Int, y: Int)
		fun onChainLedTurnOn(c: Int, color: Int, velocity: Int)
		fun onChainLedTurnOff(c: Int)
		/** A keyLED `c` event. Called on the runner thread; the listener decides where to apply it. */
		fun onChainChange(c: Int)
	}

	private fun loop() {
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
						val loopCount = state.ledAnimation.loop.coerceAtLeast(1)
						if (++processed > ledEvents.size * loopCount + 1) {
							state.isPlaying = false
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

										if (x != -1) {
											listener.onPadLedTurnOn(x, y, color, velocity)
											btnLed[x][y] = Led(state.buttonX, state.buttonY, state.chainAtCreation)
										} else {
											listener.onChainLedTurnOn(y, color, velocity)
											cirLed[y] = Led(state.buttonX, state.buttonY, state.chainAtCreation)
										}
									}

									is LedAnimation.LedEvent.Off -> {
										val x = event.x
										val y = event.y

										if (x != -1) {
											if (btnLed[x][y]?.equal(state.buttonX, state.buttonY, state.chainAtCreation) == true) {
												listener.onPadLedTurnOff(x, y)
												btnLed[x][y] = null
											}
										} else {
											if (cirLed[y]?.equal(state.buttonX, state.buttonY, state.chainAtCreation) == true) {
												listener.onChainLedTurnOff(y)
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
										listener.onChainChange(event.chain)
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
								listener.onPadLedTurnOff(x, y)
								btnLed[x][y] = null
							}
						}
					}
					for (y in cirLed.indices) {
						if (cirLed[y]?.equal(state.buttonX, state.buttonY, state.chainAtCreation) == true) {
							listener.onChainLedTurnOff(y)
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
	}


	fun launch() {
		Log.thread("[Led] 1. Request Coroutine")
		if (job?.isActive != true) {
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
		job?.cancel()
		job = null
		synchronized(this) { ledAnimationStatesAdd.clear() }
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
