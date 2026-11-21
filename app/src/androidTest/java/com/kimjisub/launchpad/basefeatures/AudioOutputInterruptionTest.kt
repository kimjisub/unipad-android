package com.kimjisub.launchpad.basefeatures

import android.content.BroadcastReceiver
import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** In-process system-event simulation with a recording engine, not physical routing or listening. */
@RunWith(AndroidJUnit4::class)
class AudioOutputInterruptionTest : PlaybackScreenTest() {
    // The system protects this broadcast from shell/apps. Deliver to the activity's actual receiver
    // on main instead. An older activity with no receiver receives nothing, preserving the baseline.
    private fun receiver(activity: PlayActivity): BroadcastReceiver? = screen.onMain {
        activity.javaClass.declaredFields.firstOrNull { it.name == "audioOutputReceiver" }
            ?.apply { isAccessible = true }?.get(activity) as? BroadcastReceiver
    }

    private fun deliver(receiver: BroadcastReceiver?, action: String) = screen.onMain {
        receiver?.onReceive(screen.context, Intent(action))
    }

    private fun prepare() {
        File(screen.pack(), "keySound").writeText("1 1 1 silence.wav 1\n1 1 2 silence.wav 3\n1 1 3 silence.wav 0\n")
        File(screen.pack(), "autoPlay").writeText("on 1 1\non 1 2\non 1 3\ndelay 5000\non 1 1\ndelay 60000\n")
    }

    @Test fun speakerFallbackStopsEveryVoiceAndWaitsForManualResume() = checkAutoPlay(reconnect = true)

    @Test fun speakerFallbackResumesOnSpeakerWithoutReconnection() = checkAutoPlay(reconnect = false)

    private fun checkAutoPlay(reconnect: Boolean) {
        prepare()
        screen.openPlay()
        val activity = screen.resumed() as PlayActivity
        val vm = screen.vm()
        val receiver = receiver(activity)
        screen.onMain { vm.switchPlayMode(PlayMode.AutoPlay) }
        screen.await("Three automatic sound types did not start") { audio.plays.size == 3 }
        screen.capture("audio-output-before-disconnect-$reconnect")
        assertEquals(listOf(0, 2, -1), audio.loops.toList())
        val silencesBefore = audio.silences.get()
        deliver(receiver, AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        try {
            screen.onMain {
                assertFalse("Speaker fallback must suspend autoplay", vm.isAutoPlayPlaying)
                assertTrue(vm.autoPlayRunner!!.suspended)
            }
            val progress = screen.onMain { vm.autoPlayRunner!!.progress }
            val plays = audio.plays.size
            assertTrue("All voices must receive stopAll", audio.silences.get() > silencesBefore)
            assertTrue(audio.ringingLoops().isEmpty())
            deliver(receiver, AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            // Reconnection and unrelated output/device events never restart the interrupted sequence.
            if (reconnect) deliver(receiver, AudioManager.ACTION_HEADSET_PLUG)
            screen.onMain { repeat(2) { vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN) } }
            SystemClock.sleep(5500)
            assertEquals("No new automatic commands after interruption", plays, audio.plays.size)
            screen.onMain { assertEquals(progress, vm.autoPlayRunner!!.progress) }
            screen.node(androidx.test.uiautomator.By.desc(screen.text(R.string.cd_autoplay_play)))
            screen.capture("audio-output-paused-reconnect-$reconnect")
            screen.clickDescription(R.string.cd_autoplay_play)
            screen.await("Manual Play must resume without headphones") { screen.onMain { vm.isAutoPlayPlaying } }
            screen.node(androidx.test.uiautomator.By.desc(screen.text(R.string.cd_autoplay_pause)))
            screen.await("The next pending automatic command did not play") { audio.plays.size > plays }
            assertEquals("Resume must not replay the first three commands", plays + 1, audio.plays.size)
            screen.capture("audio-output-manually-resumed-on-speaker-$reconnect")
        } finally {
            screen.capture("audio-output-after-disconnect-$reconnect")
        }
    }

    @Test fun handPlayedVoicesStopAndFreshInputWorksWithoutPlay() {
        prepare()
        File(screen.pack(), "autoPlay").delete()
        screen.openPlay()
        val vm = screen.vm()
        val receiver = receiver(screen.resumed() as PlayActivity)
        screen.onMain { repeat(3) { vm.padTouch(0, it, true, it.toLong()) } }
        assertEquals(listOf(0, 2, -1), audio.loops.toList())
        val silencesBefore = audio.silences.get()
        deliver(receiver, AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        assertTrue(audio.silences.get() > silencesBefore)
        assertTrue(audio.ringingLoops().isEmpty())
        deliver(receiver, AudioManager.ACTION_HEADSET_PLUG)
        screen.onMain { vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN) }
        assertEquals(3, audio.plays.size)
        screen.onMain { repeat(3) { vm.padTouch(0, it, false, it.toLong()) } }
        assertEquals(3, audio.plays.size)
        pressPad(2)
        screen.onMain { assertEquals(PlayMode.None, vm.playMode) }
        screen.capture("audio-output-fresh-hand-input")
    }

    @Test fun overlappingFocusLossStillRequiresUserAndOutputPermission() {
        prepare()
        screen.openPlay()
        val vm = screen.vm()
        val receiver = receiver(screen.resumed() as PlayActivity)
        val requestFocus = screen.onMain { vm.requestAudioFocus }
        screen.onMain { vm.switchPlayMode(PlayMode.AutoPlay) }
        screen.await("Autoplay did not start") { audio.plays.size == 3 }
        screen.onMain { vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) }
        deliver(receiver, AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        screen.onMain { vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN) }
        val progress = screen.onMain { vm.autoPlayRunner!!.progress }
        screen.onMain {
            assertFalse(vm.isAutoPlayPlaying)
            vm.requestAudioFocus = { false }
        }
        try {
            screen.clickDescription(R.string.cd_autoplay_play)
            screen.onMain {
                assertFalse("Manual Play must still respect denied audio focus", vm.isAutoPlayPlaying)
                assertEquals(progress, vm.autoPlayRunner!!.progress)
                vm.padTouch(0, 2, true)
                vm.padTouch(0, 2, false)
            }
            assertEquals("Denied output must not issue new sounds", 3, audio.plays.size)
            screen.capture("audio-output-permission-denied")
        } finally {
            screen.onMain { vm.requestAudioFocus = requestFocus }
        }
    }

    @Test fun unrelatedEventsAndLateDeliveryOutsideScreenDoNothing() {
        prepare()
        val scenario = screen.openPlay()
        val vm = screen.vm()
        val receiver = receiver(screen.resumed() as PlayActivity)
        screen.onMain { vm.switchPlayMode(PlayMode.AutoPlay) }
        screen.await("Autoplay did not start") { audio.plays.size == 3 }
        val silences = audio.silences.get()
        for (action in listOf(AudioManager.ACTION_HEADSET_PLUG, "android.hardware.usb.action.USB_DEVICE_DETACHED")) {
            deliver(receiver, action)
        }
        screen.onMain { assertTrue(vm.isAutoPlayPlaying) }
        assertEquals(silences, audio.silences.get())
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        val afterStop = audio.silences.get()
        deliver(receiver, AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        assertEquals("A queued notification after onStop must be ignored", afterStop, audio.silences.get())
        scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        screen.await("Play did not return") { screen.ready() }
        deliver(receiver, AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        screen.onMain { assertFalse(vm.isAutoPlayPlaying) }
        screen.capture("audio-output-already-paused")
    }

    @Test fun failedOutputRecoveryCannotReviveInterruptedVoices() {
        val recovering = CountDownLatch(1)
        val finishRecovery = CountDownLatch(1)
        val recoveryFailed = CountDownLatch(1)
        val available = AtomicBoolean(true)
        val starts = AtomicInteger()
        val rejected = AtomicInteger()
        val engine = object : SoundRunner.Engine by audio {
            override fun start(): Boolean {
                if (starts.incrementAndGet() == 1) return audio.start()
                recovering.countDown()
                check(finishRecovery.await(10, TimeUnit.SECONDS)) { "Recovery gate was not released" }
                recoveryFailed.countDown()
                return false
            }
            override fun stop() {
                available.set(false)
                audio.stop()
            }
            override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int =
                if (available.get()) audio.play(soundId, volumeL, volumeR, loop)
                else { rejected.incrementAndGet(); 0 }
        }
        val binding = module { single<SoundRunner.Engine> { engine } }
        loadKoinModules(binding)
        try {
            prepare()
            screen.openPlay()
            val vm = screen.vm()
            val receiver = receiver(screen.resumed() as PlayActivity)
            screen.onMain { vm.switchPlayMode(PlayMode.AutoPlay) }
            screen.await("Autoplay did not start") { audio.plays.size == 3 }
            screen.onMain { vm.soundRunner!!.pauseStream() }
            screen.await("Output did not close") { audio.stops.get() > 0 }
            screen.onMain { vm.soundRunner!!.resumeStream() }
            assertTrue("Output recovery did not start", recovering.await(10, TimeUnit.SECONDS))
            deliver(receiver, AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            finishRecovery.countDown()
            assertTrue("Output recovery did not fail", recoveryFailed.await(10, TimeUnit.SECONDS))
            deliver(receiver, AudioManager.ACTION_HEADSET_PLUG)
            screen.onMain { vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN) }
            SystemClock.sleep(5500)
            screen.onMain {
                assertFalse(vm.isAutoPlayPlaying)
                assertTrue(vm.autoPlayRunner!!.suspended)
                vm.padTouch(0, 2, true)
                vm.padTouch(0, 2, false)
            }
            assertEquals("A fresh input reaches the existing failed-output path", 1, rejected.get())
            assertEquals("A request on failed output is not a successful voice", 3, audio.plays.size)
            assertTrue(audio.ringingLoops().isEmpty())
            screen.capture("audio-output-recovery-failed")
        } finally {
            finishRecovery.countDown()
            try { screen.close() } finally { unloadKoinModules(binding) }
        }
    }
}
