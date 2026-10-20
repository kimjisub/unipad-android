package com.kimjisub.launchpad.viewmodel

import android.os.SystemClock
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.runner.LedRunner
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import io.mockk.every
import io.mockk.excludeRecords
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runners.model.Statement
import org.junit.runners.model.TestTimedOutException
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * The user presses a pad while an LED animation blinks the same pad. PlayActivity's setLedPad reads the
 * pad's state and queues it to the Launchpad, so the last command sent matches the app's state only if
 * every channel write and its setLedPad run on one thread, in order. Runner ticks here run on their own
 * thread against presses on the test's main thread, through the view model's real runner and listener.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelPressLedRaceTest {
	@Volatile private var testThread: Thread? = null

	// Every wait inside a trial is bounded, so only a deadlock outside them should reach this limit. JUnit then
	// interrupts the test thread but does not wait for it, and its tearDown would reset Dispatchers.Main and the
	// clock mock under the next test; waiting here lets a trial that the interrupt wakes unwind first.
	@get:Rule
	val timeout = TestRule { base, description ->
		val limited = Timeout.seconds(HANG_TIMEOUT_SECONDS).apply(object : Statement() {
			override fun evaluate() {
				testThread = Thread.currentThread()
				base.evaluate()
			}
		}, description)
		object : Statement() {
			override fun evaluate() {
				try {
					limited.evaluate()
				} catch (timedOut: TestTimedOutException) {
					testThread?.join(TimeUnit.SECONDS.toMillis(UNWIND_SECONDS))
					throw timedOut
				}
			}
		}
	}

	private companion object {
		const val TRIALS = 3000
		const val OPS_PER_TRIAL = 200
		const val PAD = 3
		const val LED_VELOCITY = 45
		// Well above the slowest loaded run seen (187 s), and with the unwind wait and the rest of the unit tests
		// still inside CI's 12-minute step limit, so a hang reports its stack instead of the step being killed.
		const val HANG_TIMEOUT_SECONDS = 300L
		const val UNWIND_SECONDS = 30L
	}

	/** A plain pack whose only animation is the blink on (0,0), so each press costs what it costs in the app. */
	private class BlinkPack(blink: LedAnimation) : UniPack() {
		override val id = "press-led-race"
		override val keyLedExist = true

		init {
			buttonX = 8
			buttonY = 8
			chain = 1
			ledAnimationTable = Array(1) { Array(8) { arrayOfNulls<ArrayDeque<LedAnimation>>(8) } }
				.also { it[0][0][0] = ArrayDeque(listOf(blink)) }
		}

		override fun lastModified() = 0L
		override fun loadInfo() = this
		override fun loadDetail() = this
		override fun checkFile() {}
		override fun delete() = false
		override fun getPathString() = ""
		override fun getByteSize() = 0L
	}

	private val main = FakeMainDispatcher()
	private val clock = AtomicLong(1000)
	private lateinit var vm: PlayActivityViewModel
	private lateinit var runner: LedRunner
	private lateinit var unipack: UniPack
	private val loopMethod = LedRunner::class.java.getDeclaredMethod("loop").apply { isAccessible = true }

	private val sentToPad = Collections.synchronizedList(ArrayList<Int>())
	private val sentOffMain = AtomicInteger()
	private var afterPadRead: (() -> Unit)? = null

	private inner class FakeActivity : PlayActivityViewModel.UiCallback {
		override fun setLedPad(x: Int, y: Int) {
			if (x != PAD || y != PAD) return
			if (Thread.currentThread() !== main.mainThread) sentOffMain.incrementAndGet()
			val code = vm.channelManager.get(x, y)?.code ?: 0
			// The driver turns the code into a MIDI message before queueing it.
			if (Thread.currentThread() === main.mainThread) {
				afterPadRead?.let { overlap ->
					afterPadRead = null
					overlap()
				}
			}
			sentToPad += code
		}

		override fun setLedChain(c: Int) {}
		override fun updateTraceLogOverlay() {}
		override fun showToast(resId: Int) {}
		override fun finishActivity() {}
		override fun copyToClipboard(text: String) {}
		override fun setChainViewVisibility(index: Int, visibility: Int) {}
		override fun startGuideAnimation(x: Int, y: Int, targetWallTimeMs: Long) {}
		override fun stopGuideAnimation(x: Int, y: Int) {}
		override fun sendGuideLedToLaunchpad(x: Int, y: Int, velocity: Int) {}
		override fun onRequestRelayout() {}
	}

	/** Pressing (0,0) blinks the pad under test once per tick; the pad itself has no animation. */
	private fun setUp(loop: Int) {
		// JUnit runs the timeout-bounded test and cleanup on their own thread.
		main.mainThread = Thread.currentThread()
		Dispatchers.setMain(main)
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { clock.get() }
		// Clock calls supply time only; recording them adds work on every tick and grows the heap.
		excludeRecords { SystemClock.elapsedRealtime() }
		val blink = LedAnimation(
			arrayListOf(LedEvent.On(PAD, PAD, velocity = LED_VELOCITY), LedEvent.Delay(1), LedEvent.Off(PAD, PAD), LedEvent.Delay(1)),
			loop,
			0,
		)
		unipack = BlinkPack(blink)

		vm = PlayActivityViewModel(mockk<UnipackRepository>(), UsageAnalytics { _, _ -> })
		vm.unipack = unipack
		vm.channelManager = ChannelManager(8, 8)
		vm.uiCallback = FakeActivity()
		vm.initPlayback()
		// No audio engine in unit tests; presses only need the LED path.
		vm.soundRunner = null
		runner = vm.ledRunner!!
		vm.scbFeedbackLight.setCheckedSilently(true)
		vm.screenVisible = true
	}

	@After
	fun tearDown() {
		if (::runner.isInitialized) runner.stop()
		unmockkStatic(SystemClock::class)
		Dispatchers.resetMain()
	}

	/** Returns how many trials ended with the last command sent to the pad differing from its state. */
	private fun pressWhileTheSamePadBlinks(expectFiniteEnd: Boolean): Int {
		var completed = 0
		var overlaps = 0
		var finiteEnds = 0
		var mismatches = 0
		var firstMismatch: String? = null
		val started = System.nanoTime()
		// Starting and joining 3,000 native threads adds scheduler work to every trial.
		// Reuse one worker; each future must finish before the next trial starts.
		val tickWorker = Executors.newSingleThreadExecutor { task ->
			Thread(task, "press-led-ticks").apply { isDaemon = true }
		}
		try {
			repeat(TRIALS) { trial ->
				sentToPad.clear()
				runner.eventOn(0, 0)
				val tickBudget = Semaphore(0)
				val pressing = AtomicBoolean(true)
				val firstRead = CountDownLatch(1)
				val blinkDuringRead = CountDownLatch(1)
				val blinkEnded = CountDownLatch(1)
				// Hold the first pad command after its state read until the other thread has ticked.
				// This guarantees the race window in every trial without yielding on every delivery.
				afterPadRead = {
					firstRead.countDown()
					tickBudget.release(2)
					assertTrue("LED ticks did not overlap the pad read in trial $trial", blinkDuringRead.await(5, TimeUnit.SECONDS))
					overlaps++
				}
				// Pace ticks by press progress. A slow press must not let the producer run unbounded,
				// spend time spinning or keep adding work while main.runAll() tries to empty the queue.
				val tickFailure = AtomicReference<Throwable>()
				val ticks = tickWorker.submit {
					try {
						check(firstRead.await(5, TimeUnit.SECONDS)) { "pad read did not start in trial $trial" }
						var tickCount = 0
						while (pressing.get()) {
							if (!tickBudget.tryAcquire(5, TimeUnit.SECONDS)) continue
							if (!pressing.get()) break
							clock.incrementAndGet()
							loopMethod.invoke(runner)
							// The first tick may only add the new animation; the second emits its light.
							if (++tickCount == 2) blinkDuringRead.countDown()
							if (expectFiniteEnd && tickCount >= 2 && !runner.isEventExist(0, 0)) blinkEnded.countDown()
						}
					} catch (failure: Throwable) {
						tickFailure.set(failure)
					}
				}
				try {
					repeat(OPS_PER_TRIAL) { i ->
						// The first read allows two ticks; subsequent presses each allow one more.
						if (i > 0) tickBudget.release()
						if (expectFiniteEnd && i == OPS_PER_TRIAL / 2) {
							// Keep the finite case: it must finish during a press, with later presses still to come.
							afterPadRead = {
								assertTrue("finite blink did not finish during trial $trial", blinkEnded.await(5, TimeUnit.SECONDS))
								finiteEnds++
							}
						}
						vm.padTouch(PAD, PAD, i % 2 == 0)
						main.runAll()
					}
				} finally {
					afterPadRead = null
					pressing.set(false)
					firstRead.countDown()
					tickBudget.release()
					ticks.get(5, TimeUnit.SECONDS)
				}
				tickFailure.get()?.let { throw AssertionError("LED tick worker failed in trial $trial", it) }
				main.runAll()

				val state = vm.channelManager.get(PAD, PAD)?.code ?: 0
				val lastSent = synchronized(sentToPad) { sentToPad.lastOrNull() }
				if (lastSent != state) {
					mismatches++
					if (firstMismatch == null) firstMismatch = "trial $trial: state=$state lastSent=$lastSent"
				}
				completed++
			}
			assertEquals("trials whose LED ticks overlap the pad read", TRIALS, overlaps)
			if (expectFiniteEnd) {
				assertEquals("trials whose finite blink ends during a press", TRIALS, finiteEnds)
			}
		} finally {
			tickWorker.shutdownNow()
			assertTrue("LED tick worker did not stop", tickWorker.awaitTermination(5, TimeUnit.SECONDS))
			println(
				"PRESS_LED_RACE trials=$completed overlaps=$overlaps finiteEnds=$finiteEnds " +
					"mismatches=$mismatches sentOffMain=${sentOffMain.get()} first=$firstMismatch " +
					"elapsedMs=${TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)}"
			)
		}
		return mismatches
	}

	@Test
	fun endlessBlink_pressedOnTheSamePad_lastCommandSentMatchesTheState() {
		setUp(loop = 0)

		val mismatches = pressWhileTheSamePadBlinks(expectFiniteEnd = false)

		assertEquals("pad commands sent off the main thread", 0, sentOffMain.get())
		assertEquals("trials whose last command differs from the state", 0, mismatches)
	}

	@Test
	fun finiteBlink_pressedOnTheSamePad_lastCommandSentMatchesTheState() {
		// Ends partway through each trial, so later ticks send nothing and presses have the last word.
		setUp(loop = OPS_PER_TRIAL / 4)

		val mismatches = pressWhileTheSamePadBlinks(expectFiniteEnd = true)

		assertEquals("pad commands sent off the main thread", 0, sentOffMain.get())
		assertEquals("trials whose last command differs from the state", 0, mismatches)
	}
}
