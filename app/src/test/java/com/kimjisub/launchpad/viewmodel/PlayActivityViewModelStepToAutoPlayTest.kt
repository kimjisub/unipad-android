package com.kimjisub.launchpad.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

/** Both screen actions use the real view model, parser and runner on one controlled clock. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class PlayActivityViewModelStepToAutoPlayTest(
    private val action: String,
    private val pausedMs: Long,
    private val playedPad: Int,
    private val finalGroup: Boolean,
) {
    @get:Rule val tmp = TemporaryFolder()
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val plays = mutableListOf<Pair<Int, Long>>()
    private val hints = mutableSetOf<Pair<Int, Int>>()
    private lateinit var vm: PlayActivityViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { scheduler.currentTime }
        val folder = tmp.newFolder()
        File(folder, "info").writeText("title=Step transition\nproducerName=Test\nbuttonX=8\nbuttonY=8\nchain=2\nsquareButton=true\n")
        File(folder, "sounds").mkdir()
        File(folder, "keySound").writeText("")
        File(folder, "autoPlay").writeText(
            "delay 500\ntouch 1 1\ndelay 2000\non 2 1\non 3 1\n" +
                if (finalGroup) "" else "delay 1000\ntouch 4 1\ndelay 60000\n",
        )
        vm = PlayActivityViewModel(mockk<UnipackRepository>(relaxed = true), UsageAnalytics { _, _ -> })
        vm.unipack = UniPackFolder(folder).load().loadDetail()
        vm.initState()
        vm.soundRunner = mockk<SoundRunner>(relaxed = true).also { sound ->
            every { sound.soundOn(any(), any(), any()) } answers {
                if (!thirdArg<Boolean>()) plays += firstArg<Int>() to scheduler.currentTime
            }
        }
        vm.uiCallback = mockk<PlayActivityViewModel.UiCallback>(relaxed = true).also { ui ->
            every { ui.startGuideAnimation(any(), any(), any()) } answers {
                hints += firstArg<Int>() to secondArg<Int>()
            }
            every { ui.stopGuideAnimation(any(), any()) } answers {
                hints -= firstArg<Int>() to secondArg<Int>()
            }
        }
        vm.initAutoPlayRunner()
        val runner = checkNotNull(vm.autoPlayRunner)
        runner.javaClass.getDeclaredField("scope").apply { isAccessible = true }.set(runner, scope)
        vm.switchPlayMode(PlayMode.StepPractice)
        tick()
        assertEquals(setOf(0 to 0), hints)
        press(0)
        assertEquals("The previous group was completed", setOf(1 to 0, 2 to 0), hints)
        if (playedPad >= 0) {
            press(playedPad)
            assertEquals(setOf((3 - playedPad) to 0), hints)
        }
        vm.autoPlayPause()
        scheduler.advanceTimeBy(pausedMs)
        scheduler.runCurrent()
        assertTrue("An unplayed final group must keep practice alive", runner.active)
    }

    private fun tick() {
        scheduler.advanceTimeBy(1)
        scheduler.runCurrent()
    }

    private fun press(x: Int) {
        vm.padTouch(x, 0, true)
        vm.padTouch(x, 0, false)
        tick()
    }

    @After fun tearDown() {
        if (::vm.isInitialized) ViewModelStore().apply { put("play", vm); clear() }
        scheduler.runCurrent()
        scope.cancel()
        unmockkStatic(SystemClock::class)
        Dispatchers.resetMain()
    }

    @Test fun audibleTransitionsReplayOnlyPendingPadsWhileGuideResumeStaysSilent() {
        val resumedAt = scheduler.currentTime
        val previousProgress = vm.autoPlayRunner!!.progress
        if (action == "guideResume") {
            // Resuming practice keeps timed guidance; it is outside the audible autoplay criteria.
            vm.autoPlayResume()
            scheduler.runCurrent()
            assertTrue(vm.autoPlayRunner!!.practiceGuide)
            assertFalse(vm.autoPlayRunner!!.stepMode)
            assertTrue(plays.isEmpty())
            assertEquals("Guide resume keeps the existing progress handling",
                previousProgress + if (finalGroup) 0 else 1, vm.autoPlayRunner!!.progress)
            scheduler.advanceTimeBy(5000)
            scheduler.runCurrent()
            assertTrue("Timed guidance must remain silent", plays.isEmpty())
            return
        }
        vm.switchPlayMode(PlayMode.AutoPlay)
        if (action == "modeThenResume") {
            // Pause before the loop can deliver the transition, then use the real transport path.
            vm.autoPlayPause()
            scheduler.runCurrent()
            assertTrue(plays.isEmpty())
            vm.autoPlayResume()
        }
        scheduler.runCurrent()
        val expectedPads = listOf(1, 2).filter { it != playedPad }
        assertEquals("Only unplayed pads must sound immediately, including after a zero-length pause",
            expectedPads.map { it to resumedAt }, plays)
        if (finalGroup) {
            tick()
            assertFalse("The final group ends only after sounding its pending pads", vm.autoPlayRunner!!.active)
        } else {
            scheduler.advanceTimeBy(999)
            scheduler.runCurrent()
            assertEquals(expectedPads.map { it to resumedAt }, plays)
            scheduler.advanceTimeBy(1)
            scheduler.runCurrent()
            assertEquals("The next group keeps the pack's original interval",
                expectedPads.map { it to resumedAt } + (3 to resumedAt + 1000), plays)
            val sounded = plays.toList()
            vm.autoPlayPause()
            scheduler.advanceTimeBy(300)
            scheduler.runCurrent()
            vm.autoPlayResume()
            scheduler.runCurrent()
            assertEquals("Resuming after delivery must not repeat the recovered chord", sounded, plays)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "action={0}, pauseMs={1}, playedPad={2}, final={3}")
        fun cases(): List<Array<Any>> = listOf("mode", "modeThenResume", "guideResume").flatMap { action ->
            listOf(0L, 1L, 300L, 5000L).flatMap { pause ->
                listOf(-1, 1, 2).flatMap { played ->
                    listOf(false, true).map { final -> arrayOf(action, pause, played, final) }
                }
            }
        }
    }
}
