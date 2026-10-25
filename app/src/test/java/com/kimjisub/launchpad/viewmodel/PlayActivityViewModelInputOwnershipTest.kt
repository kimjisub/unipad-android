package com.kimjisub.launchpad.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import com.kimjisub.design.touch.PadTouchTracker
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.LedRunner
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Real view model and sound/LED runners; only the output engine and clock are controlled. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelInputOwnershipTest {
    @get:Rule val tmp = TemporaryFolder()
    private val main = FakeMainDispatcher()
    private val clock = AtomicLong(1000)
    private lateinit var vm: PlayActivityViewModel
    private lateinit var screen: PadTouchTracker
    private val tick = LedRunner::class.java.getDeclaredMethod("loop").apply { isAccessible = true }
    private val engine = RecordingEngine()

    private class RecordingEngine : SoundRunner.Engine {
        val voices = linkedMapOf<Int, Int>()
        var plays = 0
        override fun start() = true
        override fun stop() = Unit
        override fun decode(file: File) = OboeAudioEngine.DecodedAudio(shortArrayOf(1), 1, 1, 44100)
        override fun load(decoded: OboeAudioEngine.DecodedAudio) = 1
        override fun unloadSound(soundId: Int) = Unit
        override fun unloadAll() = Unit
        override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int) = (++plays).also { voices[it] = loop }
        override fun stopVoice(stopKey: Int) { voices.remove(stopKey) }
        override fun stopAllVoices() { voices.clear() }
    }

    @Before fun setUp() {
        Dispatchers.setMain(main)
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { clock.get() }
        val folder = tmp.newFolder()
        File(folder, "info").writeText("title=Inputs\nproducerName=Test\nbuttonX=8\nbuttonY=8\nchain=1\nsquareButton=true\n")
        File(folder, "sounds").mkdir()
        File(folder, "sounds/a.wav").writeText("")
        File(folder, "keySound").writeText("1 1 1 a.wav 0\n")
        File(folder, "keyLED").mkdir()
        File(folder, "keyLED/1 1 1 0").writeText("on 1 1 a 4\nd 100000\n")
        vm = PlayActivityViewModel(mockk<UnipackRepository>(relaxed = true), UsageAnalytics { _, _ -> }, engine)
        vm.unipack = UniPackFolder(folder).load().loadDetail()
        vm.initState()
        vm.setupCheckBoxListeners()
        vm.initPlayback()
        val deadline = System.nanoTime() + 5_000_000_000L
        while (vm.unipack.soundGet(0, 0, 0)?.id != 1 && System.nanoTime() < deadline) {
            main.runAll()
            Thread.sleep(5)
        }
        main.runAll()
        assertEquals(1, vm.unipack.soundGet(0, 0, 0)?.id)
        vm.ledRunner!!.stop()
        vm.ledRunner = LedRunner(vm.unipack, vm.ledRunnerListener, vm.chain, loopDelay = 3_600_000)
        vm.screenVisible = true
        vm.scbFeedbackLight.setChecked(true)
        screen = PadTouchTracker { pointerId, x, y, down -> vm.padTouch(x, y, down, pointerId) }.apply { setGrid(8, 8) }
    }

    @After fun tearDown() {
        if (::vm.isInitialized) ViewModelStore().apply { put("play", vm); clear() }
        main.runAll()
        unmockkStatic(SystemClock::class)
        Dispatchers.resetMain()
    }

    private fun advance() {
        clock.addAndGet(20)
        repeat(2) { tick.invoke(vm.ledRunner) }
        main.runAll()
    }

    private fun fingerDown(id: Long) { screen.down(id, 50f, 50f, 800, 800); advance() }
    private fun auto(down: Boolean) { vm.autoPlayPadTouch(0, 0, down, clock.get()); advance() }

    /** Observe the audible loop, the pack light below feedback, and feedback independently. */
    private fun state(): List<Any?> {
        val pressed = vm.channelManager.get(0, 0)?.channel == Channel.PRESSED
        vm.channelManager.setBtnIgnore(Channel.PRESSED, true)
        val light = vm.channelManager.get(0, 0)?.takeIf { it.channel == Channel.LED }?.code
        vm.channelManager.setBtnIgnore(Channel.PRESSED, false)
        return listOf(engine.voices.values.count { it == -1 }, light, pressed)
    }

    private fun twoFingers(firstReleased: Long) {
        fingerDown(10)
        fingerDown(20)
        assertEquals("A re-press replaces the loop, rather than layering another voice", 2, engine.plays)
        assertEquals(listOf(1, 4, true), state())
        screen.up(firstReleased)
        advance()
        val outputRemains = firstReleased == 10L
        assertEquals("Only the input that started the current output may end it",
            listOf(if (outputRemains) 1 else 0, if (outputRemains) 4 else null, true), state())
        screen.up(if (firstReleased == 10L) 20 else 10)
        advance()
        assertEquals(listOf(0, null, false), state())
    }

    @Test fun firstFingerReleaseKeepsTheCurrentLoopAndBothLights() = twoFingers(10)
    @Test fun laterFingerReleaseEndsItsOutputWhileTheFirstFingerRemains() = twoFingers(20)

    private fun fingerAndController(controllerFirst: Boolean, laterFirst: Boolean) {
        if (controllerFirst) vm.controllerPadTouch(0, 0, true) else fingerDown(10)
        if (controllerFirst) fingerDown(10) else vm.controllerPadTouch(0, 0, true)
        advance()
        assertEquals(listOf(1, 4, true), state())
        val releaseController = controllerFirst != laterFirst
        if (releaseController) vm.controllerPadTouch(0, 0, false) else screen.up(10)
        advance()
        assertEquals(listOf(if (laterFirst) 0 else 1, if (laterFirst) null else 4, true), state())
        if (releaseController) screen.up(10) else vm.controllerPadTouch(0, 0, false)
        advance()
        assertEquals(listOf(0, null, false), state())
    }

    @Test fun laterControllerReleaseEndsItsOutputWhileTheFingerRemains() = fingerAndController(false, true)
    @Test fun earlierFingerReleaseKeepsTheControllersOutput() = fingerAndController(false, false)
    @Test fun laterFingerReleaseEndsItsOutputWhileTheControllerRemains() = fingerAndController(true, true)
    @Test fun earlierControllerReleaseKeepsTheFingersOutput() = fingerAndController(true, false)

    @Test fun unmatchedReleasesCannotEndAnotherFingersOutput() {
        fingerDown(10)
        vm.padTouch(0, 0, false, 20)
        vm.controllerPadTouch(0, 0, false)
        auto(false)
        assertEquals(listOf(1, 4, true), state())
        screen.up(10)
        advance()
        assertEquals(listOf(0, null, false), state())
    }

    @Test fun screenReleaseDoesNotEndTheNewerAutoplayLoop() {
        fingerDown(10)
        auto(true)
        screen.up(10)
        advance()
        assertEquals(listOf(1, 4, true), state())
        auto(false)
        assertEquals(listOf(0, null, false), state())
    }

    @Test fun controllerReleaseDoesNotEndTheNewerAutoplayLoop() {
        vm.controllerPadTouch(0, 0, true)
        auto(true)
        vm.controllerPadTouch(0, 0, false)
        advance()
        assertEquals(listOf(1, 4, true), state())
        auto(false)
        assertEquals(listOf(0, null, false), state())
    }

    @Test fun autoplayReleaseDoesNotEndTheNewerPlayerLoop() {
        auto(true)
        fingerDown(10)
        auto(false)
        assertEquals(listOf(1, 4, true), state())
        screen.up(10)
        advance()
        assertEquals(listOf(0, null, false), state())
    }

    @Test fun autoplayReleaseEndsItsOwnLoopEvenWhileAnEarlierFingerRemains() {
        fingerDown(10)
        auto(true)
        auto(false)
        assertEquals(listOf(0, null, true), state())
        screen.up(10)
        advance()
        assertEquals(listOf(0, null, false), state())
    }

    @Test fun playerReleaseEndsItsOwnLoopEvenWhileAnEarlierAutoplayHoldRemains() {
        auto(true)
        fingerDown(10)
        screen.up(10)
        advance()
        assertEquals(listOf(0, null, true), state())
        auto(false)
        assertEquals(listOf(0, null, false), state())
    }

    @Test fun anUnmatchedPlayerReleaseCannotEndAutoplay() {
        auto(true)
        vm.padTouch(0, 0, false)
        advance()
        assertEquals(listOf(1, 4, true), state())
    }

    @Test fun cancellingBothFingersReleasesTheLoopOnlyOnceAndAllowsNewInput() {
        fingerDown(10)
        fingerDown(20)
        screen.cancel()
        advance()
        assertEquals(listOf(0, null, false), state())
        screen.up(10)
        screen.up(20)
        fingerDown(10)
        assertTrue(engine.voices.isNotEmpty())
        screen.up(10)
        advance()
        assertEquals(listOf(0, null, false), state())
    }
}
