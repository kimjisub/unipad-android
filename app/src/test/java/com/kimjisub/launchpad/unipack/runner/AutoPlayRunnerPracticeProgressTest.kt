package com.kimjisub.launchpad.unipack.runner

import android.os.SystemClock
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Runs the real loop on a controlled clock, including its idle wakeups and final callbacks. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoPlayRunnerPracticeProgressTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val chain = ChainObserver().apply { range = 0 until 3 }
    private lateinit var runner: AutoPlayRunner
    private val hints = mutableSetOf<Pair<Int, Int>>()
    private val velocities = mutableMapOf<Pair<Int, Int>, Int>()
    private val guideEvents = mutableListOf<Triple<Int, Int, Long>>()
    private val chainHints = mutableListOf<Int>()
    private var ended = false

    @Before fun setUp() {
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { scheduler.currentTime }
    }

    @After fun tearDown() {
        if (::runner.isInitialized) runner.stop()
        scheduler.runCurrent()
        scope.cancel()
        unmockkStatic(SystemClock::class)
    }

    private fun start(elements: List<AutoPlay.Element>, step: Boolean = false, delivery: CoroutineDispatcher = dispatcher) {
        val pack = mockk<UniPack>(relaxed = true)
        every { pack.autoPlayTable } returns AutoPlay(ArrayList(elements))
        val listener = object : AutoPlayRunner.Listener {
            override fun onStart() {}
            override fun onPadTouchOn(x: Int, y: Int, c: Int, num: Int, atMs: Long) {}
            override fun onPadTouchOff(x: Int, y: Int, atMs: Long) {}
            override fun onChainChange(c: Int) { chain.value = c }
            override fun onGuidePadOn(x: Int, y: Int, targetWallTimeMs: Long) {
                hints += x to y
                guideEvents += Triple(x, y, targetWallTimeMs)
            }
            override fun onGuidePadOff(x: Int, y: Int) { hints -= x to y }
            override fun onGuideLedUpdate(x: Int, y: Int, velocity: Int) { velocities[x to y] = velocity }
            override fun onGuideChainOn(c: Int) { chainHints += c }
            override fun onRemoveGuide() { hints.clear() }
            override fun chainButsRefresh() {}
            override fun onProgressUpdate(progress: Int) {}
            override fun onEnd() { ended = true }
        }
        runner = AutoPlayRunner(pack, listener, chain, eventDispatcher = delivery)
        // Only execution scheduling is replaced; the parser input, loop and all callbacks stay real.
        AutoPlayRunner::class.java.getDeclaredField("scope").apply { isAccessible = true }.set(runner, scope)
        runner.practiceGuide = true
        runner.stepMode = step
        runner.playmode = !step
        runner.launch()
        tick(10)
    }

    private fun tick(ms: Long = 1) {
        scheduler.advanceTimeBy(ms)
        scheduler.runCurrent()
    }

    private fun on(x: Int, c: Int = 0) = AutoPlay.Element.On(x, 0, c, 0)

    @Test fun aFinalTouchWaitsUntilItsPadIsPlayed() {
        start(listOf(on(0)), step = true)
        assertEquals(setOf(0 to 0), hints)
        assertFalse("Lighting the last pad must not end practice", ended)
        assertTrue(runner.active)
        runner.stepPadPressed(0, 0)
        tick()
        assertTrue("Practice must end after the last pad is played", ended)
        assertTrue(hints.isEmpty())
        assertEquals(0, velocities[0 to 0])
    }

    @Test fun theFinalChordWaitsForEveryPadAndIgnoresOtherPads() {
        start(listOf(on(0), on(1)), step = true)
        runner.stepPadPressed(2, 0)
        tick()
        assertFalse("An unrelated pad must not finish the last chord", ended)
        assertEquals(setOf(0 to 0, 1 to 0), hints)
        runner.stepPadPressed(0, 0)
        tick()
        assertFalse("One pad must not finish a two-pad chord", ended)
        assertEquals(setOf(1 to 0), hints)
        runner.stepPadPressed(1, 0)
        tick()
        assertTrue(ended)
        assertTrue(hints.isEmpty())
        assertTrue(velocities.values.all { it == 0 })
    }

    @Test fun aTrailingReleaseStillWaitsForTheFinalPress() {
        start(listOf(on(0), AutoPlay.Element.Off(0, 0, 0)), step = true)
        assertFalse("A trailing release is not proof the last pad was played", ended)
        runner.stepPadPressed(0, 0)
        tick()
        assertTrue(ended)
        assertTrue(hints.isEmpty())
    }

    @Test fun stoppingStepPracticeClearsTheUnplayedHintAndControllerLight() {
        start(listOf(on(0), AutoPlay.Element.Delay(1000), on(1)), step = true)
        assertEquals(setOf(0 to 0), hints)
        runner.stop()
        tick()
        assertTrue(ended)
        assertTrue("Stopping must clear the outstanding screen hint", hints.isEmpty())
        assertEquals("Stopping must send controller light off", 0, velocities[0 to 0])
    }

    private fun song() = listOf(
        AutoPlay.Element.Delay(200), on(0), AutoPlay.Element.Off(0, 0, 0),
        AutoPlay.Element.Delay(1000), on(1), AutoPlay.Element.Off(1, 0, 0),
        AutoPlay.Element.Delay(1000), on(2), AutoPlay.Element.Delay(60000), AutoPlay.Element.Off(2, 0, 0),
    )

    @Test fun seekingForwardRebuildsTheGuideAtTheNewSongTime() {
        start(song())
        assertEquals(setOf(0 to 0), hints)
        val previous = guideEvents.size
        runner.progressOffset(5) // From the delay's next command (1) to delay (6), at song time 1200.
        tick()
        assertTrue("The old position must no longer be hinted", hints.isEmpty())
        tick(200)
        assertEquals(listOf(Triple(2, 0, 1010L)), guideEvents.drop(previous))
        assertEquals(setOf(2 to 0), hints)
        assertEquals(7, runner.progress)
    }

    @Test fun seekingBackwardRestartsGuideTimingFromTheChosenPosition() {
        start(song())
        tick(2500)
        assertTrue("Seeking is exercised while practice is still running", runner.active)
        val previous = guideEvents.size
        val seekTime = scheduler.currentTime
        runner.progressOffset(-runner.progress)
        tick()
        assertEquals(listOf(Triple(0, 0, seekTime + 200)), guideEvents.drop(previous))
        assertEquals(setOf(0 to 0), hints)
    }

    @Test fun seekingAwayFromAChainWaitGuidesTheNewPosition() {
        start(listOf(on(0, c = 1), AutoPlay.Element.Delay(2000), on(1), AutoPlay.Element.Delay(60000)))
        assertEquals(listOf(1), chainHints)
        runner.progressOffset(2)
        tick()
        assertEquals("The old chain wait must not block the new guide", listOf(Triple(1, 0, 10L)), guideEvents)
        assertTrue(hints.isEmpty()) // The press at the selected position is already due.
    }
    @Test fun seekingWhileDeliveryWaitsDoesNotSkipTheSelectedDelay() {
        val deliveryClock = TestCoroutineScheduler()
        val delivery = StandardTestDispatcher(deliveryClock)
        start(song(), delivery = delivery)
        deliveryClock.runCurrent()
        scheduler.runCurrent()
        tick(200)
        assertEquals("The first press is waiting for delivery", 1, runner.progress)
        runner.progressOffset(-runner.progress)
        deliveryClock.runCurrent()
        scheduler.runCurrent()
        tick()
        deliveryClock.runCurrent()
        scheduler.runCurrent()
        assertEquals("The selected delay must be consumed on its rebuilt clock", 1, runner.progress)
        assertEquals(Triple(0, 0, 410L), guideEvents.last())
    }
}
