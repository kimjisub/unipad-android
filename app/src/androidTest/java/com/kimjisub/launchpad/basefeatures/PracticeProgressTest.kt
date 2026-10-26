package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import android.util.Log
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

    private data class SeekObservation(
        val elapsedMs: Long,
        val hints: Set<Int>,
        val progress: Int,
        val progressMax: Int,
    )

    private fun observeSeek(startedAt: Long): SeekObservation = screen.onMain {
        SeekObservation(
            SystemClock.elapsedRealtime() - startedAt,
            hints(),
            screen.vm().autoPlayProgress,
            screen.vm().autoPlayProgressMax,
        )
    }

    private fun record(name: String, observations: List<SeekObservation> = emptyList()) {
        val drawn = CountDownLatch(1)
        screen.onMain {
            val decor = screen.resumed().window.decorView
            decor.postOnAnimation { decor.postOnAnimation { drawn.countDown() } }
        }
        assertTrue("The screen did not draw its updated practice guide", drawn.await(10, TimeUnit.SECONDS))
        screen.capture(name)
        val observed = screen.onMain { "captureElapsedRealtimeMs=${SystemClock.elapsedRealtime()}\nmode=${screen.vm().playMode}\nactive=${screen.vm().autoPlayRunner?.active}\nprogress=${screen.vm().autoPlayProgress}\nstepMode=${screen.vm().autoPlayRunner?.stepMode}\nhints=${hints()}\n" }
        assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/$name.txt") { it.writeText("assertionObservations=$observations\n$observed"); true })
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
        val startedAt = screen.onMain {
            val now = SystemClock.elapsedRealtime()
            screen.vm().autoPlayNext()
            now
        }
        val observations = mutableListOf<SeekObservation>()
        fun observe(): SeekObservation = observeSeek(startedAt).also {
            observations += it
            Log.i("PracticeProgressTest", "seek observation: $it")
        }
        try {
            SystemClock.sleep(200)
            var upcoming = observe()
            assertTrue(
                "Timing condition failed: first main-thread read was +${upcoming.elapsedMs} ms; " +
                    "the upcoming hint must be read before +500 ms. $upcoming",
                upcoming.elapsedMs < 500,
            )
            // Poll only inside the fixture's original hint window. A missing hint in that
            // window is a value failure, distinct from a first read that missed the window.
            while (upcoming.hints != setOf(9)) {
                SystemClock.sleep(25)
                val next = observe()
                if (next.elapsedMs >= 500) break
                upcoming = next
            }
            assertEquals("Seeking must guide the upcoming pad at the new song time: $upcoming", setOf(9), upcoming.hints)
            assertFalse("Seeking must leave progress unfinished: $upcoming", upcoming.progress >= upcoming.progressMax)

            // Reuse the existing state-wait deadline for the separate, normal expiry check.
            var expired: SeekObservation? = null
            screen.await({ "The seek hint did not expire and advance after +500 ms: $expired" }) {
                observe().also { expired = it }.let {
                    it.elapsedMs >= 500 && it.hints.isEmpty() && it.progress > upcoming.progress
                }
            }
        } finally {
            // Capturing can outlast the hint. Preserve the assertion reads separately from
            // the later screen state, including when an assertion fails.
            record("practice-seek-forward", observations)
        }
    }

    @Test fun pausingGuidePlayLetsThePerformerAdvanceTheNextStep() {
        File(screen.pack(), "autoPlay").writeText("delay 700\ntouch 1 1\ndelay 5000\ntouch 2 2\ndelay 60000\n")
        screen.openPlay()
        screen.onMain { screen.vm().switchPlayMode(PlayMode.GuidePlay) }
        screen.await("The upcoming guide did not appear") { hints() == setOf(0) }
        screen.onMain { screen.vm().autoPlayPause() }
        assertEquals(PlayMode.StepPractice, screen.onMain { screen.vm().playMode })
        record("practice-paused-step-ready")
        val progress = screen.onMain { screen.vm().autoPlayProgress }
        pressPad(0)
        record("practice-paused-step-pressed")
        screen.await("Pressing the paused guide did not advance to the next step") { hints() == setOf(9) }
        assertTrue(screen.onMain { screen.vm().autoPlayProgress > progress })
        assertEquals(PlayMode.StepPractice, screen.onMain { screen.vm().playMode })
        assertTrue(screen.onMain { screen.vm().autoPlayRunner!!.stepMode })
        record("practice-paused-step-advanced")
    }

    @Test fun resumingPracticeKeepsTheUpcomingScreenHint() {
        File(screen.pack(), "autoPlay").writeText("delay 700\ntouch 1 1\ndelay 60000\n")
        screen.openPlay()
        screen.onMain { screen.vm().switchPlayMode(PlayMode.GuidePlay) }
        screen.await("The upcoming guide did not appear") { hints() == setOf(0) }
        screen.onMain { screen.vm().autoPlayPause() }
        screen.await("Paused guide play did not enter step practice") {
            screen.onMain { screen.vm().playMode == PlayMode.StepPractice && screen.vm().autoPlayRunner!!.stepMode }
        }
        record("practice-paused-upcoming-hint")
        SystemClock.sleep(1200)
        screen.onMain { screen.vm().autoPlayResume() }
        screen.await("The still upcoming guide must remain visible after resume") { hints() == setOf(0) }
        assertEquals(PlayMode.GuidePlay, screen.onMain { screen.vm().playMode })
        record("practice-resumed-upcoming-hint")
    }
}
