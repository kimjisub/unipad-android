package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The real screen changes modes; the recording audio engine keeps the speaker silent. */
class AutoPlayChainWaitTest : PlaybackScreenTest() {
    @Test fun switchingFromAChainHintDoesNotFlushTheWaitingNotes() {
        File(screen.pack(), "autoPlay").writeText(
            "d 1000\nc 2\no 1 1\nf 1 1\nd 500\no 1 1\nf 1 1\nd 500\no 1 1\nf 1 1\nd 10000\n"
        )
        screen.openPlay()
        val vm = screen.vm()
        val label = InstrumentationRegistry.getArguments().getString("captureLabel") ?: "after"
        try {
            screen.onMain { vm.switchPlayMode(PlayMode.GuidePlay) }
            screen.await("The second chain hint did not appear") {
                screen.onMain {
                    vm.channelManager.get(-1, PlayActivityViewModel.CHAIN_INDEX_OFFSET + 1)?.channel == Channel.GUIDE
                }
            }
            screen.capture("chain-wait-$label-hint")
            SystemClock.sleep(5000)
            val switchedAt = SystemClock.elapsedRealtime()
            screen.onMain { vm.switchPlayMode(PlayMode.AutoPlay) }
            SystemClock.sleep(250)
            val observedAt = SystemClock.elapsedRealtime()
            val earlyPlays = audio.plays.size
            screen.capture("chain-wait-$label-switched")
            assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/chain-wait-$label.txt") {
                it.writeText("switched_at_ms=$switchedAt\nobserved_at_ms=$observedAt\nearly_sound_requests=$earlyPlays\n")
                true
            })
            assertEquals("The remaining 800 ms must survive the mode change", 0, earlyPlays)
            screen.await("The three notes did not play after switching modes") { audio.plays.size == 3 }
            assertEquals(1, screen.onMain { vm.chain.value })
        } finally {
            screen.onMain { vm.switchPlayMode(PlayMode.None) }
        }
    }
}
