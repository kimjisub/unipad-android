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
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicInteger

/** Frozen pack time makes a seek's new guide and sound deadlines independent of host speed. */
class AutoPlayRunnerSeekTest {
	private val clock = AtomicLong(0)
	private val guideRemovals = AtomicInteger(0)
	private val guides = CopyOnWriteArrayList<Pair<Int, Long>>()
	private val lightsOff = CopyOnWriteArrayList<Int>()
	private val presses = CopyOnWriteArrayList<Pair<Int, Long>>()
	private val chainGuides = CopyOnWriteArrayList<Int>()
	private val ended = CountDownLatch(1)
	private lateinit var runner: AutoPlayRunner

	@Before fun setUp() {
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { clock.get() }
	}

	@After fun tearDown() {
		if (::runner.isInitialized) {
			runner.stop()
			assertTrue("Runner did not stop", ended.await(5, TimeUnit.SECONDS))
		}
		unmockkStatic(SystemClock::class)
	}

	private fun await(message: String, check: () -> Boolean) {
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
		while (!check() && System.nanoTime() < deadline) Thread.sleep(1)
		assertTrue(message, check())
	}

	private fun runsInRunner(stack: Array<StackTraceElement>) = stack.any {
		it.className == AutoPlayRunner::class.java.name || it.className.startsWith(AutoPlayRunner::class.java.name + "$")
	}

	private fun start(practice: Boolean, firstChain: Int = 0) {
		val pack = mockk<UniPack>(relaxed = true)
		// Index 3 starts at pack time 5500, with pad 1 due 500 ms later.
		every { pack.autoPlayTable } returns AutoPlay(arrayListOf(
			AutoPlay.Element.Delay(500),
			AutoPlay.Element.On(0, 0, firstChain, 0),
			AutoPlay.Element.Delay(5000),
			AutoPlay.Element.Delay(500),
			AutoPlay.Element.On(1, 0, 0, 0),
			AutoPlay.Element.Delay(10000),
			AutoPlay.Element.On(2, 0, 0, 0),
		))
		val listener = object : AutoPlayRunner.Listener {
			override fun onStart() {}
			override fun onPadTouchOn(x: Int, y: Int, c: Int, num: Int, atMs: Long) { presses += x to atMs }
			override fun onPadTouchOff(x: Int, y: Int, atMs: Long) {}
			override fun onChainChange(c: Int) {}
			override fun onGuidePadOn(x: Int, y: Int, targetWallTimeMs: Long) { guides += x to targetWallTimeMs }
			override fun onGuidePadOff(x: Int, y: Int) {}
			override fun onGuideLedUpdate(x: Int, y: Int, velocity: Int) { if (velocity == 0) lightsOff += x }
			override fun onGuideChainOn(c: Int) { chainGuides += c }
			override fun onRemoveGuide() { guideRemovals.incrementAndGet() }
			override fun chainButsRefresh() {}
			override fun onProgressUpdate(progress: Int) {}
			override fun onEnd() { ended.countDown() }
		}
		runner = AutoPlayRunner(pack, listener, ChainObserver().apply { range = 0..1 })
		runner.practiceGuide = practice
		runner.launch()
		await("Initial delay was not read") { runner.progress == 1 || chainGuides.isNotEmpty() }
	}

	@Test fun nextInPracticeClearsTheOldLightAndStartsGuidesAtTheNewTime() {
		start(practice = true)
		await("First guide missing") { guides.contains(0 to 500L) }
		runner.progressOffset(2)
		await("Guide did not move to the new time") { guides.contains(1 to 500L) }
		assertTrue("The old hardware guide must turn off", 0 in lightsOff)
	}

	@Test fun previousInPracticeRewindsTheGuideClockAndIndex() {
		start(practice = true)
		clock.set(5500)
		await("Second guide missing") { guides.any { it.first == 1 } && runner.progress == 4 }
		runner.progressOffset(-4)
		await("Previous did not guide the first pad again") { guides.contains(0 to 6000L) }
		assertTrue("The old hardware guide must turn off", 1 in lightsOff)
	}

	@Test fun nextEscapesAChainWaitAtTheOldPosition() {
		start(practice = true, firstChain = 1)
		await("Chain wait missing") { chainGuides.contains(1) }
		runner.progressOffset(3) // The chain wait precedes consumption of index 0.
		await("Seek kept waiting for the old chain") { guides.contains(1 to 500L) }
	}

	@Test fun nextInAutoplayWaitsOnlyForTheDelayAtTheNewPosition() {
		start(practice = false)
		runner.progressOffset(2)
		await("Target delay was not read") { runner.progress == 4 }
		assertEquals(emptyList<Pair<Int, Long>>(), presses)
		clock.set(500)
		await("Target press did not play on its new deadline") { presses.isNotEmpty() }
		assertEquals(listOf(1 to 500L), presses.toList())
	}
	@Test fun previousInAutoplayRestartsTheEarlierPadsDelay() {
		start(practice = false)
		clock.set(5500)
		await("First pad was not played") { presses.isNotEmpty() && runner.progress == 4 }
		presses.clear()
		runner.progressOffset(-4)
		await("The earlier delay was not read") { runner.progress == 1 }
		clock.set(6000)
		await("The earlier pad was not played on its new deadline") { presses.isNotEmpty() }
		assertEquals(listOf(0 to 6000L), presses.toList())
	}

	@Test fun seekWhilePausedStartsItsNewDelayWhenResumed() {
		start(practice = false)
		runner.playmode = false
		val removals = guideRemovals.get()
		runner.progressOffset(2)
		await("Paused seek did not clear the old guide") { guideRemovals.get() > removals }
		clock.set(10000)
		runner.playmode = true
		await("Target delay was not read after resume") { runner.progress == 4 }
		clock.set(10500)
		await("Seeking while paused kept the old clock") { presses.isNotEmpty() }
		assertEquals(listOf(1 to 10500L), presses.toList())
	}

	@Test fun seekWhilePracticeIsPausedStartsGuidesAtResumeTime() {
		start(practice = true)
		await("Initial guide missing") { guides.contains(0 to 500L) }
		runner.playmode = false
		val removals = guideRemovals.get()
		runner.progressOffset(2)
		await("Paused seek did not clear the old guide") { guideRemovals.get() > removals }
		clock.set(10000)
		runner.playmode = true
		await("Paused seek kept the old guide clock") { guides.contains(1 to 10500L) }
		assertTrue("Seeking practice must not play sounds", presses.isEmpty())
	}

	@Test fun seekInStepPracticeRebasesTheCurrentPositionWhenContinuousPracticeResumes() {
		start(practice = true)
		await("Initial guide missing") { guides.contains(0 to 500L) }
		runner.playmode = false
		runner.stepMode = true
		runner.progressOffset(2)
		await("Step seek did not guide the new pad") { guides.contains(1 to 0L) && runner.progress == 5 }
		clock.set(10000)
		runner.stepMode = false
		runner.resetStepState()
		runner.playmode = true
		await("Resume did not keep the delay after the step hint") { runner.progress == 6 }
		clock.set(19200)
		await("Step seek kept the old continuous guide clock") { guides.contains(2 to 20000L) }
	}

	@Test fun stepSeekDuringAStepScanGuidesTheStepAtTheNewPosition() {
		start(practice = true)
		await("Initial guide missing") { guides.contains(0 to 500L) }
		runner.playmode = false
		val pressedKeysLock = checkNotNull(AutoPlayRunner::class.java.getDeclaredField("pressedKeysLock")
			.apply { isAccessible = true }.get(runner))
		val seek = Thread { runner.progressOffset(2) }
		synchronized(pressedKeysLock) {
			// Hold the runner at the start of a step scan so it scans the seek's position before it
			// takes the seek. Neither that scan nor a step reset from the caller after it may move
			// the step past the new position.
			runner.stepMode = true
			await("Runner did not reach the step scan") {
				Thread.getAllStackTraces().any { (thread, stack) ->
					thread.state == Thread.State.BLOCKED && runsInRunner(stack)
				}
			}
			seek.start()
			seek.join(TimeUnit.SECONDS.toMillis(5))
			assertTrue("The seek waited to reset the step on the caller thread", !seek.isAlive)
		}
		seek.join()
		val pendingSeek = checkNotNull(AutoPlayRunner::class.java.getDeclaredField("pendingSeek")
			.apply { isAccessible = true }.get(runner) as AtomicReference<*>)
		await("The runner did not take the seek and wait for a step") {
			pendingSeek.get() == null && Thread.getAllStackTraces().values.none(::runsInRunner)
		}
		assertTrue("Step seek did not guide the new pad", guides.contains(1 to 0L))
		assertEquals("Step seek moved past the new position", 5, runner.progress)
		assertTrue("Step seek guided the step after the new position", guides.none { it.first == 2 })
	}

	@Test fun pauseAndSeekBetweenTicksDoNotUseTheEarlierPlayingState() {
		start(practice = true)
		await("Initial guide missing") { guides.contains(0 to 500L) }
		val progressLock = checkNotNull(AutoPlayRunner::class.java.getDeclaredField("progressValue")
			.apply { isAccessible = true }.get(runner))
		val removals = guideRemovals.get()
		synchronized(progressLock) {
			// Hold the request handoff until the loop reaches it, then pause and seek
			// on the caller thread. The assertions below concern the guide's real deadline.
			await("Runner did not reach the request handoff") {
				Thread.getAllStackTraces().any { (thread, stack) ->
					thread.state == Thread.State.BLOCKED &&
						stack.any { it.className.startsWith(AutoPlayRunner::class.java.name) }
				}
			}
			runner.playmode = false
			runner.progressOffset(2)
			clock.set(10000)
		}
		await("Seek was not handled") { guideRemovals.get() > removals }
		clock.set(20000)
		runner.playmode = true
		await("The seek used the playing state from before the pause") { guides.contains(1 to 20500L) }
	}

}
