package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Observes real screen state and decoded sound requests; the recording engine opens no speaker. */
class AutoPlayTimingTest : PlaybackScreenTest() {
    @Test fun aWormholeDoesNotUndoTheNextAutoplayChain() {
        val pack = screen.pack()
        val info = File(pack, "info")
        info.writeText(info.readText().replace(Regex("(?m)^chain=.*$"), "chain=3"))
        File(pack, "keySound").writeText("1 1 1 silence.wav 1 2\n")
        File(pack, "autoPlay").writeText("on 1 1\ndelay 50\nchain 3\ndelay 60000\noff 1 1\n")
        screen.openPlay()
        val vm = screen.vm()
        val chains = CopyOnWriteArrayList<Int>()
        val record: (Int, Int) -> Unit = { c, _ -> chains += c }
        vm.chain.addObserver(record)
        try {
            screen.onMain { vm.switchPlayMode(PlayMode.AutoPlay) }
            screen.await("Autoplay did not request the first sound") { audio.plays.isNotEmpty() }
            SystemClock.sleep(300)
            screen.capture("autoplay-wormhole-final-chain")
            assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/autoplay-wormhole-chains.txt") {
                it.writeText(chains.joinToString("\n"))
                true
            })
            assertEquals("The parsed chain move must not be replayed after 100 ms", listOf(1, 2), chains.toList())
            assertEquals(2, screen.onMain { vm.chain.value })
        } finally {
            vm.chain.removeObserver(record)
            screen.onMain { vm.switchPlayMode(PlayMode.None) }
        }
    }

    @Test fun resumingKeepsTheUnfinishedDelay() {
        File(screen.pack(), "autoPlay").writeText("on 1 1\ndelay 3000\non 2 2\ndelay 60000\noff 2 2\n")
        screen.openPlay()
        val vm = screen.vm()
        screen.onMain {
            vm.scbTraceLog.setChecked(true)
            vm.switchPlayMode(PlayMode.AutoPlay)
        }
        screen.await("Autoplay did not request the first sound") { audio.plays.size == 1 }
        val pausedAt = SystemClock.elapsedRealtime()
        screen.onMain { vm.autoPlayPause() }
        SystemClock.sleep(3500)
        assertEquals("No sound may start while paused", 1, audio.plays.size)
        val resumedAt = SystemClock.elapsedRealtime()
        screen.onMain { vm.autoPlayResume() }
        SystemClock.sleep(200)
        val playsAfterResume = audio.plays.size
        val observedAt = SystemClock.elapsedRealtime()
        screen.capture("autoplay-delay-after-resume")
        assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/autoplay-delay-observation.txt") {
            it.writeText("paused_at_ms=$pausedAt\nresumed_at_ms=$resumedAt\nobserved_at_ms=$observedAt\nsound_requests_after_resume=$playsAfterResume\n")
            true
        })
        assertEquals("The remaining delay must not disappear on resume", 1, playsAfterResume)
        screen.await("Autoplay did not continue after the remaining delay") { audio.plays.size == 2 }
        screen.onMain { vm.switchPlayMode(PlayMode.None) }
    }
}
