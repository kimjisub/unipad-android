package com.kimjisub.launchpad.basefeatures

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pads autoplay presses at the same time play their sounds within one main-thread message, so
 * nothing the screen does between messages can pull a chord apart. The spread of each chord's
 * sound requests is logged under [TAG].
 */
@RunWith(AndroidJUnit4::class)
class AutoPlayChordTest {
    private class Play(val nanos: Long, val mainMessage: Int)

    /** Records when each sound is requested and in which main-thread message, -1 off the main thread. */
    private class TimedAudio(
        private val mainMessage: AtomicInteger,
        private val inner: RecordingAudio = RecordingAudio(),
    ) : SoundRunner.Engine by inner {
        val plays = CopyOnWriteArrayList<Play>()
        override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int {
            val onMain = Looper.myLooper() == Looper.getMainLooper()
            plays += Play(SystemClock.elapsedRealtimeNanos(), if (onMain) mainMessage.get() else -1)
            return inner.play(soundId, volumeL, volumeR, loop)
        }
    }

    private val chords = 12
    private val pads = 8
    private val mainMessage = AtomicInteger()
    private lateinit var screen: FeatureScreen
    private lateinit var audio: TimedAudio
    private lateinit var doubles: Module

    @Before fun setUp() {
        screen = FeatureScreen()
        screen.assertDedicatedWorkspace()
        screen.cleanPacks()
        screen.install()
        audio = TimedAudio(mainMessage)
        doubles = module { single<SoundRunner.Engine> { audio } }
        loadKoinModules(doubles)
    }

    @After fun tearDown() {
        try {
            screen.close()
        } finally {
            unloadKoinModules(doubles)
        }
    }

    @Test fun chordsPlayInOneMainMessageWhileMainIsIdle() = playChords(busyFrameMs = 0)

    @Test fun chordsPlayInOneMainMessageWhileFramesKeepMainBusy() = playChords(busyFrameMs = 8)

    private fun playChords(busyFrameMs: Long) {
        File(screen.pack(), "autoPlay").writeText(buildString {
            repeat(chords) {
                for (y in 1..pads) append("on 1 $y\n")
                append("d 250\n")
                for (y in 1..pads) append("off 1 $y\n")
                append("d 250\n")
            }
        })
        screen.openPlay()
        val vm = screen.vm()
        val busy = AtomicBoolean(busyFrameMs > 0)
        val load = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (!busy.get()) return
                SystemClock.sleep(busyFrameMs)
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
        screen.onMain {
            Looper.getMainLooper().setMessageLogging { if (it.startsWith(">>>>>")) mainMessage.incrementAndGet() }
            if (busyFrameMs > 0) Choreographer.getInstance().postFrameCallback(load)
            vm.switchPlayMode(PlayMode.AutoPlay)
        }
        try {
            screen.await("Autoplay did not press every chord") { audio.plays.size >= chords * pads }
        } finally {
            busy.set(false)
            screen.onMain {
                vm.switchPlayMode(PlayMode.None)
                Looper.getMainLooper().setMessageLogging(null)
            }
        }
        val chordPlays = audio.plays.take(chords * pads).chunked(pads)
        val spreadsMs = chordPlays.map { (it.last().nanos - it.first().nanos) / 1_000_000.0 }
        Log.i(TAG, "busyFrameMs=$busyFrameMs spreadMs=${spreadsMs.joinToString(",") { "%.2f".format(it) }}")
        val split = chordPlays.withIndex().filter { (_, plays) ->
            plays.any { it.mainMessage < 0 } || plays.map { it.mainMessage }.distinct().size > 1
        }.map { it.index + 1 }
        assertEquals("Chords whose presses were not played in one main-thread message", emptyList<Int>(), split)
    }

    private companion object {
        const val TAG = "AutoPlayChord"
    }
}
