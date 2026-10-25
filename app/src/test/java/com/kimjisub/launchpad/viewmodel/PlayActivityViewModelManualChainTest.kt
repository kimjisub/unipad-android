package com.kimjisub.launchpad.viewmodel

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.RecordingSoundEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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

/** Manual choices supersede pending pad moves; scripted chain changes keep their own timing. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelManualChainTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scheduler = TestCoroutineScheduler()
    private val engine = RecordingSoundEngine()
    private lateinit var vm: PlayActivityViewModel

    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
        mockkObject(MidiConnection)
        every { MidiConnection.driver } returns mockk(relaxed = true)
        val root = tmp.newFolder()
        File(root, "info").writeText("title=Test\nproducerName=Test\nbuttonX=2\nbuttonY=2\nchain=3\nsquareButton=true\n")
        File(root, "sounds").mkdir()
        File(root, "sounds/a.wav").writeBytes(byteArrayOf(1))
        File(root, "keySound").writeText("1 1 1 a.wav 1 2\n1 1 2 a.wav 1 1\n")
        vm = PlayActivityViewModel(mockk<UnipackRepository>(relaxed = true), UsageAnalytics { _, _ -> }, engine)
        vm.uiCallback = mockk(relaxed = true)
        vm.unipack = UniPackFolder(root).load().loadDetail()
        vm.initState()
        vm.initPlayback()
        val deadline = System.nanoTime() + 5_000_000_000L
        while (true) {
            scheduler.runCurrent()
            if (!vm.soundLoadingActive && (vm.unipack.soundGet(0, 0, 0)?.id ?: -1) >= 0) break
            assertTrue("Sound loading did not complete", System.nanoTime() < deadline)
            Thread.sleep(1)
        }
        vm.screenVisible = true
    }

    @After fun tearDown() {
        clearPlayViewModel(if (::vm.isInitialized) vm else null, drain = { scheduler.runCurrent() }) {
            scheduler.runCurrent()
            unmockkObject(MidiConnection)
            Dispatchers.resetMain()
            ArchTaskExecutor.getInstance().setDelegate(null)
        }
    }

    private fun press() {
        vm.padTouch(0, 0, true)
        scheduler.runCurrent()
        assertEquals(1, engine.plays.size)
        scheduler.advanceTimeBy(50)
    }

    private fun settle() {
        scheduler.advanceTimeBy(150)
        scheduler.runCurrent()
    }

    private fun controller(): MidiController {
        val activity = PlayActivity()
        PlayActivity::class.java.getDeclaredField("vm").apply { isAccessible = true }.set(activity, vm)
        return PlayActivity::class.java.getDeclaredField("midiController").apply { isAccessible = true }
            .get(activity) as MidiController
    }

    @Test fun screenChainSelectionCancelsThePendingPadMove() {
        press()
        vm.selectChain(2)
        settle()
        assertEquals("The screen's later manual choice must remain selected", 2, vm.chain.value)
    }

    @Test fun controllerChainSelectionCancelsThePendingPadMove() {
        press()
        controller().onChainTouch(2, true)
        settle()
        assertEquals("The controller's later manual choice must remain selected", 2, vm.chain.value)
    }

    @Test fun closingThePlayScreenCancelsThePendingPadMove() {
        press()
        vm.screenVisible = false
        settle()
        assertEquals("A hidden play screen must not move its chain later", 0, vm.chain.value)
    }

    @Test fun destroyingTheSoundRunnerCancelsThePendingPadMove() {
        press()
        vm.soundRunner!!.destroy()
        settle()
        assertEquals(0, vm.chain.value)
    }

    @Test fun selectingTheCurrentChainAlsoCancelsThePendingPadMove() {
        press()
        vm.selectChain(0)
        settle()
        assertEquals(0, vm.chain.value)
    }

    @Test fun aManualChoiceCancelsEveryPendingPadMove() {
        vm.padTouch(0, 1, true)
        scheduler.runCurrent()
        vm.padTouch(0, 0, true)
        scheduler.runCurrent()
        assertEquals("Both pads must have started a sound and scheduled a move", 2, engine.plays.size)
        scheduler.advanceTimeBy(50)
        vm.selectChain(2)
        settle()
        assertEquals(2, vm.chain.value)
    }

    @Test fun anUnchangedPressStillMovesAfterOneHundredMilliseconds() {
        press()
        scheduler.advanceTimeBy(49)
        scheduler.runCurrent()
        assertEquals(0, vm.chain.value)
        scheduler.advanceTimeBy(1)
        scheduler.runCurrent()
        assertEquals(1, vm.chain.value)
    }

    @Test fun scriptedChainChangesDoNotCancelPendingPadMoves() {
        press()
        vm.ledRunnerListener.onChainChange(2)
        settle()
        assertEquals("Only manual selection cancels a pending pad move", 1, vm.chain.value)
    }

    @Test fun anAbsentChainOrButtonReleaseDoesNotCancelThePendingPadMove() {
        press()
        vm.selectChain(3)
        controller().onChainTouch(2, false)
        settle()
        assertEquals(1, vm.chain.value)
    }
}
