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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The song clock and worker are advanced together, without wall-time assertions. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoPlayRunnerContinuationTest {
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val guides = mutableListOf<Pair<Int, Long>>()
    private val presses = mutableListOf<Int>()
    private var ends = 0
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

    private fun start(guide: Boolean) {
        val pack = mockk<UniPack>(relaxed = true)
        every { pack.autoPlayTable } returns AutoPlay(arrayListOf(
            AutoPlay.Element.On(0, 0, 0, 0), AutoPlay.Element.Off(0, 0, 0),
            AutoPlay.Element.Delay(500), AutoPlay.Element.On(1, 0, 0, 0),
            AutoPlay.Element.Off(1, 0, 0), AutoPlay.Element.Delay(1000),
        ))
        val listener = mockk<AutoPlayRunner.Listener>(relaxed = true)
        every { listener.onGuidePadOn(any(), any(), any()) } answers { guides += arg<Int>(0) to arg<Long>(2) }
        every { listener.onPadTouchOn(any(), any(), any(), any(), any()) } answers { presses += arg<Int>(0) }
        every { listener.onEnd() } answers { ends++ }
        runner = AutoPlayRunner(pack, listener, ChainObserver(), eventDispatcher = dispatcher)
        AutoPlayRunner::class.java.getDeclaredField("scope").apply { isAccessible = true }.set(runner, scope)
        runner.practiceGuide = guide
        runner.launch()
        scheduler.runCurrent()
    }

    private fun at(ms: Long) {
        scheduler.advanceTimeBy(ms - scheduler.currentTime)
        scheduler.runCurrent()
    }

    @Test fun enablingGuideOnlyShowsUnprocessedNotes() {
        start(false)
        at(200)
        assertEquals(listOf(0), presses)
        runner.practiceGuide = true
        scheduler.runCurrent()
        at(201)
        assertEquals("Already played notes must not be guided again", listOf(1 to 500L), guides)
    }

    @Test fun pausingGuideInTheFinalWaitPreservesTheRemainder() {
        start(true)
        at(750)
        assertEquals(6, runner.progress)
        runner.playmode = false
        runner.stepMode = true // The screen pauses timed guidance into step practice.
        scheduler.runCurrent()
        at(3750)
        assertTrue("Step practice cannot finish a pending timed wait", runner.active)
        assertEquals(0, ends)
        runner.resume()
        scheduler.runCurrent()
        at(4499)
        assertEquals("The final wait still has one millisecond left", 0, ends)
        at(4500)
        assertEquals(1, ends)
    }
}
