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
 * A pad's release ends the endless (loop 0) animation its press started, whatever the chain is by
 * then. Pad (0,0) lights cell (5,5) on chain 1 and cell (6,6) on chain 2, each endlessly, so the cells
 * still lit tell which animation kept running. Ticks are driven through the private loop() at a
 * controlled clock, as in [LedRunnerToggleTest].
 */
class LedRunnerHeldLoopTest {

	private val clock = AtomicLong(1000)
	private val chain = ChainObserver().apply { range = 0..1 }
	private val lit = mutableSetOf<Pair<Int, Int>>()
	private val lastOnAt = mutableMapOf<Pair<Int, Int>, Long>()
	private lateinit var runner: LedRunner
	private val loopMethod = LedRunner::class.java.getDeclaredMethod("loop").apply { isAccessible = true }

	@Before
	fun setUp() {
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { clock.get() }
		val unipack = mockk<UniPack>(relaxed = true)
		every { unipack.buttonX } returns 8
		every { unipack.buttonY } returns 8
		every { unipack.ledGet(any(), 0, 0) } answers {
			val cell = 5 + firstArg<Int>()
			LedAnimation(arrayListOf(LedEvent.On(cell, cell), LedEvent.Delay(100)), 0, 0)
		}
		runner = LedRunner(unipack, object : LedRunner.Listener {
			override fun onPadLedTurnOn(x: Int, y: Int, color: Int, velocity: Int) {
				lit += x to y
				lastOnAt[x to y] = clock.get()
			}

			override fun onPadLedTurnOff(x: Int, y: Int) {
				lit -= x to y
			}

			override fun onChainLedTurnOn(c: Int, color: Int, velocity: Int) {}
			override fun onChainLedTurnOff(c: Int) {}
			override fun onChainChange(c: Int) {}
		}, chain, loopDelay = 3_600_000L)
		runner.launch()
		// Waits out the single loop() the runner's coroutine runs right after launch.
		Thread.sleep(150)
	}

	@After
	fun tearDown() {
		runner.stop()
		unmockkStatic(SystemClock::class)
	}

	private fun tick(t: Long) {
		clock.set(t)
		loopMethod.invoke(runner)
	}

	private fun at(t: Long, action: () -> Unit) {
		clock.set(t)
		action()
	}

	@Test
	fun releasedAfterAChainChange_endsTheLoopItsPressStarted() {
		at(1000) { runner.eventOn(0, 0) }
		tick(1000)
		chain.value = 1
		tick(1050)
		at(1060) { runner.eventOff(0, 0) }
		tick(1500)

		assertEquals(emptySet<Pair<Int, Int>>(), lit)
		assertEquals(1000L, lastOnAt[5 to 5])
	}

	/**
	 * Hand and autoplay pressing one pad, or a controller sending two presses with no release between,
	 * press the pad again on the new chain while its first loop runs. That press ends the first loop,
	 * as a press on the same chain does; the release then ends the second.
	 */
	@Test
	fun pressedAgainOnAnotherChainWhileHeld_endsTheEarlierLoopAndTheReleaseEndsTheLater() {
		at(1000) { runner.eventOn(0, 0) }
		tick(1000)
		chain.value = 1
		at(1010) { runner.eventOn(0, 0) }
		tick(1050)
		assertEquals(setOf(6 to 6), lit)

		at(1060) { runner.eventOff(0, 0) }
		tick(1500)

		assertEquals(emptySet<Pair<Int, Int>>(), lit)
		assertEquals(mapOf((5 to 5) to 1000L, (6 to 6) to 1050L), lastOnAt)
	}
}
