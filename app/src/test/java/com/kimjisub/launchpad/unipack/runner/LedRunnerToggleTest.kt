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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * Stopping and relaunching the runner (LED switch, screen hidden) must pause the animation timeline:
 * no burst of the events scheduled while stopped, and a wait that was still pending keeps its remaining
 * time. The runner's own coroutine is parked on a huge loopDelay so every tick is driven by the test
 * through the private loop() at a controlled clock.
 */
class LedRunnerToggleTest {

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

	private fun setUpRunner(animation: () -> LedAnimation) {
		val unipack = mockk<UniPack>(relaxed = true)
		every { unipack.buttonX } returns 8
		every { unipack.buttonY } returns 8
		every { unipack.ledGet(any(), any(), any()) } answers { animation() }
		runner = LedRunner(unipack, object : LedRunner.Listener {
			override fun onPadLedTurnOn(x: Int, y: Int, color: Int, velocity: Int) = record("on")
			override fun onPadLedTurnOff(x: Int, y: Int) = record("off")
			override fun onChainLedTurnOn(c: Int, color: Int, velocity: Int) {}
			override fun onChainLedTurnOff(c: Int) {}
			override fun onChainChange(c: Int) {}
		}, ChainObserver(), loopDelay = 3_600_000L)
	}

	private fun record(type: String) = synchronized(events) { events += "$type@${clock.get()}" }

	private fun blink(loop: Int, on: Int = 100, off: Int = 100) = LedAnimation(
		arrayListOf(LedEvent.On(1, 1), LedEvent.Delay(on), LedEvent.Off(1, 1), LedEvent.Delay(off)), loop, 0,
	)

	/** Waits out the single loop() the runner's coroutine runs right after launch. */
	private fun launchAt(t: Long) {
		clock.set(t)
		runner.launch()
		Thread.sleep(150)
	}

	private fun tick(t: Long) {
		clock.set(t)
		loopMethod.invoke(runner)
	}

	private fun run(from: Long, to: Long, step: Long = 10) {
		var t = from
		while (t <= to) {
			tick(t)
			t += step
		}
	}

	private fun press() {
		runner.eventOn(0, 0)
		tick(clock.get())
	}

	/** PlayActivityViewModel.syncLedRunner stops the runner, and the LED switch also shuts infinite loops. */
	private fun ledSwitchOff() {
		runner.stop()
		runner.eventOffAll(0, 0)
	}

	private fun screenHidden() = runner.stop()

	private fun ons() = events.filter { it.startsWith("on@") }
	private fun maxOnsPerInstant() = ons().groupingBy { it }.eachCount().values.maxOrNull() ?: 0

	@Test
	fun finite_longOff_resumesRemainingWaitWithoutBurst() {
		setUpRunner { blink(loop = 3) }
		launchAt(1000)
		press()
		run(1000, 1050)
		ledSwitchOff()
		launchAt(10_050)
		run(10_050, 11_000)

		assertEquals(1, maxOnsPerInstant())
		assertEquals(
			listOf("on@1000", "off@10100", "on@10200", "off@10300", "on@10400", "off@10500"),
			events,
		)
		assertFalse(runner.isEventExist(0, 0))
	}

	@Test
	fun finite_shortOff_keepsPendingWait() {
		setUpRunner { LedAnimation(arrayListOf(LedEvent.On(1, 1), LedEvent.Delay(500), LedEvent.Off(1, 1)), 1, 0) }
		launchAt(1000)
		press()
		run(1000, 1100)
		screenHidden()
		launchAt(1200)
		run(1200, 2000)

		// 100ms of the 500ms wait ran before stopping; the remaining 400ms runs after relaunch.
		assertEquals(listOf("on@1000", "off@1600"), events)
		assertFalse(runner.isEventExist(0, 0))
	}

	@Test
	fun finite_rapidToggles_accumulateOnlyRunningTime() {
		setUpRunner { blink(loop = 4) }
		launchAt(1000)
		press()
		run(1000, 1030)
		for (t in listOf(5_000L, 10_000L)) {
			ledSwitchOff()
			launchAt(t)
			run(t, t + 30)
		}
		ledSwitchOff()
		launchAt(15_000)
		run(15_000, 17_000)

		// 30ms of the first 100ms wait runs in each of the first three segments, 10ms remain.
		assertEquals(1, maxOnsPerInstant())
		assertEquals(
			listOf(
				"on@1000", "off@15010", "on@15110", "off@15210", "on@15310", "off@15410", "on@15510", "off@15610",
			),
			events,
		)
		assertFalse(runner.isEventExist(0, 0))
	}

	@Test
	fun repeatedStopWhileStopped_countsPauseFromFirstStop() {
		setUpRunner { blink(loop = 1) }
		launchAt(1000)
		press()
		run(1000, 1050)
		screenHidden()
		clock.set(3000)
		ledSwitchOff()
		launchAt(5000)
		run(5000, 6000)

		assertEquals(listOf("on@1000", "off@5050"), events)
	}

	@Test
	fun finite_screenHidden_resumesWithOriginalSpacing() {
		setUpRunner { blink(loop = 3) }
		launchAt(1000)
		press()
		run(1000, 1250)
		screenHidden()
		launchAt(20_000)
		run(20_000, 21_000)

		assertEquals(
			listOf("on@1000", "off@1100", "on@1200", "off@20050", "on@20150", "off@20250"),
			events,
		)
		assertFalse(runner.isEventExist(0, 0))
	}

	@Test
	fun infinite_ledSwitchOff_shutsDown() {
		setUpRunner { blink(loop = 0) }
		launchAt(1000)
		press()
		run(1000, 1050)
		ledSwitchOff()
		launchAt(10_000)
		run(10_000, 11_000)

		assertEquals(listOf("on@1000", "off@10000"), events)
		assertFalse(runner.isEventExist(0, 0))
	}

	@Test
	fun infinite_screenHidden_resumesWithOriginalPeriod() {
		setUpRunner { blink(loop = 0) }
		launchAt(1000)
		press()
		run(1000, 1050)
		screenHidden()
		launchAt(10_000)
		run(10_000, 11_000)

		assertEquals(listOf("on@1000", "on@10150", "on@10350", "on@10550", "on@10750", "on@10950"), ons())
		assertTrue(runner.isEventExist(0, 0))
	}

	@Test
	fun delaylessInfinite_stillPlaysAfterRelaunch() {
		setUpRunner { LedAnimation(arrayListOf(LedEvent.On(1, 1), LedEvent.Off(1, 1)), 0, 0) }
		launchAt(1000)
		press()
		run(1000, 1020)
		screenHidden()
		launchAt(5000)
		run(5000, 5100)

		assertTrue(ons().count { it.substringAfter('@').toLong() >= 5000 } >= 10)
		assertTrue(runner.isEventExist(0, 0))
	}

	@Test
	fun finite_withoutToggle_keepsExactTiming() {
		setUpRunner { blink(loop = 3) }
		launchAt(1000)
		press()
		run(1000, 2000)

		assertEquals(listOf("on@1000", "on@1200", "on@1400"), ons())
		assertFalse(runner.isEventExist(0, 0))
	}

	@Test
	fun infinite_withoutToggle_keepsExactPeriod() {
		setUpRunner { blink(loop = 0) }
		launchAt(1000)
		press()
		run(1000, 2000)

		assertEquals((1000L..2000L step 200).map { "on@$it" }, ons())
		assertTrue(runner.isEventExist(0, 0))
	}

	@Test
	fun reentry_afterToggle_newPressStartsFresh() {
		setUpRunner { blink(loop = 3) }
		launchAt(1000)
		press()
		run(1000, 1050)
		ledSwitchOff()
		launchAt(8000)
		run(8000, 8040)
		press()
		run(8040, 9500)

		assertEquals(1, maxOnsPerInstant())
		assertEquals(listOf("on@1000", "on@8040", "on@8240", "on@8440"), ons())
		assertFalse(runner.isEventExist(0, 0))
	}

	@Test
	fun packExit_emitsNothingAfterStop() {
		setUpRunner { blink(loop = 0) }
		launchAt(1000)
		press()
		run(1000, 1050)
		runner.stop()
		val before = events.toList()
		clock.set(50_000)
		Thread.sleep(150)

		assertEquals(before, events)
	}
}
