package com.kimjisub.launchpad.basefeatures

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Deliberately refuses output startup; it does not simulate or establish a real phone-call failure. */
@RunWith(AndroidJUnit4::class)
class AudioOutputFailureTest {
    private lateinit var screen: FeatureScreen
    private lateinit var doubles: Module
    private val startGate = CountDownLatch(1)

    @Before fun setUp() {
        screen = FeatureScreen()
        screen.assertDedicatedWorkspace()
        screen.cleanPacks()
        screen.install()
        val engine = object : SoundRunner.Engine by RecordingAudio() {
            override fun start(): Boolean {
                check(startGate.await(10, TimeUnit.SECONDS)) { "Output failure was not released" }
                return false
            }
        }
        doubles = module { single<SoundRunner.Engine> { engine } }
        loadKoinModules(doubles)
    }

    @After fun tearDown() {
        startGate.countDown()
        try {
            screen.close()
        } finally {
            unloadKoinModules(doubles)
        }
    }

    @Test fun outputStartupFailureRequestsItsOwnNoticeAndClosesPlayback() {
        screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
        val activity = screen.resumed()
        val notices = CopyOnWriteArrayList<Int>()
        screen.onMain {
            val vm = screen.vm()
            val original = checkNotNull(vm.uiCallback)
            vm.uiCallback = object : PlayActivityViewModel.UiCallback by original {
                override fun showToast(resId: Int) {
                    notices += resId
                    original.showToast(resId)
                }
            }
        }
        startGate.countDown()
        screen.await("Output failure notice missing") { notices.isNotEmpty() }
        val message = screen.text(notices.single())
        // The Snackbar belongs to the activity that immediately finishes. Check its requested
        // wording at the actual UI boundary, while leaving the finish call unchanged.
        screen.capture("audio-output-failure-closed")
        assertTrue("Playback must still close after output startup fails", screen.onMain { activity.isFinishing || activity.isDestroyed })
        assertNotEquals("Output failure must not blame device performance", screen.text(R.string.outOfCPU), message)
    }
}
