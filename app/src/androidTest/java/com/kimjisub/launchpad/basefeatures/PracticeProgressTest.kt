package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Exercises practice on the real play screen; the recorded audio engine opens no speaker. */
class PracticeProgressTest : PlaybackScreenTest() {
    private fun hints(): Set<Int> = screen.onMain {
        (0 until 64).filter { screen.vm().channelManager.get(it / 8, it % 8)?.channel == Channel.GUIDE }.toSet()
    }

    private fun record(name: String) {
        val drawn = CountDownLatch(1)
        screen.onMain {
            val decor = screen.resumed().window.decorView
            decor.postOnAnimation { decor.postOnAnimation { drawn.countDown() } }
        }
        assertTrue("The screen did not draw its updated practice guide", drawn.await(10, TimeUnit.SECONDS))
        screen.capture(name)
        val observed = screen.onMain { "mode=${screen.vm().playMode}\nactive=${screen.vm().autoPlayRunner?.active}\nprogress=${screen.vm().autoPlayProgress}\nhints=${hints()}\n" }
        assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/$name.txt") { it.writeText(observed); true })
    }

    @Test fun theFinalStepRemainsAvailableUntilItIsPlayed() {
        File(screen.pack(), "autoPlay").writeText("touch 1 1\n")
        screen.openPlay()
        screen.onMain { screen.vm().switchPlayMode(PlayMode.StepPractice) }
        SystemClock.sleep(250)
        record("practice-final-step-waiting")
        assertEquals("The final step must wait for the performer", PlayMode.StepPractice, screen.onMain { screen.vm().playMode })
        assertTrue(screen.onMain { screen.vm().autoPlayRunner!!.active })
        assertEquals(setOf(0), hints())
        pressPad(0)
        screen.await("Practice did not finish after the final step") { screen.onMain { screen.vm().playMode == PlayMode.None } }
        assertTrue(hints().isEmpty())
    }

    @Test fun stoppingStepPracticeRemovesItsHint() {
        File(screen.pack(), "autoPlay").writeText("touch 1 1\ndelay 2000\ntouch 2 2\n")
        screen.openPlay()
        screen.onMain { screen.vm().switchPlayMode(PlayMode.StepPractice) }
        screen.await("The first step hint did not appear") { hints() == setOf(0) }
        screen.onMain { screen.vm().autoPlayRunner!!.stop() }
        screen.await("The stopped runner did not finish") { screen.onMain { screen.vm().playMode == PlayMode.None } }
        record("practice-stopped-hints")
        assertTrue("No guide may stay lit after practice stops", hints().isEmpty())
    }

    @Test fun seekingForwardShowsTheHintAtTheNewPosition() {
        // Repeating explicit delays puts the next hint far away without adding pad presses.
        File(screen.pack(), "autoPlay").writeText("delay 5000\ntouch 1 1\n" + "delay 5000\n".repeat(38) + "delay 500\ntouch 2 2\ndelay 60000\n")
        screen.openPlay()
        screen.onMain { screen.vm().switchPlayMode(PlayMode.GuidePlay) }
        screen.await("The sequence did not start waiting at the first delay") { screen.onMain { screen.vm().autoPlayProgress == 1 } }
        screen.onMain { screen.vm().autoPlayNext() }
        SystemClock.sleep(200)
        record("practice-seek-forward")
        assertEquals("Seeking must guide the upcoming pad at the new song time", setOf(9), hints())
        assertFalse(screen.onMain { screen.vm().autoPlayProgress >= screen.vm().autoPlayProgressMax })
    }

    @Test fun resumingPracticeKeepsTheUpcomingScreenHint() {
        File(screen.pack(), "autoPlay").writeText("delay 700\ntouch 1 1\ndelay 60000\n")
        screen.openPlay()
        screen.onMain { screen.vm().switchPlayMode(PlayMode.GuidePlay) }
        screen.await("The upcoming guide did not appear") { hints() == setOf(0) }
        screen.onMain { screen.vm().autoPlayPause() }
        record("practice-paused-upcoming-hint")
        SystemClock.sleep(1200)
        screen.onMain { screen.vm().autoPlayResume() }
        record("practice-resumed-upcoming-hint")
        assertEquals("The still upcoming guide must remain visible after resume", setOf(0), hints())
    }
}
