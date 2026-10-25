package com.kimjisub.launchpad.viewmodel

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.AutoPlayRunner
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelPlaybackSetupTest {
    @get:Rule val tmp = TemporaryFolder()
    private val main = FakeMainDispatcher()
    private lateinit var vm: PlayActivityViewModel

    @Before fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
        Dispatchers.setMain(main)
        mockkObject(MidiConnection)
        every { MidiConnection.driver } returns mockk(relaxed = true)
    }

    @After fun tearDown() {
        clearPlayViewModel(if (::vm.isInitialized) vm else null, drain = { main.runAll() }) {
            main.runAll()
            unmockkObject(MidiConnection)
            Dispatchers.resetMain()
            ArchTaskExecutor.getInstance().setDelegate(null)
        }
    }

    private fun load(square: Boolean, lights: Boolean = true, playable: Boolean = true) {
        val root = tmp.newFolder()
        File(root, "info").writeText("title=Test\nproducerName=Test\nbuttonX=2\nbuttonY=2\nchain=2\nsquareButton=$square\n")
        File(root, "sounds").mkdir()
        File(root, "sounds/a.wav").writeBytes(byteArrayOf(1))
        File(root, "keySound").writeText("1 1 1 a.wav\n1 1 1 a.wav\n1 1 1 a.wav\n")
        if (lights) {
            File(root, "keyLED").mkdir()
            repeat(3) { File(root, "keyLED/1 1 1 1 ${it + 1}").writeText("on 1 1 a 5\ndelay 1000\noff 1 1\n") }
        }
        File(root, "autoPlay").writeText("on 1 1\ndelay 60000\noff 1 1\n")
        val engine = mockk<SoundRunner.Engine>(relaxed = true)
        every { engine.start() } returns true
        every { engine.decode(any()) } returns if (playable) OboeAudioEngine.DecodedAudio(ShortArray(1), 1, 1, 44100) else null
        every { engine.load(any()) } returns 1
        vm = PlayActivityViewModel(mockk<UnipackRepository>(relaxed = true), UsageAnalytics { _, _ -> }, engine)
        vm.uiCallback = mockk(relaxed = true)
        vm.unipack = UniPackFolder(root).load().loadDetail()
        vm.initState()
        vm.setupCheckBoxVisibility()
        vm.setupCheckBoxListeners()
        vm.initPlayback()
    }

    @Test fun roundPadsWithKeyLedKeepAnimationEnabled() {
        load(square = false)
        vm.screenVisible = true
        assertTrue("A pack's shape must not disable its animations", vm.scbLed.checked)
        assertTrue("The real LED runner must run on the visible screen", vm.ledRunner?.active == true)
        assertFalse(vm.scbFeedbackLight.checked)
    }

    @Test fun roundPadsWithoutKeyLedKeepPressFeedbackEnabled() {
        load(square = false, lights = false)
        assertTrue("A pack without keyLED must still show press feedback", vm.scbFeedbackLight.checked)
    }

    @Test fun squarePadsKeepExistingLightDefaults() {
        load(square = true)
        assertTrue(vm.scbLed.checked)
        assertFalse(vm.scbFeedbackLight.checked)
    }

    @Test fun startingAutoPlayRestoresPlaybackLights() {
        load(square = true)
        vm.scbLed.setChecked(false)
        vm.scbFeedbackLight.setChecked(true)
        vm.autoPlayRunner?.stop()
        vm.autoPlayRunner = mockk<AutoPlayRunner>(relaxed = true)
        vm.switchPlayMode(PlayMode.AutoPlay)
        assertTrue(vm.scbLed.checked)
        assertFalse(vm.scbFeedbackLight.checked)
    }

    @Test fun unplayablePackClosesWithAnExplanation() {
        load(square = true, playable = false)
        var closed = false
        every { vm.uiCallback!!.finishWithNotice(any()) } answers { closed = true }
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!closed && System.nanoTime() < deadline) {
            main.runAll()
            Thread.sleep(5)
        }
        assertTrue("A pack with no decodable audio must close", closed)
        assertFalse(vm.soundLoadingActive)
        verify(exactly = 1) { vm.uiCallback?.finishWithNotice(R.string.pack_no_playable_sounds) }
    }

    private fun controller(): MidiController {
        val activity = PlayActivity()
        PlayActivity::class.java.getDeclaredField("vm").apply { isAccessible = true }.set(activity, vm)
        return PlayActivity::class.java.getDeclaredField("midiController").apply { isAccessible = true }
            .get(activity) as MidiController
    }

    private fun advanceMappingsTwice() {
        repeat(2) {
            vm.unipack.soundPush(0, 0, 0)
            vm.unipack.ledPush(0, 0, 0)
        }
        assertEquals(2, vm.unipack.soundGet(0, 0, 0)?.num)
        assertEquals(2, vm.unipack.ledGet(0, 0, 0)?.num)
    }

    @Test fun attachingControllerKeepsSoundAndLightMappings() {
        load(square = true)
        advanceMappingsTwice()
        controller().onAttach()
        assertEquals("Attaching must keep the third sound next", 2, vm.unipack.soundGet(0, 0, 0)?.num)
        assertEquals("Attaching must keep the third animation next", 2, vm.unipack.ledGet(0, 0, 0)?.num)
    }

    @Test fun controllerModeMessageKeepsSoundAndLightMappings() {
        load(square = true)
        advanceMappingsTwice()
        controller().onUnknownEvent(7, 46, 0, -9)
        assertEquals(2, vm.unipack.soundGet(0, 0, 0)?.num)
        assertEquals(2, vm.unipack.ledGet(0, 0, 0)?.num)
    }

    @Test fun controllerIgnoresChainNumbersOutsideThePack() {
        load(square = true)
        vm.unipack.soundTable!![1][0][0] = ArrayDeque(vm.unipack.soundTable!![0][0][0]!!)
        vm.unipack.ledAnimationTable!![1][0][0] = ArrayDeque(vm.unipack.ledAnimationTable!![0][0][0]!!)
        vm.chain.value = 1
        repeat(2) {
            vm.unipack.soundPush(1, 0, 0)
            vm.unipack.ledPush(1, 0, 0)
        }
        val controller = controller()
        for (chain in listOf(-1, 2, 6)) {
            controller.onChainTouch(chain, true)
            assertEquals("Invalid chain $chain must leave the selected chain alone", 1, vm.chain.value)
            assertEquals("Invalid selection must not reset the sound order", 2, vm.unipack.soundGet(1, 0, 0)?.num)
            assertEquals("Invalid selection must not reset the light order", 2, vm.unipack.ledGet(1, 0, 0)?.num)
        }
    }

    @Test fun selectingTheCurrentChainStillResetsItsMappings() {
        load(square = true)
        advanceMappingsTwice()
        controller().onChainTouch(0, true)
        assertEquals(0, vm.unipack.soundGet(0, 0, 0)?.num)
        assertEquals(0, vm.unipack.ledGet(0, 0, 0)?.num)
    }

    @Test fun releasingAChainButtonDoesNotSelectIt() {
        load(square = true)
        controller().onChainTouch(1, false)
        assertEquals(0, vm.chain.value)
    }

    @Test fun attachingControllerAddsNoChainChangeToRecording() {
        load(square = true)
        vm.uiCallback = mockk(relaxed = true)
        vm.scbRecord.setChecked(true)
        controller().onAttach()
        vm.scbRecord.setChecked(false)
        verify { vm.uiCallback?.copyToClipboard("c 1") }
    }
}
