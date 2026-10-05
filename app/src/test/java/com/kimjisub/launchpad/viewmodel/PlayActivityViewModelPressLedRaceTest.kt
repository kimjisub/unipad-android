package com.kimjisub.launchpad.viewmodel

import android.os.SystemClock
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.runner.LedRunner
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread

/**
 * The user presses a pad while an LED animation blinks the same pad. PlayActivity's setLedPad reads the
 * pad's state and queues it to the Launchpad, so the last command sent matches the app's state only if
 * every channel write and its setLedPad run on one thread, in order. Runner ticks here run on their own
 * thread against presses on the test's main thread, through the view model's real runner and listener.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelPressLedRaceTest {
	// Only a deadlock should reach this: JUnit abandons a timed-out test on a thread that keeps running and
	// later resets Dispatchers.Main under the next test. Slowness is caught first by TRIALS_DEADLINE_SECONDS.
	@get:Rule
	val timeout: Timeout = Timeout.seconds(180)

	private companion object {
		const val TRIALS = 3000
		const val OPS_PER_TRIAL = 200
		const val PAD = 3
		const val LED_VELOCITY = 45
		const val TICK_PAUSE_NANOS = 20_000L
		const val TRIALS_DEADLINE_SECONDS = 120L
	}

	/** A pack whose only animation is the blink on (0,0); a MockK pack spends most of each press in reflection. */
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

	private inner class FakeActivity : PlayActivityViewModel.UiCallback {
		override fun setLedPad(x: Int, y: Int) {
			if (x != PAD || y != PAD) return
			if (Thread.currentThread() !== main.mainThread) sentOffMain.incrementAndGet()
			val code = vm.channelManager.get(x, y)?.code ?: 0
			// The driver turns the code into a MIDI message before queueing it.
			Thread.yield()
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
		val blink = LedAnimation(
			arrayListOf(LedEvent.On(PAD, PAD, velocity = LED_VELOCITY), LedEvent.Delay(1), LedEvent.Off(PAD, PAD), LedEvent.Delay(1)),
			loop,
			0,
		)
		unipack = BlinkPack(blink)

		vm = PlayActivityViewModel(mockk<UnipackRepository>())
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
	private fun pressWhileTheSamePadBlinks(): Int {
		var mismatches = 0
		var firstMismatch: String? = null
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TRIALS_DEADLINE_SECONDS)
		repeat(TRIALS) { trial ->
			// Fails on this thread, after the trial's worker has stopped, so tearDown runs before the next test.
			assertTrue("only $trial of $TRIALS trials ran in ${TRIALS_DEADLINE_SECONDS}s", System.nanoTime() < deadline)
			// MockK keeps every call it answers; without this the trials run out of heap.
			clearStaticMockk(SystemClock::class, answers = false, recordedCalls = true, childMocks = false)
			sentToPad.clear()
			runner.eventOn(0, 0)
			val barrier = CyclicBarrier(2)
			val pressing = AtomicBoolean(true)
			// Ticks until the presses end, so the last tick and the last press overlap. The pause between
			// ticks stands in for the runner's loop delay and lets presses take the runner's lock.
			val tickFailure = AtomicReference<Throwable>()
			val ticks = thread(isDaemon = true, name = "press-led-ticks-$trial") {
				try {
					barrier.await(5, TimeUnit.SECONDS)
					while (pressing.get()) {
						clock.incrementAndGet()
						loopMethod.invoke(runner)
						LockSupport.parkNanos(TICK_PAUSE_NANOS)
					}
				} catch (failure: Throwable) {
					tickFailure.set(failure)
				}
			}
			try {
				barrier.await(5, TimeUnit.SECONDS)
				repeat(OPS_PER_TRIAL) { i ->
					vm.padTouch(PAD, PAD, i % 2 == 0)
					main.runAll()
				}
			} finally {
				pressing.set(false)
				ticks.join(5_000)
				if (ticks.isAlive) ticks.interrupt()
			}
			assertFalse("LED tick worker did not stop in trial $trial", ticks.isAlive)
			tickFailure.get()?.let { throw AssertionError("LED tick worker failed in trial $trial", it) }
			main.runAll()

			val state = vm.channelManager.get(PAD, PAD)?.code ?: 0
			val lastSent = synchronized(sentToPad) { sentToPad.lastOrNull() }
			if (lastSent != state) {
				mismatches++
				if (firstMismatch == null) firstMismatch = "trial $trial: state=$state lastSent=$lastSent"
			}
		}
		println("PRESS_LED_RACE trials=$TRIALS mismatches=$mismatches sentOffMain=${sentOffMain.get()} first=$firstMismatch")
		return mismatches
	}

	@Test
	fun endlessBlink_pressedOnTheSamePad_lastCommandSentMatchesTheState() {
		setUp(loop = 0)

		val mismatches = pressWhileTheSamePadBlinks()

		assertEquals("pad commands sent off the main thread", 0, sentOffMain.get())
		assertEquals("trials whose last command differs from the state", 0, mismatches)
	}

	@Test
	fun finiteBlink_pressedOnTheSamePad_lastCommandSentMatchesTheState() {
		// Ends partway through each trial, so later ticks send nothing and presses have the last word.
		setUp(loop = OPS_PER_TRIAL / 4)

		val mismatches = pressWhileTheSamePadBlinks()

		assertEquals("pad commands sent off the main thread", 0, sentOffMain.get())
		assertEquals("trials whose last command differs from the state", 0, mismatches)
	}
}
