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
import java.util.PriorityQueue
import kotlin.system.measureTimeMillis

class LedRunner(
	private val unipack: UniPack,
	private val listener: Listener,
	private val chain: ChainObserver,
	private val loopDelay: Long = 4L,
) {
	companion object {
		private const val CIRCULAR_LED_COUNT = 36

		// How long a finished animation is kept for a press that reaches the runner after it finished but
		// was placed before: autoplay's presses come by way of the main thread, a few milliseconds late.
		private const val LATE_PRESS_MS = 1_000L
	}

	private val btnLed: Array<Array<Led?>> = Array(unipack.buttonX) { arrayOfNulls<Led>(unipack.buttonY) }
	private val cirLed: Array<Led?> = arrayOfNulls<Led>(CIRCULAR_LED_COUNT)
	private var ledAnimationStates: MutableList<LedAnimationState> = mutableListOf()
	private val ledAnimationStatesAdd: MutableList<LedAnimationState> = mutableListOf()
	// The endless animation the press of each held pad (x, y) started, null when none. Its release ends
	// that animation even when the chain changed while the pad was held.
	private val heldLoop: Array<Array<LedAnimationState?>> = Array(unipack.buttonX) { arrayOfNulls<LedAnimationState>(unipack.buttonY) }
	// Numbers presses and releases, so steps due in the same millisecond play in the order they were asked for.
	private var requests = 0L

	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

	// Held from a tick's first computation until the listener has its changes. It is separate from the
	// runner lock so that eventOn/eventOff never wait for a delivery, while stop() and afterDelivery()
	// can order themselves between whole ticks.
	private val deliveryLock = Any()
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

	private fun loop() = synchronized(deliveryLock) {
		val changes = ArrayList<LedChange>()
		synchronized(this) {
			val currTime = SystemClock.elapsedRealtime()
			ledAnimationStates.addAll(ledAnimationStatesAdd)
			ledAnimationStatesAdd.clear()
			// Animations interleave in the order their steps fell due, so a late tick still gives an LED that
			// two animations write a few milliseconds apart the colours in the pack's order.
			val due = PriorityQueue<Step>()
			for (state in ledAnimationStates) {
				state.processed = 0
				state.nextStep(currTime)?.let(due::add)
			}
			while (true) {
				val step = due.poll() ?: break
				if (step.isShutdown) step.state.shutDown(changes) else step.state.playEvent(step.at, changes)
				step.state.nextStep(currTime)?.let(due::add)
			}
			ledAnimationStates.removeAll { it.remove }
		}
		if (changes.isNotEmpty()) listener.onLedChanges(changes)
	}

	/**
	 * An animation's next event, or its shutdown, due [at]. Steps due in the same millisecond play the
	 * events of animations already running, then the shutdowns the presses and releases of that
	 * millisecond cause, then the first events of the animations those presses start; each group in the
	 * order of the presses and releases ([order]).
	 */
	internal class Step(val state: LedAnimationState, val at: Long, val rank: Int, val order: Long, val isShutdown: Boolean) : Comparable<Step> {
		override fun compareTo(other: Step): Int = compareValuesBy(this, other, Step::at, Step::rank, Step::order)

		companion object {
			const val RUNNING = 0
			const val SHUTDOWN = 1
			const val STARTING = 2
		}
	}

	fun launch() {
		Log.thread("[Led] 1. Request Coroutine")
		if (job?.isActive != true) {
			// Finite animations survive stop() (eventOffAll only shuts loop 0 down). Their times are absolute,
			// so they are pushed back by the paused time: resuming neither replays what was scheduled while
			// stopped in one burst nor cuts short a wait that was still pending.
			synchronized(this) {
				pausedAt?.let { since ->
					val pausedFor = SystemClock.elapsedRealtime() - since
					for (state in ledAnimationStates) state.resume(since, pausedFor)
				}
				pausedAt = null
			}
			job = scope.launch {
				Log.thread("[Led] 2. Start Coroutine")
				while (isActive) {
					val millis = measureTimeMillis {
						// stop() cancels under the delivery lock, so no tick starts after it returns.
						synchronized(deliveryLock) { if (isActive) loop() }
					}
					delay((loopDelay - millis).coerceAtLeast(0))
				}
				Log.thread("[Led] 4. End Coroutine")
			}
		}
	}

	/** Waits for a tick in progress to deliver its changes; once this returns, none are delivered until [launch]. */
	fun stop() {
		Log.thread("[Led] 3. Request Stop")
		synchronized(deliveryLock) {
			val wasActive = active
			job?.cancel()
			job = null
			synchronized(this) {
				if (wasActive) pausedAt = SystemClock.elapsedRealtime()
				ledAnimationStatesAdd.clear()
			}
		}
	}

	/** Runs [block] between ticks: every change computed before it has reached the listener, and none after it has. */
	fun <T> afterDelivery(block: () -> T): T = synchronized(deliveryLock) { block() }

	// Functions

	private fun searchEvent(x: Int, y: Int, chain: Int): LedAnimationState? {
		synchronized(this) {
			for (state in ledAnimationStates) {
				if (state.equal(x, y, chain) && state.isPlaying) {
					return state
				}
			}
			return null
		}
	}

	fun isEventExist(x: Int, y: Int, chain: Int): Boolean = searchEvent(x, y, chain) != null

	fun isEventExist(x: Int, y: Int): Boolean {
		synchronized(this) {
			return ledAnimationStates.any { it.buttonX == x && it.buttonY == y && it.isPlaying }
		}
	}

	/**
	 * A press of pad ([x], [y]) at [pressedAt] (SystemClock.elapsedRealtime): the pad's running animation
	 * ends there and its new one starts there, however late the loop gets to them.
	 */
	fun eventOn(x: Int, y: Int, pressedAt: Long = SystemClock.elapsedRealtime()) {
		if (active) {
			synchronized(this) {
				val order = ++requests
				val currentChain = chain.value
				for (states in arrayOf(ledAnimationStates, ledAnimationStatesAdd)) {
					for (state in states) {
						if (state.equal(x, y, currentChain)) state.shutDownAt(pressedAt, order)
					}
				}
				// A press made on another chain while the pad is still held replaces its loop below; end
				// that earlier loop now, as a press on the same chain does, or nothing could end it.
				heldLoop[x][y]?.shutDownAt(pressedAt, order)
				val state = LedAnimationState(x, y, pressedAt, order)
				heldLoop[x][y] = state.takeIf { it.ledAnimation?.loop == 0 }
				if (state.noError) ledAnimationStatesAdd.add(state)
			}
		}
	}

	/** A release of pad ([x], [y]) at [releasedAt]: the endless animation the pad's press started ends there. */
	fun eventOff(x: Int, y: Int, releasedAt: Long = SystemClock.elapsedRealtime()) {
		if (active) {
			synchronized(this) {
				val loop = heldLoop[x][y] ?: return
				heldLoop[x][y] = null
				loop.shutDownAt(releasedAt, ++requests)
			}
		}
	}

	fun eventOffAll(x: Int, y: Int) {
		// No `active` guard: ledInit() calls this right after stop() to shut the looping
		// animations down; with the guard they stayed isPlaying and replayed in a burst on relaunch.
		synchronized(this) {
			for (states in arrayOf(ledAnimationStates, ledAnimationStatesAdd)) {
				for (state in states) {
					if (state.buttonX == x && state.buttonY == y && state.ledAnimation?.loop == 0) {
						// Before anything still due: nothing more of it plays.
						state.shutDownAt(Long.MIN_VALUE, 0)
					}
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
	inner class LedAnimationState(val buttonX: Int, val buttonY: Int, startedAt: Long, private val order: Long) {
		var index = 0
		/** When the animation was pressed, on the SystemClock.elapsedRealtime clock. */
		private var startedAt = startedAt
		/** When the next event is due. */
		private var delay: Long = startedAt
		/** Scheduled start of the current pass, used to detect a pass with no elapsed time. */
		private var loopStartedAt: Long = startedAt
		var isPlaying = true
		/** When a press or release ended the animation; what fell due before then still plays. */
		private var shutdownAt: Long? = null
		private var shutdownOrder = 0L
		/** When the last event played; null until then, or for an animation with nothing to play. */
		private var finishedAt: Long? = null
		var remove = false
		var loopProgress = 0
		/** Events played in the current tick. */
		var processed = 0

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
			// An empty keyLED file gives an animation with no events; with loop 0 the old code
			// spun into ledEvents[0] on an empty list (Crashlytics a1376611).
			if (animation?.ledEvents?.isEmpty() == true) isPlaying = false else finishIfDone()
		}

		internal fun shutDownAt(at: Long, order: Long) {
			if (shutdownAt.let { it == null || at < it }) {
				shutdownAt = at
				shutdownOrder = order
			}
		}

		/** A shutdown asked for while stopped takes effect at the moment the runner stopped. */
		internal fun resume(pausedSince: Long, pausedFor: Long) {
			startedAt += pausedFor
			delay += pausedFor
			loopStartedAt += pausedFor
			finishedAt = finishedAt?.let { it + pausedFor }
			shutdownAt = shutdownAt?.let { minOf(it, pausedSince) + pausedFor }
		}

		/** The step due by [currTime], if any; marks the animation for removal once it has none left. */
		internal fun nextStep(currTime: Long): Step? {
			val animation = ledAnimation
			if (remove || animation == null) {
				remove = true
				return null
			}
			val shutdown = shutdownAt?.let { Step(this, it, Step.SHUTDOWN, shutdownOrder, isShutdown = true) }
			if (!isPlaying) {
				// A press placed before the last event still ends the animation and turns its LEDs off.
				val end = finishedAt
				if (shutdown != null && end != null && shutdown.at < end) return shutdown
				if (end == null || currTime - end > LATE_PRESS_MS) remove = true
				return null
			}
			var event: Step? = Step(this, delay, if (delay == startedAt) Step.STARTING else Step.RUNNING, order, isShutdown = false)
			// An animation with no delay lines never pushes delay past currTime, so it would play forever
			// while holding the monitor and the next padTouch would block into an ANR. Endless (loop 0)
			// animations yield to the next tick at the existing cap and keep playing, so eventOff still
			// shuts them down and turns their LEDs off. Finite zero-duration animations finish after one
			// pass instead of multiplying the event count by the loop count, which could also overflow. The
			// backlog is dropped, otherwise a strobe shorter than a tick falls further behind real time every
			// tick and a GC stall leaves it lagging for many ticks.
			if (animation.loop == 0 && delay <= currTime && processed > animation.ledEvents.size) {
				delay = currTime
				event = null
			}
			val next = if (event == null || (shutdown != null && shutdown < event)) shutdown else event
			return next?.takeIf { it.at <= currTime }
		}

		/** Plays the event due [at]. */
		internal fun playEvent(at: Long, changes: MutableList<LedChange>) {
			val ledEvents = ledAnimation?.ledEvents ?: return
			processed++
			try {
				when (val event = ledEvents[index]) {
					is LedAnimation.LedEvent.On -> {
						val x = event.x
						val y = event.y
						val color = event.color
						val velocity = event.velocity

						// Recorded after the table write, which rejects a coordinate outside the grid:
						// the change is applied later, outside the catch below.
						if (x != -1) {
							btnLed[x][y] = Led(buttonX, buttonY, chainAtCreation)
							changes.add(LedChange.PadOn(x, y, color, velocity))
						} else {
							cirLed[y] = Led(buttonX, buttonY, chainAtCreation)
							changes.add(LedChange.ChainLedOn(y, color, velocity))
						}
					}

					is LedAnimation.LedEvent.Off -> {
						val x = event.x
						val y = event.y

						if (x != -1) {
							if (btnLed[x][y]?.equal(buttonX, buttonY, chainAtCreation) == true) {
								changes.add(LedChange.PadOff(x, y))
								btnLed[x][y] = null
							}
						} else {
							if (cirLed[y]?.equal(buttonX, buttonY, chainAtCreation) == true) {
								changes.add(LedChange.ChainLedOff(y))
								cirLed[y] = null
							}
						}
					}

					is LedAnimation.LedEvent.Delay -> {
						delay += event.delay.toLong()
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
			index++
			finishIfDone()
			if (!isPlaying) finishedAt = at
		}

		private fun finishIfDone() {
			val animation = ledAnimation ?: return
			if (index >= animation.ledEvents.size) {
				loopProgress++
				index = 0
				// Repeating a pass at the same scheduled instant leaves the same final lights and chain.
				if (animation.loop != 0 && delay == loopStartedAt) loopProgress = animation.loop
				loopStartedAt = delay
			}
			if (animation.loop != 0 && animation.loop <= loopProgress) isPlaying = false
		}

		/** Turns off every LED this pad and chain lit last. */
		internal fun shutDown(changes: MutableList<LedChange>) {
			for (x in 0 until unipack.buttonX) {
				for (y in 0 until unipack.buttonY) {
					if (btnLed[x][y]?.equal(buttonX, buttonY, chainAtCreation) == true) {
						changes.add(LedChange.PadOff(x, y))
						btnLed[x][y] = null
					}
				}
			}
			for (y in cirLed.indices) {
				if (cirLed[y]?.equal(buttonX, buttonY, chainAtCreation) == true) {
					changes.add(LedChange.ChainLedOff(y))
					cirLed[y] = null
				}
			}
			remove = true
		}
	}
}

/**
 * Carries [LedRunner.LedChange]s from the runner thread to one consumer thread in order, with at most
 * one drain scheduled at a time however many changes pile up. Posting a task per change let a pack
 * with hundreds of thousands of changes in one tick bury the main thread's queue for seconds.
 *
 * Resets requested between the changes are reported by the drain that takes the last change offered
 * before them, so a large backlog ahead of a reset is still applied a few changes per task, and none of
 * those changes lands after the reset.
 */
class LedChangeQueue(private val scheduleDrain: () -> Unit) {
	class Batch(val changes: List<LedRunner.LedChange>, val reset: Boolean)

	private val pending = ArrayDeque<LedRunner.LedChange>()
	// The rest are guarded by pending. Positions count every change ever offered.
	private val resetPositions = ArrayDeque<Long>()
	private var offered = 0L
	private var taken = 0L
	private var drainScheduled = false

	fun offer(changes: List<LedRunner.LedChange>) {
		val schedule = synchronized(pending) {
			pending.addAll(changes)
			offered += changes.size
			!drainScheduled.also { drainScheduled = true }
		}
		if (schedule) scheduleDrain()
	}

	/** Returns true when nothing is waiting, so the caller resets right away; otherwise a later [drain] reports it. */
	fun requestReset(): Boolean {
		val schedule = synchronized(pending) {
			if (pending.isEmpty() && resetPositions.isEmpty()) return true
			resetPositions.addLast(offered)
			!drainScheduled.also { drainScheduled = true }
		}
		if (schedule) scheduleDrain()
		return false
	}

	/**
	 * Drops every waiting change and reset. A drain already scheduled still runs and finds nothing left.
	 * Returns the last waiting chain change, since the chain is state the caller keeps rather than a light.
	 */
	fun discard(): LedRunner.LedChange.ChainChange? = synchronized(pending) {
		val lastChainChange = pending.lastOrNull { it is LedRunner.LedChange.ChainChange } as LedRunner.LedChange.ChainChange?
		pending.clear()
		resetPositions.clear()
		taken = offered
		lastChainChange
	}

	/**
	 * Removes up to [max] changes in the order they were offered, stopping at the next reset. When some
	 * remain another drain is scheduled, so the consumer thread can handle input between the portions of a
	 * large backlog.
	 */
	fun drain(max: Int = Int.MAX_VALUE): Batch {
		val changes: List<LedRunner.LedChange>
		val reset: Boolean
		val more: Boolean
		synchronized(pending) {
			val beforeReset = resetPositions.firstOrNull()?.let { (it - taken).toInt() } ?: pending.size
			val count = minOf(max, beforeReset)
			changes = ArrayList<LedRunner.LedChange>(count).apply { repeat(count) { add(pending.removeFirst()) } }
			taken += count
			reset = resetPositions.firstOrNull() == taken
			if (reset) resetPositions.removeFirst()
			more = pending.isNotEmpty() || resetPositions.isNotEmpty()
			drainScheduled = more
		}
		if (more) scheduleDrain()
		return Batch(changes, reset)
	}
}
