package com.kimjisub.launchpad.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.unipack.UniPackFolder
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelGuideResumeTest {
    @get:Rule val tmp = TemporaryFolder()
    private val main = FakeMainDispatcher()
    private val clock = AtomicLong()
    private lateinit var vm: PlayActivityViewModel
    private val hardware = mutableMapOf<Pair<Int, Int>, Int>()
    private val animationTargets = mutableMapOf<Pair<Int, Int>, Long>()

    @Before fun setUp() {
        Dispatchers.setMain(main)
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { clock.get() }
        val folder = tmp.newFolder()
        File(folder, "info").writeText("title=Guide\nproducerName=Test\nbuttonX=8\nbuttonY=8\nchain=2\nsquareButton=true\n")
        File(folder, "sounds").mkdir()
        File(folder, "keySound").writeText("")
        File(folder, "autoPlay").writeText("delay 500\ntouch 1 1\ndelay 60000\n")
        vm = PlayActivityViewModel(mockk<UnipackRepository>(relaxed = true), UsageAnalytics { _, _ -> })
        vm.unipack = UniPackFolder(folder).load().loadDetail()
        vm.initState()
        vm.initAutoPlayRunner()
        vm.uiCallback = mockk<PlayActivityViewModel.UiCallback>(relaxed = true).also { ui ->
            every { ui.sendGuideLedToLaunchpad(any(), any(), any()) } answers {
                hardware[firstArg<Int>() to secondArg<Int>()] = thirdArg()
            }
            every { ui.startGuideAnimation(any(), any(), any()) } answers {
                animationTargets[firstArg<Int>() to secondArg<Int>()] = thirdArg()
            }
            every { ui.stopGuideAnimation(any(), any()) } answers {
                animationTargets.remove(firstArg<Int>() to secondArg<Int>())
            }
        }
    }

    @After fun tearDown() {
        if (::vm.isInitialized) ViewModelStore().apply { put("play", vm); clear() }
        main.runAll()
        unmockkStatic(SystemClock::class)
        Dispatchers.resetMain()
    }

    private fun await(check: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!check() && System.nanoTime() < deadline) {
            main.runAll()
            Thread.sleep(1)
        }
        main.runAll()
        assertTrue("Guide state: targets=$animationTargets hardware=$hardware progress=${vm.autoPlayRunner?.progress}", check())
    }

    @Test fun resumingPracticeKeepsScreenAndControllerGuidesTogether() {
        vm.switchPlayMode(PlayMode.GuidePlay)
        await { animationTargets[0 to 0] == 500L }
        clock.set(100)
        vm.autoPlayPause()
        assertFalse("Pausing continuous practice must not scan a new step", vm.autoPlayRunner!!.stepMode)
        clock.set(3100)
        vm.autoPlayResume()
        clock.set(3150)
        await { animationTargets[0 to 0] == 3500L && (hardware[0 to 0] ?: 0) > 0 }
        assertEquals(Channel.GUIDE, vm.channelManager.get(0, 0)?.channel)
        assertEquals("The screen animation uses the same postponed target as the controller", 3500L, animationTargets[0 to 0])
        clock.set(3500)
        await { hardware[0 to 0] == 0 }
        assertTrue(vm.channelManager.get(0, 0)?.channel != Channel.GUIDE)
    }

    @Test fun pausingStepPracticeKeepsThePendingStep() {
        vm.switchPlayMode(PlayMode.StepPractice)
        await { animationTargets[0 to 0] == 0L && hardware[0 to 0] == 21 }
        vm.autoPlayPause()
        assertTrue(vm.autoPlayRunner!!.stepMode)
        assertEquals(Channel.GUIDE, vm.channelManager.get(0, 0)?.channel)
        assertEquals(0L, animationTargets[0 to 0])
    }
}
