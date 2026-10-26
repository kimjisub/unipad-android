package com.kimjisub.launchpad.viewmodel

import android.media.AudioManager
import android.os.SystemClock
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.LedRunner
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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Real screen actions and parser; sound, light and guide deadlines have independent assertions. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelPracticeTransitionsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = StandardTestDispatcher(scheduler)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private data class Output(val chain: Int, val x: Int, val y: Int, val time: Long)
    private val sounds = mutableListOf<Output>()
    private val lights = mutableListOf<Output>()
    private val guides = mutableMapOf<Pair<Int, Int>, Long>()
    private val guideHistory = mutableListOf<Pair<Pair<Int, Int>, Long>>()
    private val hardwareGuides = mutableMapOf<Pair<Int, Int>, Int>()
    private lateinit var vm: PlayActivityViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { scheduler.currentTime }
    }

    private fun start(script: String) {
        val folder = tmp.newFolder()
        File(folder, "info").writeText("title=Practice transitions\nproducerName=Test\nbuttonX=8\nbuttonY=8\nchain=2\nsquareButton=true\n")
        File(folder, "sounds").mkdir()
        File(folder, "keySound").writeText("")
        File(folder, "autoPlay").writeText(script)
        vm = PlayActivityViewModel(mockk<UnipackRepository>(relaxed = true), UsageAnalytics { _, _ -> })
        vm.unipack = UniPackFolder(folder).load().loadDetail()
        vm.initState()
        vm.soundRunner = mockk<SoundRunner>(relaxed = true).also { sound ->
            every { sound.soundOn(any(), any(), any()) } answers {
                sounds += Output(vm.chain.value, firstArg(), secondArg(), scheduler.currentTime)
            }
        }
        vm.ledRunner = mockk<LedRunner>(relaxed = true).also { led ->
            every { led.eventOn(any(), any(), any()) } answers {
                lights += Output(vm.chain.value, firstArg(), secondArg(), thirdArg())
            }
        }
        vm.uiCallback = mockk<PlayActivityViewModel.UiCallback>(relaxed = true).also { ui ->
            every { ui.startGuideAnimation(any(), any(), any()) } answers {
                val pad = firstArg<Int>() to secondArg<Int>()
                guides[pad] = thirdArg()
                guideHistory += pad to thirdArg<Long>()
            }
            every { ui.stopGuideAnimation(any(), any()) } answers { guides.remove(firstArg<Int>() to secondArg<Int>()) }
            every { ui.sendGuideLedToLaunchpad(any(), any(), any()) } answers {
                hardwareGuides[firstArg<Int>() to secondArg<Int>()] = thirdArg()
            }
        }
        vm.initAutoPlayRunner()
        val runner = checkNotNull(vm.autoPlayRunner)
        runner.javaClass.getDeclaredField("scope").apply { isAccessible = true }.set(runner, scope)
        vm.switchPlayMode(PlayMode.StepPractice)
        advance(1)
    }

    private fun advance(ms: Long) { scheduler.advanceTimeBy(ms); scheduler.runCurrent() }
    private fun press(x: Int, y: Int = 0) {
        vm.padTouch(x, y, true)
        vm.padTouch(x, y, false)
        advance(1)
    }
    private fun assertGuides(vararg pads: Pair<Int, Int>) {
        assertEquals(pads.toSet(), guides.keys)
        for (x in 0..2) for (y in 0..2) {
            assertEquals("Screen guide at $x,$y", x to y in pads, vm.channelManager.get(x, y)?.channel == Channel.GUIDE)
            assertEquals("Controller guide at $x,$y", x to y in pads, (hardwareGuides[x to y] ?: 0) > 0)
        }
    }
    private fun assertOutputs(vararg expected: Output) {
        assertEquals("Sound requests", expected.toList(), sounds)
        assertEquals("Light requests must use the same pads, chain, order and song deadlines", expected.toList(), lights)
    }

    @After fun tearDown() {
        clearPlayViewModel(if (::vm.isInitialized) vm else null, drain = { scheduler.runCurrent() }) {
            scheduler.runCurrent()
            scope.cancel()
            unmockkStatic(SystemClock::class)
            Dispatchers.resetMain()
        }
    }

    private fun timedGuideAfterReplay(pauseMs: Long) {
        start("on 1 1\ndelay 5000\non 1 2\ndelay 5000\non 1 3\ndelay 5000\n")
        assertGuides(0 to 0)
        press(0)
        assertGuides(0 to 1)
        advance(300)
        val replayAt = scheduler.currentTime
        vm.switchPlayMode(PlayMode.AutoPlay)
        scheduler.runCurrent()
        assertOutputs(Output(0, 0, 0, 1), Output(0, 0, 1, replayAt))
        assertGuides()
        advance(1000)
        vm.switchPlayMode(PlayMode.GuidePlay)
        advance(1)
        assertGuides()
        if (pauseMs > 0) {
            vm.autoPlayPause()
            // The screen pause deliberately enters step practice until Play is pressed again.
            advance(pauseMs)
            vm.autoPlayResume()
            scheduler.runCurrent()
            assertGuides()
        }
        guideHistory.clear()
        val target = replayAt + 5000 + pauseMs
        advance(target - 801 - scheduler.currentTime)
        assertGuides()
        advance(1)
        assertGuides(0 to 2)
        assertEquals(target, guides[0 to 2])
        assertTrue("A sounded pad must never be guided again", guideHistory.all { it.first == 0 to 2 })
        advance(800)
        assertGuides()
        assertEquals("Guide playback stays silent", 2, sounds.size)
        assertTrue(vm.autoPlayRunner!!.active)
        advance(5000)
        assertFalse("The trailing delay ends at the original song deadline", vm.autoPlayRunner!!.active)
        assertEquals(PlayMode.None, vm.playMode)
    }

    @Test fun timedGuideAfterStepReplayKeepsTheSongPosition() = timedGuideAfterReplay(0)
    @Test fun timedGuideAfterStepReplayAndPauseKeepsTheSongPosition() = timedGuideAfterReplay(3000)

    private fun returningToSteps(chainChange: Boolean, deliverFirst: Boolean) {
        start("on 1 1\non 2 1\ndelay 20\non 3 1\ndelay 5000\n" +
            (if (chainChange) "chain 2\n" else "") + "on 1 1\n")
        assertGuides(0 to 0, 1 to 0, 2 to 0)
        press(0)
        assertGuides(1 to 0, 2 to 0)
        vm.switchPlayMode(PlayMode.AutoPlay)
        if (deliverFirst) {
            scheduler.runCurrent()
            assertEquals(listOf(0, 1), sounds.map { it.x })
            assertGuides()
        }
        vm.switchPlayMode(PlayMode.StepPractice)
        advance(1)
        val pending = if (deliverFirst) listOf(2) else listOf(1, 2)
        assertGuides(*pending.map { it to 0 }.toTypedArray())
        pending.forEach { press(it) }
        if (chainChange) {
            assertGuides()
            assertEquals(Channel.GUIDE, vm.channelManager.get(-1, PlayActivityViewModel.CHAIN_INDEX_OFFSET + 1)?.channel)
            vm.selectChain(1)
            advance(1)
        }
        assertGuides(0 to 0)
        press(0)
        advance(1)
        assertFalse("Pressing the last step ends practice", vm.autoPlayRunner!!.active)
        assertGuides()
        assertEquals("Every input must request its light too", sounds, lights)
        val newRunAt = scheduler.currentTime
        sounds.clear(); lights.clear()
        vm.switchPlayMode(PlayMode.AutoPlay)
        scheduler.runCurrent()
        assertOutputs(Output(0, 0, 0, newRunAt), Output(0, 1, 0, newRunAt))
        advance(20)
        assertEquals(listOf(0, 1, 2), sounds.map { it.x })
        advance(5000)
        assertEquals("The earlier preparation cannot suppress the next chain's first pad", listOf(0, 1, 2, 0), sounds.map { it.x })
        assertEquals(if (chainChange) 1 else 0, sounds.last().chain)
        assertEquals(sounds, lights)
        advance(1)
        assertFalse(vm.autoPlayRunner!!.active)
    }

    @Test fun immediateReturnRestoresEveryUnplayedStep() = returningToSteps(false, false)
    @Test fun returnBeforeReplayFinishesRestoresRemainingSteps() = returningToSteps(false, true)
    @Test fun immediateReturnAcrossAChainWaitDoesNotReusePreparation() = returningToSteps(true, false)
    @Test fun returnAfterPartialReplayAcrossAChainWaitDoesNotReusePreparation() = returningToSteps(true, true)

    private fun interruptionBeforeReplay(away: Long, finalGroup: Boolean) {
        start("on 1 1\ndelay 5000\non 2 1\non 3 1\n" + if (finalGroup) "" else "delay 5000\non 1 2\n")
        press(0)
        assertGuides(1 to 0, 2 to 0)
        press(1)
        assertGuides(2 to 0)
        vm.interruptPlayback()
        scheduler.runCurrent()
        assertGuides()
        val progress = vm.autoPlayRunner!!.progress
        advance(away)
        vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        scheduler.runCurrent()
        assertGuides()
        assertTrue(vm.autoPlayRunner!!.suspended)
        assertEquals(progress, vm.autoPlayRunner!!.progress)
        assertEquals(2, sounds.size)
        vm.autoPlayResume()
        scheduler.runCurrent()
        assertGuides(2 to 0)
        val replayAt = scheduler.currentTime
        vm.switchPlayMode(PlayMode.AutoPlay)
        scheduler.runCurrent()
        assertOutputs(Output(0, 0, 0, 1), Output(0, 1, 0, 2), Output(0, 2, 0, replayAt))
        assertGuides()
        if (!finalGroup) {
            advance(4999)
            assertEquals(3, sounds.size)
            advance(1)
            assertEquals(Output(0, 0, 1, replayAt + 5000), sounds.last())
        }
        advance(1)
        assertFalse(vm.autoPlayRunner!!.active)
        assertEquals(PlayMode.None, vm.playMode)
    }

    @Test fun shortInterruptionKeepsOnlyThePendingPad() = interruptionBeforeReplay(1000, false)
    @Test fun longInterruptionKeepsOnlyThePendingPad() = interruptionBeforeReplay(10000, false)
    @Test fun shortInterruptionBeforeTheFinalGroupEndsAfterReplay() = interruptionBeforeReplay(1000, true)
    @Test fun longInterruptionBeforeTheFinalGroupEndsAfterReplay() = interruptionBeforeReplay(10000, true)
}
