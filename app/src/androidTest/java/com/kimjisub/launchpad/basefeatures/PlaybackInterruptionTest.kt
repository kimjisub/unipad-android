package com.kimjisub.launchpad.basefeatures

import android.content.Intent
import android.media.AudioManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.MidiSelectActivity
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Screen/lifecycle checks; audio requests are recorded, not claimed as physical speaker output. */
@RunWith(AndroidJUnit4::class)
class PlaybackInterruptionTest : PlaybackScreenTest() {
    @Test fun homeKeepsGuideModeAndWaitsForManualPlay() {
        File(screen.pack(), "autoPlay").writeText("delay 5000\ntouch 1 1\ndelay 60000\n")
        screen.openPlay()
        val activity = screen.resumed()
        val vm = screen.vm()
        screen.onMain { vm.switchPlayMode(PlayMode.GuidePlay) }
        screen.capture("interruption-guide-before-home")
        for (away in listOf(1000L, 10000L)) {
            screen.goHome { screen.onMain { !vm.screenVisible } }
            val progress = screen.onMain { vm.autoPlayRunner!!.progress }
            SystemClock.sleep(away)
            screen.bringToFront(activity)
            screen.awaitActivity(activity.javaClass)
            screen.node(androidx.test.uiautomator.By.desc(screen.text(R.string.cd_autoplay_play)))
            screen.capture("interruption-guide-return-$away")
            screen.onMain {
                assertEquals("Keep the chosen guide mode", PlayMode.GuidePlay, vm.playMode)
                assertFalse("Coming back must wait for Play", vm.isAutoPlayPlaying)
                assertEquals(progress, vm.autoPlayRunner!!.progress)
            }
        }
        screen.clickDescription(R.string.cd_autoplay_play)
        screen.await("Manual Play did not resume guide play") { screen.onMain { vm.isAutoPlayPlaying } }
        screen.node(androidx.test.uiautomator.By.desc(screen.text(R.string.cd_autoplay_pause)))
        screen.capture("interruption-guide-manually-resumed")
    }

    @Test fun lockKeepsStepPracticeSuspendedUntilManualPlay() {
        File(screen.pack(), "autoPlay").writeText("on 1 1\ndelay 1000\non 1 2\ndelay 60000\n")
        screen.openPlay()
        val vm = screen.vm()
        screen.onMain { vm.switchPlayMode(PlayMode.StepPractice) }
        screen.await("Step guide did not start") { screen.onMain { vm.autoPlayRunner!!.progress > 0 } }
        for (away in listOf(1000L, 10000L)) {
            screen.device.sleep()
            screen.await("Lock did not hide play") { screen.onMain { !vm.screenVisible } }
            val progress = screen.onMain { vm.autoPlayRunner!!.progress }
            SystemClock.sleep(away)
            screen.device.wakeUp()
            screen.device.executeShellCommand("wm dismiss-keyguard")
            screen.await("Unlock did not return to play") { screen.ready() }
            screen.node(androidx.test.uiautomator.By.desc(screen.text(R.string.cd_autoplay_play)))
            screen.capture("interruption-step-unlocked-$away")
            screen.onMain {
                assertEquals(PlayMode.StepPractice, vm.playMode)
                assertTrue(vm.autoPlayRunner!!.suspended)
                assertEquals(progress, vm.autoPlayRunner!!.progress)
                assertNull(vm.channelManager.get(0, 0))
            }
        }
        screen.clickDescription(R.string.cd_autoplay_play)
        screen.await("Manual Play did not restore the pending step") {
            screen.onMain { !vm.autoPlayRunner!!.suspended && vm.playMode == PlayMode.StepPractice }
        }
        screen.capture("interruption-step-manually-resumed")
    }

    @Test fun menuKeepsPlayingButDeviceSelectionRequiresManualResume() {
        File(screen.pack(), "autoPlay").writeText("on 1 1\ndelay 60000\n")
        screen.openPlay()
        val activity = screen.resumed()
        val vm = screen.vm()
        screen.onMain { vm.switchPlayMode(PlayMode.AutoPlay) }
        screen.options()
        screen.onMain { assertTrue("The in-screen menu must not interrupt", vm.isAutoPlayPlaying) }
        screen.device.pressBack()
        screen.await("The menu did not close") { screen.onMain { !vm.isOptionWindowVisible } }
        screen.onMain { activity.startActivity(Intent(activity, MidiSelectActivity::class.java)) }
        screen.awaitActivity(MidiSelectActivity::class.java)
        screen.capture("interruption-device-selection")
        SystemClock.sleep(1000)
        screen.device.pressBack()
        screen.awaitActivity(activity.javaClass)
        screen.node(androidx.test.uiautomator.By.desc(screen.text(R.string.cd_autoplay_play)))
        screen.capture("interruption-device-return")
        screen.onMain {
            assertFalse(vm.isAutoPlayPlaying)
            assertTrue(vm.autoPlayRunner!!.suspended)
            assertEquals(PlayMode.AutoPlay, vm.playMode)
        }
    }

    @Test fun notificationShadeInterruptsWithoutLeavingTheActivity() {
        File(screen.pack(), "autoPlay").writeText("on 1 1\ndelay 60000\n")
        screen.openPlay()
        val vm = screen.vm()
        screen.onMain { vm.switchPlayMode(PlayMode.AutoPlay) }
        assertTrue(screen.device.openNotification())
        screen.await("Covering the play window did not suspend playback") { screen.onMain { !vm.isAutoPlayPlaying } }
        SystemClock.sleep(1000)
        screen.device.pressBack()
        screen.await("Play did not return") { screen.ready() }
        screen.capture("interruption-after-notification-shade")
        screen.onMain {
            assertFalse(vm.isAutoPlayPlaying)
            assertTrue(vm.autoPlayRunner!!.suspended)
        }
    }

    @Test fun transientAudioLossWaitsEvenAfterRepeatedPermissionNotifications() {
        File(screen.pack(), "keySound").writeText("1 1 1 silence.wav 0\n")
        File(screen.pack(), "autoPlay").writeText("on 1 1\ndelay 60000\n")
        screen.openPlay()
        val vm = screen.vm()
        screen.onMain { vm.switchPlayMode(PlayMode.AutoPlay) }
        screen.await("Autoplay did not start") { audio.ringingLoops().isNotEmpty() }
        screen.onMain { vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) }
        screen.onMain { repeat(2) { vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN) } }
        screen.node(androidx.test.uiautomator.By.desc(screen.text(R.string.cd_autoplay_play)))
        screen.capture("interruption-focus-return")
        screen.onMain { assertFalse("Permission must never resume playback", vm.isAutoPlayPlaying) }
        assertTrue(audio.ringingLoops().isEmpty())
    }

    @Test fun turningLightsOffKeepsSoundAndAutoplayTimeRunning() {
        File(screen.pack(), "keySound").writeText("1 1 1 silence.wav 0\n1 1 2 silence.wav 0\n")
        File(screen.pack(), "autoPlay").writeText("on 1 1\ndelay 2000\non 1 2\ndelay 60000\n")
        screen.openPlay()
        val vm = screen.vm()
        screen.onMain { vm.switchPlayMode(PlayMode.AutoPlay) }
        screen.await("The first automatic pad did not sound") { audio.plays.size == 1 }
        screen.onMain { vm.scbLed.setChecked(false) }
        screen.await("Turning lights off paused the automatic sequence") { audio.plays.size == 2 }
        assertEquals("Both held sounds keep playing", 2, audio.ringingLoops().size)
        screen.onMain {
            assertTrue(vm.isAutoPlayPlaying)
            assertTrue(vm.autoPlayRunner!!.playmode)
            assertFalse(vm.ledRunner!!.active)
            assertNull(vm.channelManager.get(0, 1))
        }
        screen.capture("lights-off-with-autoplay-continuing")
    }

    @Test fun disablingLightsDiscardsFiniteAnimations() {
        File(screen.pack(), "keyLED/1 1 1 1").writeText("o 1 1 a 5\nd 3000\no 1 2 a 7\nd 3000\nf 1 1\nf 1 2\n")
        screen.openPlay()
        val vm = screen.vm()
        pressPad(0)
        screen.await("Animation did not start") { screen.onMain { vm.ledRunner!!.isEventExist(0, 0) } }
        screen.capture("lights-finite-before-off")
        screen.onMain { vm.scbLed.setChecked(false) }
        SystemClock.sleep(1000)
        screen.onMain { vm.scbLed.setChecked(true) }
        screen.capture("lights-finite-after-on")
        screen.onMain { assertFalse("The old animation must be discarded", vm.ledRunner!!.isEventExist(0, 0)) }
        pressPad(0)
        screen.await("New input did not start fresh lights") { screen.onMain { vm.ledRunner!!.isEventExist(0, 0) } }
        screen.capture("lights-finite-new-input")
    }
}
