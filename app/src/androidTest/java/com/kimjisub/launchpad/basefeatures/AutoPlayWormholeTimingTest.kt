package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Real parser, screen listeners and runners; records decoded requests, without opening a speaker. */
class AutoPlayWormholeTimingTest : PlaybackScreenTest() {
    private fun fixture(script: String, voice: Boolean = true) {
        val pack = screen.pack()
        val silence = File(pack, "sounds/silence.wav").readBytes()
        for (name in listOf("move", "first", "voice", "drum")) {
            File(pack, "sounds/$name.wav").writeBytes(silence)
        }
        File(pack, "keySound").writeText(
            "1 1 1 move.wav 1 2\n" +
                (if (voice) "1 1 2 first.wav\n1 1 2 voice.wav\n" else "") +
                "2 1 2 drum.wav\n",
        )
        File(pack, "keyLED").listFiles().orEmpty().forEach { it.delete() }
        File(pack, "keyLED/1 1 2 1").writeText("on 1 2 a 5\nd 60000\n")
        File(pack, "keyLED/1 1 2 2").writeText("on 1 2 a 6\nd 60000\n")
        File(pack, "keyLED/2 1 2 1").writeText("on 1 2 a 9\nd 60000\n")
        File(pack, "autoPlay").writeText(script)
    }

    private fun soundNames() = audio.plays.map { id -> audio.loaded.entries.single { it.value == id }.key }

    private fun record(name: String, chains: List<Int> = emptyList()) {
        screen.capture("wormhole-$name")
        assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/wormhole-$name.txt") {
            it.writeText("sounds=${soundNames()}\nchains=$chains\nrequests=${audio.requests}\nnativeAudioOutput=false\n")
            true
        })
    }

    @Test fun autoplayKeepsTheVoiceQueueBeforeMoving_andUsesTheNewPageLightAfterward() {
        fixture("touch 1 2\nd 10\ntouch 1 1\nd 1\ntouch 1 2\nd 189\non 1 2\nd 60000\n")
        screen.openPlay()
        val vm = screen.vm()
        val chains = CopyOnWriteArrayList<Int>()
        screen.onMain { vm.chain.addObserver { c, _ -> chains += c } }
        pauseOnPress(0, 1, plays = 3)
        record("voice-before-move", chains)
        assertEquals(listOf("first.wav", "move.wav", "voice.wav"), soundNames())
        assertEquals(0, screen.onMain { vm.chain.value })
        screen.onMain { vm.autoPlayResume() }
        screen.await("The final press was not delivered") { audio.plays.size >= 4 }
        screen.await("The new page's light was not delivered") { screen.onMain {
            vm.channelManager.get(0, 1)?.let { it.channel == Channel.LED && it.code == 9 } == true
        } }
        record("voice-and-light", chains)
        assertEquals(listOf("first.wav", "move.wav", "voice.wav", "drum.wav"), soundNames())
        assertEquals("Automatic input must not also schedule the hand-input move", listOf(1), chains.toList())
    }

    @Test fun anEmptyPadBeforeTheMoveStaysSilent() {
        fixture("touch 1 1\nd 1\ntouch 1 2\nd 199\non 1 2\nd 60000\n", voice = false)
        screen.openPlay()
        screen.onMain { screen.vm().switchPlayMode(PlayMode.AutoPlay) }
        screen.await("The last pad was not delivered") { screen.onMain { screen.vm().autoPlayProgress >= 8 } }
        screen.await("The final sound was not delivered") { audio.plays.size >= 2 }
        record("empty-pad")
        assertEquals(listOf("move.wav", "drum.wav"), soundNames())
    }

    @Test fun stepPracticeGuidesBothPadsOnTheOriginalPage() {
        fixture("touch 1 1\nd 1\ntouch 1 2\nd 199\n")
        screen.openPlay()
        val vm = screen.vm()
        screen.onMain { vm.switchPlayMode(PlayMode.StepPractice) }
        screen.await("The moving pad and voice must be guided together on page one") { screen.onMain {
            vm.channelManager.get(0, 0)?.channel == Channel.GUIDE &&
                vm.channelManager.get(0, 1)?.channel == Channel.GUIDE && vm.chain.value == 0
        } }
        record("practice-voice")
        // Press the voice first, as a person may when learning this close pair. No wall-clock
        // assertion pretends that a physical tap can be injected within one millisecond.
        pressPad(1)
        pressPad(0)
        screen.await("The final practice group did not finish") { screen.onMain { !vm.autoPlayRunner!!.active } }
        assertEquals(listOf("first.wav", "move.wav"), soundNames())
    }

    @Test fun stepPracticeInSongOrderKeepsProgressAfterTheHandMove() {
        fixture("touch 1 1\nd 1\ntouch 1 2\nd 199\ntouch 1 3\nd 1000\n")
        File(screen.pack(), "keySound").appendText("2 1 3 drum.wav\n")
        screen.openPlay()
        val vm = screen.vm()
        screen.onMain { vm.switchPlayMode(PlayMode.StepPractice) }
        screen.await("The first step must guide the two original-page pads") { screen.onMain {
            vm.channelManager.get(0, 0)?.channel == Channel.GUIDE &&
                vm.channelManager.get(0, 1)?.channel == Channel.GUIDE
        } }
        pressPad(0)
        screen.await("The hand-input wormhole must keep its ordinary delayed move") {
            screen.onMain { vm.chain.value == 1 }
        }
        SystemClock.sleep(300)
        record("practice-song-order-before-return")
        val button = chainButton(0)
        assertTrue(screen.device.click(button.x, button.y))
        screen.await("Returning must show only the remaining pad") { screen.onMain {
            vm.chain.value == 0 && vm.channelManager.get(0, 1)?.channel == Channel.GUIDE &&
                vm.channelManager.get(0, 0)?.channel != Channel.GUIDE
        } }
        record("practice-song-order-remaining")
        pressPad(1)
        val next = chainButton(1)
        assertTrue(screen.device.click(next.x, next.y))
        screen.await("The following group must be guided on page two") { screen.onMain {
            vm.chain.value == 1 && vm.channelManager.get(0, 2)?.channel == Channel.GUIDE
        } }
        pressPad(2)
        screen.await("Song-order step practice must finish without repeating the moving pad") {
            screen.onMain { !vm.autoPlayRunner!!.active }
        }
        record("practice-song-order-complete")
        assertEquals(listOf("move.wav", "first.wav", "drum.wav"), soundNames())
    }

    /** Pauses in the actual press callback, before the runner can process its next element. */
    private fun pauseOnPress(padX: Int = 0, padY: Int = 0, plays: Int? = null) {
        val paused = AtomicBoolean()
        screen.onMain {
            val vm = screen.vm()
            val drawn = checkNotNull(vm.uiCallback)
            vm.uiCallback = object : PlayActivityViewModel.UiCallback by drawn {
                override fun setLedPad(x: Int, y: Int) {
                    drawn.setLedPad(x, y)
                    if (x == padX && y == padY && (plays == null || audio.plays.size == plays) &&
                        vm.channelManager.get(x, y)?.channel == Channel.PRESSED &&
                        paused.compareAndSet(false, true)) vm.autoPlayPause()
                }
            }
            vm.switchPlayMode(PlayMode.AutoPlay)
            // Starting a mode restores the pack's light defaults. Enable press feedback after
            // that reset, on this same main-thread turn before any autoplay press is delivered.
            vm.scbFeedbackLight.setChecked(true)
            assertTrue("Press feedback must be enabled for the observation", vm.scbFeedbackLight.isChecked())
        }
        screen.await("The selected press did not pause playback") { paused.get() }
    }

    @Test fun aPausedMoveWaitsForResume_andNormalEndAppliesIt() {
        fixture("on 1 1\nd 30\n")
        screen.openPlay()
        val vm = screen.vm()
        pauseOnPress()
        SystemClock.sleep(250)
        record("paused")
        assertEquals(0, screen.onMain { vm.chain.value })
        assertTrue(screen.onMain { vm.autoPlayRunner!!.active })
        screen.onMain { vm.autoPlayResume() }
        screen.await("Normal end must apply the remaining move without another wait") { screen.onMain {
            !vm.autoPlayRunner!!.active && vm.chain.value == 1
        } }
        assertEquals(listOf("move.wav"), soundNames())
    }

    @Test fun stoppingBeforeTheMoveCancelsIt() {
        fixture("on 1 1\nd 1000\n")
        screen.openPlay()
        val vm = screen.vm()
        pauseOnPress()
        screen.onMain { vm.switchPlayMode(PlayMode.None) }
        SystemClock.sleep(250)
        record("stopped")
        assertEquals(0, screen.onMain { vm.chain.value })
        assertTrue(screen.onMain { !vm.autoPlayRunner!!.active })
    }

    @Test fun closingTheScreenBeforeTheMoveCancelsIt() {
        fixture("on 1 1\nd 1000\n")
        val scenario = screen.openPlay()
        val vm = screen.vm()
        val chains = CopyOnWriteArrayList<Int>()
        screen.onMain { vm.chain.addObserver { c, _ -> chains += c } }
        pauseOnPress()
        scenario.close()
        SystemClock.sleep(250)
        assertEquals(emptyList<Int>(), chains.toList())
        assertEquals(0, screen.onMain { vm.chain.value })
    }
}
