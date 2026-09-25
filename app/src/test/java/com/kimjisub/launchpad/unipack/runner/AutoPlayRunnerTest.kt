package com.kimjisub.launchpad.unipack.runner

import android.os.SystemClock
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * The runner coroutine iterates activeGuides in three places (leaving practice mode, chain
 * mismatch, per-tick expiration) while stop() clears it from the main thread. Each test holds
 * the runner inside one of those loops, calls stop(), and expects no ConcurrentModificationException.
 * The clock is frozen unless a test advances it, so all guides are activated in the first tick.
 */
class AutoPlayRunnerTest {

	private val clock = AtomicLong(0)
	private val uncaught = AtomicReference<Throwable?>(null)
	private var previousHandler: Thread.UncaughtExceptionHandler? = null
	private var runner: AutoPlayRunner? = null

	@Before
	fun setUp() {
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { clock.get() }
		previousHandler = Thread.getDefaultUncaughtExceptionHandler()
		Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught.compareAndSet(null, e) }
	}

	@After
	fun tearDown() {
		runner?.stop()
		Thread.setDefaultUncaughtExceptionHandler(previousHandler)
		unmockkStatic(SystemClock::class)
	}

	private fun autoPlayWithGuides(trailingChain: Int? = null): AutoPlay {
		val elements = ArrayList<AutoPlay.Element>()
		elements.add(AutoPlay.Element.Delay(10))
		for (i in 0 until GUIDE_COUNT) {
			elements.add(AutoPlay.Element.On(i / 8, i % 8, 0, 1))
			elements.add(AutoPlay.Element.Delay(1))
		}
		if (trailingChain != null) elements.add(AutoPlay.Element.On(0, 0, trailingChain, 1))
		return AutoPlay(elements)
	}

	/**
	 * Launches a practice-guide runner, waits until every guide is active, runs [afterGuidesOn],
	 * then calls stop() while the loop under test is slowed down inside its guide-off updates.
	 */
	private fun assertStopDuringGuideCleanupIsSafe(
		autoPlay: AutoPlay,
		afterGuidesOn: (AutoPlayRunner) -> Unit = {},
	) {
		val guidesOn = CountDownLatch(GUIDE_COUNT)
		val cleanupStarted = CountDownLatch(1)
		val ended = CountDownLatch(1)

		val unipack = mockk<UniPack>(relaxed = true)
		every { unipack.autoPlayTable } returns autoPlay

		val listener = object : AutoPlayRunner.Listener {
			override fun onStart() {}
			override fun onPadTouchOn(x: Int, y: Int) {}
			override fun onPadTouchOff(x: Int, y: Int) {}
			override fun onChainChange(c: Int) {}
			override fun onGuidePadOn(x: Int, y: Int, targetWallTimeMs: Long) {
				guidesOn.countDown()
			}
			override fun onGuidePadOff(x: Int, y: Int) {}
			override fun onGuideLedUpdate(x: Int, y: Int, velocity: Int) {
				if (velocity == 0) {
					cleanupStarted.countDown()
					Thread.sleep(1)
				}
			}
			override fun onGuideChainOn(c: Int) {}
			override fun onRemoveGuide() {}
			override fun chainButsRefresh() {}
			override fun onProgressUpdate(progress: Int) {}
			override fun onEnd() {
				ended.countDown()
			}
		}

		val runner = AutoPlayRunner(unipack, listener, ChainObserver()).also { runner = it }
		runner.practiceGuide = true
		runner.playmode = true
		runner.launch()

		assertTrue("guides were not activated", guidesOn.await(5, TimeUnit.SECONDS))
		afterGuidesOn(runner)
		assertTrue("runner did not start guide cleanup", cleanupStarted.await(5, TimeUnit.SECONDS))
		runner.stop()

		val deadline = System.currentTimeMillis() + 5_000
		while (ended.count > 0 && uncaught.get() == null && System.currentTimeMillis() < deadline) {
			Thread.sleep(5)
		}

		assertNull("runner coroutine crashed: ${uncaught.get()?.stackTraceToString()}", uncaught.get())
		assertEquals("onEnd was not called after stop()", 0, ended.count)
	}

	@Test
	fun stopWhileLeavingPracticeMode_doesNotThrowConcurrentModification() {
		assertStopDuringGuideCleanupIsSafe(autoPlayWithGuides()) { runner ->
			runner.practiceGuide = false
		}
	}

	/** The trailing chain-1 guide falls inside the lookahead right after the chain-0 guides. */
	@Test
	fun stopDuringChainMismatchCleanup_doesNotThrowConcurrentModification() {
		assertStopDuringGuideCleanupIsSafe(autoPlayWithGuides(trailingChain = 1))
	}

	@Test
	fun stopDuringGuideExpiration_doesNotThrowConcurrentModification() {
		assertStopDuringGuideCleanupIsSafe(autoPlayWithGuides()) {
			clock.set(AutoPlayRunner.GUIDE_LOOKAHEAD_MS)
		}
	}

	companion object {
		private const val GUIDE_COUNT = 64
	}
}
