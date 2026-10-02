package com.kimjisub.launchpad.basefeatures

import com.kimjisub.launchpad.unipack.runner.SoundRunner
import org.junit.After
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
}
