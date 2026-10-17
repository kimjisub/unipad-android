package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import androidx.test.core.app.ActivityScenario
import com.kimjisub.design.view.ChainView
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module

abstract class PlaybackScreenTest {
    protected lateinit var screen: FeatureScreen
    protected lateinit var audio: RecordingAudio
    private lateinit var doubles: Module
    @Before fun setUpPlayback() {
        screen = FeatureScreen()
        screen.assertDedicatedWorkspace()
        screen.cleanPacks()
        screen.install()
        audio = RecordingAudio()
        doubles = module { single<SoundRunner.Engine> { audio } }
        loadKoinModules(doubles)
    }
    @After fun tearDownPlayback() {
        try {
            if (this::screen.isInitialized) screen.close()
        } finally {
            if (this::doubles.isInitialized) unloadKoinModules(doubles)
        }
    }

    /**
     * Opens the pack in the given pad input mode with the press light on. A pack with keyLED starts
     * with it off; the option is set on the screen's state rather than through the options panel,
     * whose open/close by Back is not what these tests check. The caller restores [prefs]' mode.
     */
    protected fun openPlayWithPressLight(prefs: PreferenceManager, slide: Boolean): ActivityScenario<PlayActivity> {
        prefs.slideMode = slide
        val scenario = screen.openPlay()
        screen.onMain { screen.vm().scbFeedbackLight.setChecked(true) }
        assertTrue("Press light option did not turn on", screen.onMain { screen.vm().scbFeedbackLight.isChecked() })
        val applied = screen.onMain { (screen.resumed() as PlayActivity).padTouch.slide }
        assertEquals("Slide Mode applied on the play screen must match the mode under test", slide, applied)
        return scenario
    }

    /** Centre of the chain button that selects [chain], in screen coordinates. */
    protected fun chainButton(chain: Int): Point {
        val chains = screen.onMain {
            screen.views(screen.resumed().window.decorView).filterIsInstance<ChainView>().filter { it.width > 0 }.map(screen::bounds)
        }
        assertTrue("Chain button ${chain + 1} must be on screen", chain < chains.size)
        return Point(chains[chain].centerX(), chains[chain].centerY())
    }
}
