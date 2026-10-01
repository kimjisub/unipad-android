package com.kimjisub.launchpad.viewmodel

import android.os.SystemClock
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.runner.LedRunner
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import io.mockk.clearMocks
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
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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

	private companion object {
		const val TRIALS = 3000
		const val OPS_PER_TRIAL = 200
		const val PAD = 3
		const val LED_VELOCITY = 45
		const val TICK_PAUSE_NANOS = 20_000L
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
		Dispatchers.setMain(main)
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { clock.get() }
		val blink = LedAnimation(
			arrayListOf(LedEvent.On(PAD, PAD, velocity = LED_VELOCITY), LedEvent.Delay(1), LedEvent.Off(PAD, PAD), LedEvent.Delay(1)),
			loop,
			0,
		)
		unipack = mockk<UniPack>(relaxed = true)
		every { unipack.buttonX } returns 8
		every { unipack.buttonY } returns 8
		every { unipack.chain } returns 1
		every { unipack.keyLedExist } returns true
		every { unipack.autoPlayExist } returns false
		every { unipack.soundTable } returns null
		every { unipack.ledGet(any(), any(), any()) } answers { if (secondArg<Int>() == 0 && thirdArg<Int>() == 0) blink else null }

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
	private fun pressWhileTheSamePadBlinks(): Int {
		var mismatches = 0
		var firstMismatch: String? = null
		repeat(TRIALS) { trial ->
			// MockK keeps every call it answers; without this the trials run out of heap.
			clearMocks(unipack, answers = false, recordedCalls = true, childMocks = false, verificationMarks = false, exclusionRules = false)
			clearStaticMockk(SystemClock::class, answers = false, recordedCalls = true, childMocks = false)
			sentToPad.clear()
			runner.eventOn(0, 0)
			val barrier = CyclicBarrier(2)
			val pressing = AtomicBoolean(true)
			// Ticks until the presses end, so the last tick and the last press overlap. The pause between
			// ticks stands in for the runner's loop delay and lets presses take the runner's lock.
			val ticks = thread {
				barrier.await()
				while (pressing.get()) {
					clock.incrementAndGet()
					loopMethod.invoke(runner)
					LockSupport.parkNanos(TICK_PAUSE_NANOS)
				}
			}
			barrier.await()
			repeat(OPS_PER_TRIAL) { i ->
				vm.padTouch(PAD, PAD, i % 2 == 0)
				main.runAll()
			}
			pressing.set(false)
			ticks.join()
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
