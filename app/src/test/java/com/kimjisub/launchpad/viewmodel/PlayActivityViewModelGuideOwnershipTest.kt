package com.kimjisub.launchpad.viewmodel

import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.AutoPlayRunner
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Delivers callbacks from a replaced runner after a new practice has lit the same pad. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelGuideOwnershipTest {
    @get:Rule val tmp = TemporaryFolder()
    private val main = FakeMainDispatcher()
    private lateinit var vm: PlayActivityViewModel
    private lateinit var oldListener: AutoPlayRunner.Listener
    private lateinit var newListener: AutoPlayRunner.Listener
    private val lights = mutableListOf<Int>()

    @Before fun setUp() {
        Dispatchers.setMain(main)
        val folder = tmp.newFolder()
        File(folder, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=2\nsquareButton=true\n")
        File(folder, "sounds").mkdir()
        File(folder, "keySound").writeText("")
        File(folder, "autoPlay").writeText("touch 1 1\ndelay 60000\n")
        vm = PlayActivityViewModel(mockk<UnipackRepository>(relaxed = true), UsageAnalytics { _, _ -> })
        vm.unipack = UniPackFolder(folder).load().loadDetail()
        assertEquals("The fixture must load its real pad grid", 8, vm.unipack.buttonX)
        vm.uiCallback = mockk<PlayActivityViewModel.UiCallback>(relaxed = true).also { ui ->
            every { ui.sendGuideLedToLaunchpad(any(), any(), any()) } answers { lights += thirdArg<Int>() }
        }
        vm.initState()
        vm.initAutoPlayRunner()
        oldListener = listener(checkNotNull(vm.autoPlayRunner))
        vm.initAutoPlayRunner()
        newListener = listener(checkNotNull(vm.autoPlayRunner))
        main.runAll()
        newListener.onGuidePadOn(0, 0, 60000)
        newListener.onGuideLedUpdate(0, 0, 21)
        main.runAll()
        assertEquals(Channel.GUIDE, vm.channelManager.get(0, 0)?.channel)
        assertEquals(listOf(21), lights)
    }

    private fun listener(runner: AutoPlayRunner) = AutoPlayRunner::class.java.getDeclaredField("listener")
        .apply { isAccessible = true }.get(runner) as AutoPlayRunner.Listener

    @After fun tearDown() {
        clearPlayViewModel(if (::vm.isInitialized) vm else null, drain = { main.runAll() }) {
            main.runAll()
            Dispatchers.resetMain()
        }
    }

    @Test fun oldPadOnDoesNotLeaveAHintAfterReplacement() {
        oldListener.onGuidePadOn(1, 1, 60000)
        oldListener.onGuidePadOff(1, 1)
        main.runAll()
        assertNull(vm.channelManager.get(1, 1))
    }

    @Test fun oldChainOnDoesNotLeaveAHintAfterReplacement() {
        oldListener.onGuideChainOn(1)
        oldListener.onRemoveGuide()
        main.runAll()
        assertNull(vm.channelManager.get(-1, PlayActivityViewModel.CHAIN_INDEX_OFFSET + 1))
    }

    @Test fun oldPadReleaseDoesNotRemoveTheNewPracticeHint() {
        oldListener.onGuidePadOff(0, 0)
        main.runAll()
        assertEquals(Channel.GUIDE, vm.channelManager.get(0, 0)?.channel)
    }

    @Test fun oldCleanupDoesNotRemoveTheNewPracticeHint() {
        oldListener.onRemoveGuide()
        main.runAll()
        assertEquals(Channel.GUIDE, vm.channelManager.get(0, 0)?.channel)
    }

    @Test fun oldLightOffDoesNotTurnOffTheNewPracticeLight() {
        oldListener.onGuideLedUpdate(0, 0, 0)
        main.runAll()
        assertEquals(listOf(21), lights)
    }
}
