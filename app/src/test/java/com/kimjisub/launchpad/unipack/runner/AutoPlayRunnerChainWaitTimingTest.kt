package com.kimjisub.launchpad.unipack.runner

import android.os.SystemClock
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** Every deadline is checked against a virtual clock, independently of host scheduling. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoPlayRunnerChainWaitTimingTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val chain = ChainObserver().apply { range = 0 until 2 }
    private val presses = mutableListOf<Pair<Long, Long>>()
    private val guides = mutableListOf<Long>()
    private val chainHints = mutableListOf<Int>()
    private lateinit var runner: AutoPlayRunner

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

    private fun tickTo(ms: Long) {
        scheduler.advanceTimeBy(ms - scheduler.currentTime)
        scheduler.runCurrent()
    }

    private fun startWaiting() {
        val elements = arrayListOf<AutoPlay.Element>(AutoPlay.Element.Delay(1000), AutoPlay.Element.Chain(1))
        repeat(3) {
            elements += AutoPlay.Element.On(0, 0, 1, it)
            elements += AutoPlay.Element.Off(0, 0, 1)
            elements += AutoPlay.Element.Delay(if (it == 2) 10000 else 500)
        }
        val pack = mockk<UniPack>(relaxed = true)
        every { pack.autoPlayTable } returns AutoPlay(elements)
        val listener = mockk<AutoPlayRunner.Listener>(relaxed = true)
        every { listener.onPadTouchOn(any(), any(), any(), any(), any()) } answers {
            presses += scheduler.currentTime to arg<Long>(4)
        }
        every { listener.onChainChange(any()) } answers { chain.value = arg(0) }
        every { listener.onGuideChainOn(any()) } answers { chainHints += arg<Int>(0) }
        every { listener.onGuidePadOn(any(), any(), any()) } answers { guides += arg<Long>(2) }
        runner = AutoPlayRunner(pack, listener, chain, eventDispatcher = dispatcher)
        AutoPlayRunner::class.java.getDeclaredField("scope").apply { isAccessible = true }.set(runner, scope)
        runner.practiceGuide = true
        runner.launch()
        tickTo(200)
        assertEquals(listOf(1), chainHints)
        // Leave the next loop tick queued at the transition time, so it reads the new flags.
        scheduler.advanceTimeBy(5000)
        assertEquals(emptyList<Pair<Long, Long>>(), presses)
    }

    private fun switchToAuto(reset: Boolean = false) {
        runner.practiceGuide = false
        runner.stepMode = false
        if (reset) runner.resetStepState()
        runner.playmode = true
        scheduler.runCurrent()
    }

    private fun assertThreeTimedPresses() {
        assertEquals("Switching must not flush the waiting notes", emptyList<Pair<Long, Long>>(), presses)
        for (at in listOf(6000L, 6500L, 7000L)) {
            tickTo(at - 1)
            assertEquals(listOf(6000L, 6500L, 7000L).filter { it < at }.map { it to it }, presses)
            tickTo(at)
            assertEquals(at to at, presses.last())
        }
    }

    @Test fun switchingFromGuideToAutoPreservesTheRemainingDelay() {
        startWaiting()
        switchToAuto()
        assertThreeTimedPresses()
    }

    @Test fun switchingWithTheScreensStepResetPreservesTheRemainingDelay() {
        startWaiting()
        switchToAuto(reset = true)
        assertThreeTimedPresses()
    }

    @Test fun pausingThenSwitchingToAutoHasTheSameDeadlines() {
        startWaiting()
        runner.playmode = false
        scheduler.runCurrent()
        tickTo(8200)
        switchToAuto(reset = true)
        assertEquals(emptyList<Pair<Long, Long>>(), presses)
        tickTo(8999)
        assertEquals(emptyList<Pair<Long, Long>>(), presses)
        tickTo(9000)
        assertEquals(listOf(9000L to 9000L), presses)
    }

    @Test fun choosingTheRequestedChainKeepsTheOriginalSongDelay() {
        startWaiting()
        chain.value = 1
        scheduler.runCurrent()
        tickTo(5201)
        assertEquals(listOf(6000L), guides)
        tickTo(6000)
        assertEquals(5, runner.progress)
    }

    @Test fun resettingTheWaitPreservesTheGuideDeadline() {
        startWaiting()
        runner.resetStepState()
        chain.value = 1
        scheduler.runCurrent()
        assertEquals(listOf(6000L), guides)
        tickTo(5999)
        assertEquals(1, runner.progress)
        tickTo(6000)
        assertEquals(5, runner.progress)
    }

    @Test fun seekingDuringTheWaitRebasesWithoutKeepingTheOldWait() {
        startWaiting()
        runner.progressOffset(4) // From chain (1) to the second press (5), at song time 1500.
        chain.value = 1
        switchToAuto(reset = true)
        assertEquals(listOf(5200L to 5200L), presses)
        tickTo(5699)
        assertEquals(1, presses.size)
        tickTo(5700)
        assertEquals(listOf(5200L to 5200L, 5700L to 5700L), presses)
    }
}
