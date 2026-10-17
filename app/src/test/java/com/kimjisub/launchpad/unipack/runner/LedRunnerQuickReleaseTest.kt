package com.kimjisub.launchpad.unipack.runner

import android.os.SystemClock
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * eventOn queues a pressed pad's animation for the next tick, which plays it from the press time. A
 * release, or the eventOffAll an autoplay control sends to every pad, that lands before that tick must
 * still reach it, otherwise an endless (loop 0) animation starts after it was told to stop.
 * Ticks are driven through the private loop() at a controlled clock, as in [LedRunnerToggleTest].
 */
class LedRunnerQuickReleaseTest {

	private val clock = AtomicLong(1000)
	private val events = mutableListOf<String>()
	private lateinit var runner: LedRunner
	private val loopMethod = LedRunner::class.java.getDeclaredMethod("loop").apply { isAccessible = true }

	@Before
	fun setUp() {
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { clock.get() }
	}

	@After
	fun tearDown() {
		runner.stop()
		unmockkStatic(SystemClock::class)
	}

	private fun setUpRunner(loop: Int) {
		val unipack = mockk<UniPack>(relaxed = true)
		every { unipack.buttonX } returns 8
		every { unipack.buttonY } returns 8
		every { unipack.ledGet(any(), any(), any()) } answers {
			LedAnimation(arrayListOf(LedEvent.On(1, 1), LedEvent.Delay(100), LedEvent.Off(1, 1), LedEvent.Delay(100)), loop, 0)
		}
		runner = LedRunner(unipack, object : LedRunner.Listener {
			override fun onPadLedTurnOn(x: Int, y: Int, color: Int, velocity: Int) = record("on")
			override fun onPadLedTurnOff(x: Int, y: Int) = record("off")
			override fun onChainLedTurnOn(c: Int, color: Int, velocity: Int) {}
			override fun onChainLedTurnOff(c: Int) {}
			override fun onChainChange(c: Int) {}
		}, ChainObserver(), loopDelay = 3_600_000L)
		runner.launch()
		// Waits out the single loop() the runner's coroutine runs right after launch.
		Thread.sleep(150)
	}

	private fun record(type: String) = synchronized(events) { events += "$type@${clock.get()}" }

	private fun run(from: Long, to: Long, step: Long = 10) {
		var t = from
		while (t <= to) {
			clock.set(t)
			loopMethod.invoke(runner)
			t += step
		}
	}

	@Test
	fun infinite_releasedBeforeFirstTick_neverPlays() {
		setUpRunner(loop = 0)
		runner.eventOn(0, 0)
		runner.eventOff(0, 0)
		run(1000, 2000)

		assertEquals(emptyList<String>(), events)
		assertFalse(runner.isEventExist(0, 0))
	}

	@Test
	fun infinite_offAllBeforeFirstTick_neverPlays() {
		setUpRunner(loop = 0)
		runner.eventOn(0, 0)
		runner.eventOffAll(0, 0)
		run(1000, 2000)

		assertEquals(emptyList<String>(), events)
		assertFalse(runner.isEventExist(0, 0))
	}

	/** Released and pressed again before a tick: only the held press plays, and its release ends it. */
	@Test
	fun infinite_repressedBeforeFirstTick_playsOnlyTheHeldPress() {
		setUpRunner(loop = 0)
		runner.eventOn(0, 0)
		runner.eventOff(0, 0)
		runner.eventOn(0, 0)
		run(1000, 1250)
		clock.set(1255)
		runner.eventOff(0, 0)
		run(1260, 2000)

		assertEquals(listOf("on@1000", "off@1100", "on@1200", "off@1260"), events)
		assertFalse(runner.isEventExist(0, 0))
	}

	@Test
	fun finite_releasedBeforeFirstTick_stillPlaysToTheEnd() {
		setUpRunner(loop = 2)
		runner.eventOn(0, 0)
		runner.eventOff(0, 0)
		run(1000, 2000)

		assertEquals(listOf("on@1000", "off@1100", "on@1200", "off@1300"), events)
		assertFalse(runner.isEventExist(0, 0))
	}
}
