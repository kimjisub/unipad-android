package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AutoPlayRunnerTest {

	private val uncaught = AtomicReference<Throwable?>(null)
	private var previousHandler: Thread.UncaughtExceptionHandler? = null
	private var runner: AutoPlayRunner? = null

	@Before
	fun setUp() {
		previousHandler = Thread.getDefaultUncaughtExceptionHandler()
		Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught.compareAndSet(null, e) }
	}

	@After
	fun tearDown() {
		runner?.stop()
		Thread.setDefaultUncaughtExceptionHandler(previousHandler)
	}

	// SystemClock.elapsedRealtime() is stubbed to 0 in unit tests (returnDefaultValues),
	// so guides that start after t=0 never expire and stay in activeGuides.
	private fun autoPlayWithGuides(count: Int): AutoPlay {
		val elements = ArrayList<AutoPlay.Element>()
		elements.add(AutoPlay.Element.Delay(10))
		for (i in 0 until count) {
			elements.add(AutoPlay.Element.On(i / 8, i % 8, 0, 1))
			elements.add(AutoPlay.Element.Delay(1))
		}
		return AutoPlay(elements)
	}

	@Test
	fun stopWhileLeavingPracticeMode_doesNotThrowConcurrentModification() {
		val guideCount = 64
		val guidesOn = CountDownLatch(guideCount)
		val cleanupStarted = CountDownLatch(1)
		val ended = CountDownLatch(1)

		val unipack = mockk<UniPack>(relaxed = true)
		every { unipack.autoPlayTable } returns autoPlayWithGuides(guideCount)

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
					// Widen the window in which the runner is iterating activeGuides
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

		// Same order as PlayActivityViewModel.switchPlayMode(PlayMode.None):
		// flip the flag, let the runner enter its guide cleanup loop, then stop() from this thread.
		runner.practiceGuide = false
		assertTrue("runner did not start guide cleanup", cleanupStarted.await(5, TimeUnit.SECONDS))
		runner.stop()

		val deadline = System.currentTimeMillis() + 5_000
		while (ended.count > 0 && uncaught.get() == null && System.currentTimeMillis() < deadline) {
			Thread.sleep(5)
		}

		assertNull("runner coroutine crashed: ${uncaught.get()}", uncaught.get())
		assertEquals("onEnd was not called after stop()", 0, ended.count)
	}
}
