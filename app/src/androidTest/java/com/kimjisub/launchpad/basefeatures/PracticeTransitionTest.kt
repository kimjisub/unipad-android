package com.kimjisub.launchpad.basefeatures

import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.manager.LaunchpadColor
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real inputs, lifecycle, decoded sound identities and rendered pad colours; never opens a speaker. */
class PracticeTransitionTest : PlaybackScreenTest() {
    private val label get() = InstrumentationRegistry.getArguments().getString("captureLabel") ?: "current"

    private fun fixture(script: String) {
        val pack = screen.pack()
        File(pack, "autoPlay").writeText(script)
        File(pack, "keyLED").listFiles().orEmpty().forEach { it.delete() }
        File(pack, "keySound").writeText(buildString {
            for (chain in 1..2) for (x in 1..3) {
                val name = "pad-$chain-$x.wav"
                File(pack, "sounds/$name").writeBytes(File(pack, "sounds/silence.wav").readBytes())
                append("$chain $x 1 $name\n")
                File(pack, "keyLED/$chain $x 1 1").writeText("on $x 1 auto ${x * 2 + 3}\ndelay 60000\noff $x 1\n")
            }
        })
    }

    private fun guidePads(): Set<Int> = screen.onMain {
        (0..2).filter { screen.vm().channelManager.get(it, 0)?.channel == Channel.GUIDE }.toSet()
    }

    private fun assertGuide(vararg x: Int) {
        screen.await("Expected guide pads ${x.toSet()}, got ${guidePads()}") { guidePads() == x.toSet() }
    }

    private fun assertLight(x: Int) {
        val vm = screen.vm()
        screen.await("Autoplay light missing from pad ${x + 1}") { screen.onMain {
            vm.channelManager.get(x, 0)?.let { it.channel == Channel.LED && it.code == (x + 1) * 2 + 3 } == true
        } }
        screen.onMain {
            val pads = screen.views(screen.resumed().window.decorView).filterIsInstance<PadView>()
            val led = pads[x * 8].findViewById<View>(com.kimjisub.design.R.id.led)
            assertEquals("The pad view must render the pack light", LaunchpadColor.ARGB[(x + 1) * 2 + 3].toInt(),
                (led.background as ColorDrawable).color)
        }
    }

    private fun assertSounds(vararg names: String) {
        assertEquals("Each pad must sound exactly once and in order", names.map { audio.loaded.getValue("$it.wav") }, audio.plays.toList())
    }

    private fun interruptedPartialChord(away: Long, finalGroup: Boolean) {
        fixture("on 1 1\ndelay 5000\non 2 1\non 3 1\n" + if (finalGroup) "" else "delay 20000\non 1 1\n")
        screen.openPlay()
        screen.chooseOption(R.string.stepPractice)
        assertGuide(0)
        pressPad(0)
        assertGuide(1, 2)
        pressPad(8)
        assertGuide(2)
        assertSounds("pad-1-1", "pad-1-2")
        screen.capture("practice-$label-partial-before-lock-$away-$finalGroup")
        val vm = screen.vm()
        screen.device.sleep()
        screen.await("Lock did not suspend practice") { screen.onMain { vm.autoPlayRunner!!.suspended && !vm.screenVisible } }
        val progress = screen.onMain { vm.autoPlayRunner!!.progress }
        SystemClock.sleep(away)
        screen.device.wakeUp()
        screen.device.executeShellCommand("wm dismiss-keyguard")
        screen.await("Unlock did not return to play") { screen.ready() }
        screen.onMain {
            assertTrue(vm.autoPlayRunner!!.suspended)
            assertEquals(progress, vm.autoPlayRunner!!.progress)
        }
        assertGuide()
        assertSounds("pad-1-1", "pad-1-2")
        screen.clickDescription(R.string.cd_autoplay_play)
        assertGuide(2)
        screen.capture("practice-$label-restored-$away-$finalGroup")
        screen.chooseOption(R.string.autoPlay)
        screen.await("The pending pad did not sound") { audio.plays.size == 3 }
        assertSounds("pad-1-1", "pad-1-2", "pad-1-3")
        assertGuide()
        assertLight(2)
        screen.capture("practice-$label-replayed-light-$away-$finalGroup")
        if (finalGroup) {
            screen.await("The final group did not end after sounding") { screen.onMain { !vm.autoPlayRunner!!.active && vm.playMode == PlayMode.None } }
        } else {
            screen.onMain { assertTrue("The later group must still be pending", vm.autoPlayRunner!!.active) }
        }
    }

    @Test fun shortLockRestoresThePendingPadAndItsAutoplayLight() = interruptedPartialChord(1000, false)
    @Test fun longLockRestoresThePendingPadAndItsAutoplayLight() = interruptedPartialChord(10000, false)
    @Test fun shortLockOnTheFinalGroupEndsAfterThePendingPadSounds() = interruptedPartialChord(1000, true)
    @Test fun longLockOnTheFinalGroupEndsAfterThePendingPadSounds() = interruptedPartialChord(10000, true)

    @Test fun partialChordReplayUsesTheSameLightsAsNormalAutoplay() {
        fixture("on 1 1\non 2 1\non 3 1\ndelay 20000\n")
        screen.openPlay()
        val vm = screen.vm()
        screen.chooseOption(R.string.autoPlay)
        screen.await("Normal autoplay did not play the chord") { audio.plays.size == 3 }
        for (x in 0..2) assertLight(x)
        screen.capture("practice-$label-normal-autoplay-lights")
        screen.onMain { vm.switchPlayMode(PlayMode.None) }
        audio.resetPlaybackRequests()
        screen.chooseOption(R.string.stepPractice)
        assertGuide(0, 1, 2)
        pressPad(0)
        assertGuide(1, 2)
        screen.capture("practice-$label-partial-before-autoplay")
        screen.chooseOption(R.string.autoPlay)
        screen.await("The remaining chord did not play") { audio.plays.size == 3 }
        assertSounds("pad-1-1", "pad-1-2", "pad-1-3")
        assertGuide()
        for (x in 1..2) assertLight(x)
        screen.capture("practice-$label-recovered-autoplay-lights")
    }

    @Test fun returningToStepsBeforeDeliveryKeepsEveryPadAcrossAChainWait() {
        fixture("on 1 1\non 2 1\non 3 1\ndelay 5000\nchain 2\non 1 1\n")
        screen.openPlay()
        screen.chooseOption(R.string.stepPractice)
        assertGuide(0, 1, 2)
        pressPad(0)
        assertGuide(1, 2)
        val vm = screen.vm()
        screen.onMain {
            vm.switchPlayMode(PlayMode.AutoPlay)
            vm.switchPlayMode(PlayMode.StepPractice)
        }
        assertGuide(1, 2)
        assertSounds("pad-1-1")
        pressPad(8)
        assertGuide(2)
        pressPad(16)
        screen.await("The second chain was not requested") { screen.onMain {
            vm.channelManager.get(-1, com.kimjisub.launchpad.viewmodel.PlayActivityViewModel.CHAIN_INDEX_OFFSET + 1)?.channel == Channel.GUIDE
        } }
        val secondChain = chainButton(1)
        screen.device.click(secondChain.x, secondChain.y)
        assertGuide(0)
        screen.capture("practice-$label-returned-next-chain")
        pressPad(0)
        screen.await("The last input did not end practice") { screen.onMain { !vm.autoPlayRunner!!.active } }
        assertSounds("pad-1-1", "pad-1-2", "pad-1-3", "pad-2-1")
        screen.chooseOption(R.string.autoPlay)
        screen.await("A fresh run did not replay the earlier pressed pad") { audio.plays.size >= 7 }
        assertSounds("pad-1-1", "pad-1-2", "pad-1-3", "pad-2-1", "pad-1-1", "pad-1-2", "pad-1-3")
        assertGuide()
        assertLight(0)
        assertLight(1)
        assertLight(2)
        screen.capture("practice-$label-fresh-autoplay-lights")
    }
}
