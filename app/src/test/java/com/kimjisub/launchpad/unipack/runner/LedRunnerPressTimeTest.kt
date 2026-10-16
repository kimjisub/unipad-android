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
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * An animation's events are due at its press time plus its delays, whenever the 4 ms loop gets to them:
 * a tick that comes late plays what fell due since the last one in the order it fell due, across
 * animations, and a press ends the pad's running animation at the press time rather than at the tick
 * that sees it. As in [LedRunnerToggleTest], the test drives every tick at a controlled clock.
 */
class LedRunnerPressTimeTest {

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

	private fun setUpRunner(animationFor: (x: Int, y: Int) -> LedAnimation) {
		val unipack = mockk<UniPack>(relaxed = true)
		every { unipack.buttonX } returns 8
		every { unipack.buttonY } returns 8
		every { unipack.ledGet(any(), any(), any()) } answers { animationFor(secondArg(), thirdArg()) }
		runner = LedRunner(unipack, object : LedRunner.Listener {
			override fun onPadLedTurnOn(x: Int, y: Int, color: Int, velocity: Int) = record("$x,$y=$velocity")
			override fun onPadLedTurnOff(x: Int, y: Int) = record("$x,$y=0")
			override fun onChainLedTurnOn(c: Int, color: Int, velocity: Int) {}
			override fun onChainLedTurnOff(c: Int) {}
			override fun onChainChange(c: Int) {}
		}, ChainObserver(), loopDelay = 3_600_000L)
		clock.set(1000)
		runner.launch()
		Thread.sleep(150)
	}

	private fun record(change: String) = synchronized(events) { events += "$change@${clock.get()}" }

	private fun pressAt(t: Long, x: Int = 0, y: Int = 0) {
		clock.set(t)
		runner.eventOn(x, y)
	}

	private fun tick(t: Long) {
		clock.set(t)
		loopMethod.invoke(runner)
	}

	private fun animation(vararg events: LedEvent) = LedAnimation(arrayListOf(*events), 1, 0)

	@Test
	fun lateFirstTick_playsFromThePressTime() {
		setUpRunner { _, _ -> animation(LedEvent.On(1, 1, velocity = 5), LedEvent.Delay(10), LedEvent.Off(1, 1)) }
		pressAt(1000)
		tick(1012)
		tick(1016)

		assertEquals(listOf("1,1=5@1012", "1,1=0@1012"), events)
	}

	/** Pad (0,0) lights (2,2) at once and again 5 ms later; pad (1,1), pressed 2 ms after it, lights (2,2) at once. */
	@Test
	fun oneTick_playsTheEventsOfDifferentAnimationsInTheOrderTheyFellDue() {
		setUpRunner { x, _ ->
			if (x == 0) animation(LedEvent.On(2, 2, velocity = 5), LedEvent.Delay(5), LedEvent.On(2, 2, velocity = 9), LedEvent.Delay(100))
			else animation(LedEvent.On(2, 2, velocity = 21), LedEvent.Delay(100))
		}
		pressAt(1000)
		tick(1000)
		pressAt(1002, 1, 1)
		tick(1008)

		assertEquals(listOf("2,2=5@1000", "2,2=21@1008", "2,2=9@1008"), events)
	}

	/**
	 * Lit (1,1) at the press, (2,2) 10 ms later, (3,3) 20 ms later. Pressed again 15 ms in while the loop
	 * lagged: (2,2) was due before that press and still plays, (3,3) was not and never does.
	 */
	@Test
	fun pressingAgain_endsThePreviousAnimationAtThePressTime() {
		setUpRunner { _, _ ->
			animation(
				LedEvent.On(1, 1, velocity = 5), LedEvent.Delay(10),
				LedEvent.On(2, 2, velocity = 9), LedEvent.Delay(10),
				LedEvent.On(3, 3, velocity = 13), LedEvent.Delay(100),
			)
		}
		pressAt(1000)
		tick(1000)
		pressAt(1015)
		tick(1028)

		assertEquals(
			listOf(
				"1,1=5@1000",
				"2,2=9@1028",
				"1,1=0@1028", "2,2=0@1028",
				"1,1=5@1028", "2,2=9@1028",
			),
			events,
		)
	}

	/**
	 * Within one millisecond the running animation plays first, then the press ends it, then the new one
	 * starts. (Had (1,1)=9 been its last event, it would have finished first and left (1,1) lit.)
	 */
	@Test
	fun pressingAgainWhenAnEventFallsDue_playsItBeforeEnding() {
		setUpRunner { _, _ ->
			animation(
				LedEvent.On(1, 1, velocity = 5), LedEvent.Delay(10),
				LedEvent.On(1, 1, velocity = 9), LedEvent.Delay(10),
				LedEvent.On(2, 2, velocity = 13), LedEvent.Delay(100),
			)
		}
		pressAt(1000)
		tick(1000)
		pressAt(1010)
		tick(1012)

		assertEquals(listOf("1,1=5@1000", "1,1=9@1012", "1,1=0@1012", "1,1=5@1012"), events)
	}

	/** Two presses between ticks: the second ends the first at its own time, before the first was ever ticked. */
	@Test
	fun twoPressesBetweenTicks_secondEndsTheFirst() {
		setUpRunner { _, _ -> animation(LedEvent.On(1, 1, velocity = 5), LedEvent.Delay(2), LedEvent.On(2, 2, velocity = 9), LedEvent.Delay(100)) }
		pressAt(1000)
		pressAt(1001)
		tick(1004)

		assertEquals(listOf("1,1=5@1004", "1,1=0@1004", "1,1=5@1004", "2,2=9@1004"), events)
	}
}
