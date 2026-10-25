package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import android.os.SystemClock
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real pointer dispatch and auto-play runner; decoded requests and screen lights, without a speaker. */
class PadInputOwnershipTest : PlaybackScreenTest() {
    private fun open(sequence: String = "on 1 1\nd 60000\noff 1 1\n") {
        val pack = screen.pack()
        File(pack, "keySound").writeText("1 1 1 silence.wav 0\n1 1 2 silence.wav\n")
        File(pack, "keyLED").listFiles().orEmpty().forEach { it.delete() }
        File(pack, "keyLED/1 1 1 0").writeText("on 1 1 a 4\nd 100000\n")
        File(pack, "autoPlay").writeText(sequence)
        screen.openPlay()
    }

    private fun pad() = screen.padBounds()[0].let { Point(it.centerX(), it.centerY()) }

    private fun observe(name: String): Pair<Int, Int?> {
        // Let pending LED delivery reach main before taking the observation, as during play.
        SystemClock.sleep(100)
        val loops = audio.ringingLoops().size
        val (pressed, light) = screen.onMain {
            val channels = screen.vm().channelManager
            val pressed = channels.get(0, 0)?.channel == Channel.PRESSED
            channels.setBtnIgnore(Channel.PRESSED, true)
            val light = channels.get(0, 0)?.takeIf { it.channel == Channel.LED }?.code
            channels.setBtnIgnore(Channel.PRESSED, false)
            pressed to light
        }
        screen.capture(name)
        assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/$name.txt") {
            it.writeText("looping_requests=$loops\npack_light=$light\npress_feedback=$pressed\nsound_requests=${audio.plays.size}\nstopped_voices=${audio.stoppedVoices}\n")
            true
        })
        return loops to light
    }

    private fun twoFingers(laterFirst: Boolean) {
        open()
        screen.onMain { screen.vm().scbFeedbackLight.setChecked(true) }
        val fingers = Fingers(screen)
        try {
            val first = fingers.down(pad())
            screen.await("First pointer press missing") { audio.plays.size == 1 }
            assertEquals(1 to 4, observe("input-first-finger-down"))
            val later = fingers.down(pad())
            screen.await("Both pointer presses must reach the real screen") { audio.plays.size == 2 }
            assertEquals(1 to 4, observe("input-both-fingers-down"))
            fingers.up(if (laterFirst) later else first)
            val afterRelease = observe(if (laterFirst) "input-later-finger-lift" else "input-first-finger-lift")
            assertEquals(true, feedback("input-remaining-finger-feedback"))
            fingers.up(if (laterFirst) first else later)
            assertEquals(0 to null, observe("input-last-finger-lift"))
            assertEquals(false, feedback("input-last-finger-feedback"))
            assertEquals(if (laterFirst) 0 to null else 1 to 4, afterRelease)
        } finally { fingers.cancelRemaining() }
    }

    @Test fun liftingTheFirstFingerKeepsTheOtherFingersLoop() = twoFingers(false)
    @Test fun liftingTheLaterFingerEndsItsOutputWhileTheFirstRemains() = twoFingers(true)

    @Test fun liftingTheLaterControllerEndsItsOutputWhileTheFingerRemains() {
        open()
        screen.onMain { screen.vm().scbFeedbackLight.setChecked(true) }
        val fingers = Fingers(screen)
        try {
            val finger = fingers.down(pad())
            screen.await("Finger press missing") { audio.plays.size == 1 }
            assertEquals(1 to 4, observe("input-finger-before-controller"))
            screen.onMain { screen.vm().controllerPadTouch(0, 0, true) }
            screen.await("Controller press missing") { audio.plays.size == 2 }
            assertEquals(1 to 4, observe("input-finger-and-controller-down"))
            screen.onMain { screen.vm().controllerPadTouch(0, 0, false) }
            val afterRelease = observe("input-later-controller-lift")
            assertEquals(true, feedback("input-finger-after-controller-feedback"))
            fingers.up(finger)
            assertEquals(0 to null, observe("input-finger-after-controller-lift"))
            assertEquals(false, feedback("input-final-finger-feedback"))
            assertEquals(0 to null, afterRelease)
        } finally {
            fingers.cancelRemaining()
            screen.onMain { screen.vm().releaseControllerPads() }
        }
    }

    @Test fun aFingerReleaseDoesNotEndTheNewerAutoplayLoop() {
        open("d 2000\non 1 1\nd 60000\noff 1 1\n")
        val fingers = Fingers(screen)
        try {
            val finger = fingers.down(pad())
            screen.await("Manual sound missing") { audio.plays.size == 1 }
            screen.onMain { screen.vm().switchPlayMode(PlayMode.AutoPlay) }
            screen.await("Autoplay sound missing") { audio.plays.size == 2 }
            fingers.up(finger)
            assertEquals(1 to 4, observe("input-player-off-after-auto-on"))
            screen.onMain { screen.vm().autoPlayPause() }
            assertEquals(0 to null, observe("input-auto-owner-paused"))
        } finally {
            fingers.cancelRemaining()
            screen.onMain { screen.vm().switchPlayMode(PlayMode.None) }
        }
    }

    @Test fun autoplayReleaseEndsItsOwnLoopWhileAnEarlierFingerRemains() {
        open("d 2000\non 1 1\nd 500\noff 1 1\non 1 2\nd 60000\noff 1 2\n")
        val fingers = Fingers(screen)
        try {
            val finger = fingers.down(pad())
            screen.await("Manual sound missing") { audio.plays.size == 1 }
            screen.onMain { screen.vm().switchPlayMode(PlayMode.AutoPlay) }
            // The following pad is a one-shot marker in the same batch after the release.
            screen.await("Autoplay release and its marker did not arrive") { audio.plays.size == 3 }
            assertEquals(0 to null, observe("input-auto-owner-off-before-player"))
            fingers.up(finger)
        } finally {
            fingers.cancelRemaining()
            screen.onMain { screen.vm().switchPlayMode(PlayMode.None) }
        }
    }

    private fun feedback(name: String): Boolean {
        val pressed = screen.onMain { screen.vm().channelManager.get(0, 0)?.channel == Channel.PRESSED }
        val drawn = CountDownLatch(1)
        screen.onMain {
            val decor = screen.resumed().window.decorView
            decor.postOnAnimation { decor.postOnAnimation { drawn.countDown() } }
        }
        assertTrue("The screen did not draw its updated feedback", drawn.await(10, TimeUnit.SECONDS))
        screen.capture(name)
        assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/$name.txt") {
            it.writeText("press_feedback=$pressed\nlooping_requests=${audio.ringingLoops().size}\n")
            true
        })
        return pressed
    }

    @Test fun feedbackStaysForAutoplayAfterTheNewerFingerReleases() {
        open()
        val fingers = Fingers(screen)
        try {
            screen.onMain { screen.vm().switchPlayMode(PlayMode.AutoPlay) }
            screen.await("Autoplay sound missing") { audio.plays.size == 1 }
            screen.onMain { screen.vm().scbFeedbackLight.setChecked(true) }
            val finger = fingers.down(pad())
            screen.await("Manual sound missing") { audio.plays.size == 2 }
            fingers.up(finger)
            assertEquals(0, audio.ringingLoops().size)
            assertEquals(true, feedback("input-feedback-auto-remains"))
            screen.onMain { screen.vm().autoPlayPause() }
            assertEquals(false, feedback("input-feedback-auto-final-release"))
        } finally {
            fingers.cancelRemaining()
            screen.onMain { screen.vm().switchPlayMode(PlayMode.None) }
        }
    }

    @Test fun feedbackStaysForTheFingerAfterNewerAutoplayReleases() {
        open("d 2000\non 1 1\nd 500\noff 1 1\non 1 2\nd 60000\noff 1 2\n")
        val fingers = Fingers(screen)
        try {
            val finger = fingers.down(pad())
            screen.await("Manual sound missing") { audio.plays.size == 1 }
            screen.onMain {
                screen.vm().switchPlayMode(PlayMode.AutoPlay)
                screen.vm().scbFeedbackLight.setChecked(true)
            }
            screen.await("Autoplay release and its marker did not arrive") { audio.plays.size == 3 }
            assertEquals(0, audio.ringingLoops().size)
            assertEquals(true, feedback("input-feedback-finger-remains"))
            fingers.up(finger)
            assertEquals(false, feedback("input-feedback-finger-final-release"))
        } finally {
            fingers.cancelRemaining()
            screen.onMain { screen.vm().switchPlayMode(PlayMode.None) }
        }
    }
}
