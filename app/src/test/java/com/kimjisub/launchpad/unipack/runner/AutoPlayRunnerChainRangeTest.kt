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

/**
 * Practice and step modes stop at a pad on another chain and light that chain's button until the user
 * selects it. A chain outside the pack can never be selected, and its button index runs past the round
 * button table, so such a pad is passed over instead.
 */
class AutoPlayRunnerChainRangeTest {

	private var runner: AutoPlayRunner? = null
	private val guideChains = CopyOnWriteArrayList<Int>()

	@Before
	fun setUp() {
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } returns 0L
	}

	@After
	fun tearDown() {
		runner?.stop()
		unmockkStatic(SystemClock::class)
	}

	/** Starts a runner on chain 0 of a two-chain pack and waits until the pad at (1, 1) is guided. */
	private fun assertGuideReaches11(elements: List<AutoPlay.Element>, configure: (AutoPlayRunner) -> Unit) {
		val lastPadGuided = CountDownLatch(1)
		val unipack = mockk<UniPack>(relaxed = true)
		every { unipack.autoPlayTable } returns AutoPlay(ArrayList(elements))

		val listener = object : AutoPlayRunner.Listener {
			override fun onStart() {}
			override fun onPadTouchOn(x: Int, y: Int, c: Int, num: Int, atMs: Long) {}
			override fun onPadTouchOff(x: Int, y: Int, atMs: Long) {}
			override fun onChainChange(c: Int) {}
			override fun onGuidePadOn(x: Int, y: Int, targetWallTimeMs: Long) {
				if (x == 1 && y == 1) lastPadGuided.countDown()
			}
			override fun onGuidePadOff(x: Int, y: Int) {}
			override fun onGuideLedUpdate(x: Int, y: Int, velocity: Int) {}
			override fun onGuideChainOn(c: Int) {
				guideChains.add(c)
			}
			override fun onRemoveGuide() {}
			override fun chainButsRefresh() {}
			override fun onProgressUpdate(progress: Int) {}
			override fun onEnd() {}
		}

		val chain = ChainObserver().apply { range = 0 until 2 }
		val runner = AutoPlayRunner(unipack, listener, chain).also { runner = it }
		runner.practiceGuide = true
		configure(runner)
		runner.launch()

		assertTrue("the guide waited at the missing chain", lastPadGuided.await(3, TimeUnit.SECONDS))
		assertEquals(emptyList<Int>(), guideChains)
	}

	@Test
	fun practiceMode_passesOverAPadOnAMissingChain() {
		assertGuideReaches11(
			listOf(
				AutoPlay.Element.On(0, 0, 0, 0),
				AutoPlay.Element.Delay(1),
				AutoPlay.Element.On(0, 1, MISSING_CHAIN, 0),
				AutoPlay.Element.Delay(1),
				AutoPlay.Element.On(1, 1, 0, 0),
			)
		) { it.playmode = true }
	}

	@Test
	fun stepMode_passesOverAPadOnAMissingChain() {
		assertGuideReaches11(
			listOf(
				AutoPlay.Element.On(0, 1, MISSING_CHAIN, 0),
				AutoPlay.Element.Delay(100),
				AutoPlay.Element.On(1, 1, 0, 0),
			)
		) {
			it.stepMode = true
			it.playmode = false
		}
	}

	companion object {
		/** Past the 36-entry round button table once the chain button offset of 8 is added. */
		private const val MISSING_CHAIN = 39
	}
}
