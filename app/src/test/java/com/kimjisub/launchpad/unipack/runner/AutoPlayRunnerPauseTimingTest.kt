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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Wall time is advanced explicitly; none of these assertions depends on how fast the Mac runs. */
class AutoPlayRunnerPauseTimingTest {
	private val clock = AtomicLong()
	private val clockReads = AtomicLong()
	private val presses = CopyOnWriteArrayList<Long>()
	private val ended = CountDownLatch(1)
	private lateinit var runner: AutoPlayRunner

	@Before fun setUp() {
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers {
			clockReads.incrementAndGet()
			clock.get()
		}
	}

	@After fun tearDown() {
		if (::runner.isInitialized) {
			runner.stop()
			assertTrue("The runner did not stop", ended.await(5, TimeUnit.SECONDS))
		}
		unmockkStatic(SystemClock::class)
	}

	private fun await(check: () -> Boolean) {
		val deadline = System.nanoTime() + 5_000_000_000L
		while (!check() && System.nanoTime() < deadline) Thread.sleep(1)
		assertTrue("The runner did not process the requested clock tick", check())
	}

	private fun start(elements: ArrayList<AutoPlay.Element> = arrayListOf(
		AutoPlay.Element.On(0, 0, 0, 0), AutoPlay.Element.Delay(1000),
		AutoPlay.Element.On(1, 0, 0, 0), AutoPlay.Element.Delay(60000),
		AutoPlay.Element.Off(1, 0, 0),
	), readyProgress: Int = 2) {
		val pack = mockk<UniPack>(relaxed = true)
		every { pack.autoPlayTable } returns AutoPlay(elements)
		val listener = mockk<AutoPlayRunner.Listener>(relaxed = true)
		every { listener.onPadTouchOn(any(), any(), any(), any(), any()) } answers { presses += arg<Long>(4) }
		every { listener.onEnd() } answers { ended.countDown() }
		runner = AutoPlayRunner(pack, listener, ChainObserver())
		runner.launch()
		await { runner.progress == readyProgress }
	}

	private fun tickAt(ms: Long) {
		clock.set(ms)
		val before = clockReads.get()
		// A second loop read proves the preceding tick finished its value comparisons.
		await { clockReads.get() >= before + 2 }
	}

	private fun startFinalDelay() = start(arrayListOf(
		AutoPlay.Element.On(0, 0, 0, 0), AutoPlay.Element.Off(0, 0, 0),
		AutoPlay.Element.Delay(2000),
	), readyProgress = 3)

	private fun observeTickOrEnd(ms: Long) {
		clock.set(ms)
		val before = clockReads.get()
		await { ended.count == 0L || clockReads.get() >= before + 2 }
	}

	@Test fun theLastDelayFinishesBeforeTheRunnerEnds() {
		startFinalDelay()
		observeTickOrEnd(1999)
		assertEquals("The final delay still has 1 ms left", 1L, ended.count)
		clock.set(2000)
		await { ended.count == 0L }
	}

	@Test fun pausingTheLastDelayPreservesItsRemainingTime() {
		startFinalDelay()
		observeTickOrEnd(500)
		assertEquals("The runner must stay active during its final delay", 1L, ended.count)
		runner.playmode = false
		clock.set(3500)
		runner.playmode = true
		observeTickOrEnd(4999)
		assertEquals("The final delay still has 1 ms left after resume", 1L, ended.count)
		clock.set(5000)
		await { ended.count == 0L }
	}

	@Test fun pauseHalfwayThroughADelayPreservesTheOtherHalf() {
		start()
		tickAt(500)
		runner.playmode = false
		clock.set(3500)
		runner.playmode = true
		tickAt(3999)
		assertEquals("The remaining 500 ms must not disappear on resume", listOf(0L), presses)
		tickAt(4000)
		assertEquals(listOf(0L, 4000L), presses)
	}

	@Test fun repeatedPausesPreserveTheRemainingDelayEachTime() {
		start()
		tickAt(200)
		runner.playmode = false
		clock.set(1200)
		runner.playmode = true
		tickAt(1500)
		runner.playmode = false
		clock.set(3500)
		runner.playmode = true
		tickAt(3999)
		assertEquals(listOf(0L), presses)
		tickAt(4000)
		assertEquals(listOf(0L, 4000L), presses)
	}
}
