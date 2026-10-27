package com.kimjisub.launchpad.basefeatures

import android.content.Intent
import android.os.SystemClock
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Checks initialization through the real screen, with the fixture decoder and recorded sound requests. */
class PlaybackInitializationTest : PlaybackScreenTest() {
    @Test fun roundPadsShowThePacksOwnLight() {
        val root = screen.pack()
        val info = File(root, "info")
        info.writeText(info.readText().replace("squareButton=true", "squareButton=false"))
        assertTrue(info.readText().contains("squareButton=false"))
        File(root, "keyLED/1 1 1 1").writeText("on 1 1 FF0000\ndelay 60000\noff 1 1\n")
        screen.openPlay()
        pressPad(0)
        screen.instrumentation.waitForIdleSync()
        SystemClock.sleep(200)
        screen.capture("round-pack-animation")
        assertTrue(screen.onMain { screen.vm().scbLed.checked })
        assertFalse(screen.onMain { screen.vm().scbFeedbackLight.checked })
        assertEquals(Channel.LED, screen.onMain { screen.vm().channelManager.get(0, 0)?.channel })
    }

    @Test fun startingAutoPlayRestoresThePacksLight() {
        File(screen.pack(), "autoPlay").writeText("on 1 1\ndelay 60000\noff 1 1\n")
        File(screen.pack(), "keyLED/1 1 1 1").writeText("on 1 1 FF0000\ndelay 60000\noff 1 1\n")
        screen.openPlay()
        screen.onMain {
            screen.vm().scbLed.setChecked(false)
            screen.vm().scbFeedbackLight.setChecked(true)
            screen.vm().switchPlayMode(PlayMode.AutoPlay)
        }
        screen.await("Auto play did not press the first pad") { audio.plays.isNotEmpty() }
        screen.instrumentation.waitForIdleSync()
        SystemClock.sleep(200)
        screen.capture("autoplay-start-lights")
        assertTrue(screen.onMain { screen.vm().scbLed.checked })
        assertFalse(screen.onMain { screen.vm().scbFeedbackLight.checked })
        assertEquals(Channel.LED, screen.onMain { screen.vm().channelManager.get(0, 0)?.channel })
    }

    @Test fun selectingAChainOutsideThePackLeavesTheCurrentChainAlone() {
        screen.openPlay()
        screen.onMain {
            val vm = screen.vm()
            vm.scbProLightMode.setChecked(true)
            val activity = screen.resumed() as PlayActivity
            @Suppress("UNCHECKED_CAST")
            val views = PlayActivity::class.java.getDeclaredField("chainViews")
                .apply { isAccessible = true }.get(activity) as Array<com.kimjisub.design.view.ChainView?>
            assertTrue(requireNotNull(views[14]).click())
        }
        screen.instrumentation.waitForIdleSync()
        screen.capture("absent-chain-button")
        assertEquals("An absent chain button must not select the last chain", 0, screen.onMain { screen.vm().chain.value })
    }

    @Test fun unplayablePackOffersRetryInsteadOfOpeningASilentScreen() {
        File(screen.pack(), "sounds").listFiles().orEmpty().filter { it.isFile }.forEach { it.writeBytes(byteArrayOf()) }
        screen.launch(MainActivity::class.java)
        val intent = Intent(screen.context, PlayActivity::class.java).putExtra("path", screen.pack().path)
        val mainActivity = screen.resumed()
        // Preserve the library task as a normal pack launch does; a second ActivityScenario
        // clears that task and sends the user to the launcher when the play screen closes.
        screen.onMain { mainActivity.startActivity(intent) }
        screen.awaitActivity(PlayActivity::class.java)
        screen.node(By.text(screen.text(R.string.audio_load_failed_title)))
        val report = requireNotNull(screen.onMain { screen.vm().soundLoadReport })
        assertTrue("Every used sound file must have failed", report.allFailed)
        assertEquals(report.usedFiles, report.failures.size)
        report.failures.groupingBy { it.reason }.eachCount().forEach { (reason, count) ->
            screen.node(By.text(screen.context.resources.getQuantityString(failureCount(reason), count, count)))
        }
        listOf(R.string.audio_load_details, R.string.audio_load_retry, R.string.audio_load_pack_list)
            .forEach { screen.node(By.text(screen.text(it))) }
        assertFalse("The play screen must not open behind the notice", screen.has(By.desc(screen.text(R.string.menu))))
        assertFalse("Pads must not accept input", screen.onMain { screen.vm().enable })
        assertTrue("No sound may be requested", audio.plays.isEmpty())
        screen.capture("unplayable-pack-notice")

        screen.clickText(R.string.audio_load_pack_list)
        screen.awaitActivity(MainActivity::class.java)
        SystemClock.sleep(300)
        val capture = "${TestCaptures.DIRECTORY}/basefeatures-captures/unplayable-pack-result.png"
        screen.device.executeShellCommand("screencap -p $capture")
        assertTrue("System screenshot was not saved", screen.device.executeShellCommand("stat -c %s $capture")
            .trim().toLongOrNull()?.let { it > 0 } == true)
    }

    private fun failureCount(reason: SoundRunner.FailureReason) = when (reason) {
        SoundRunner.FailureReason.LIMIT -> R.plurals.audio_load_limit
        SoundRunner.FailureReason.UNREADABLE -> R.plurals.audio_load_unreadable
        SoundRunner.FailureReason.DECODE -> R.plurals.audio_load_decode
        SoundRunner.FailureReason.MEMORY -> R.plurals.audio_load_memory
    }

    @Test fun controllerAttachPreservesTheNextSoundAndLight() {
        val root = screen.pack()
        File(root, "sounds/third.wav").writeBytes(File(root, "sounds/silence.wav").readBytes())
        File(root, "keySound").writeText("1 1 1 silence.wav\n1 1 1 silence2.wav\n1 1 1 third.wav\n")
        File(root, "keyLED/1 1 1 1").writeText("on 1 1 FF0000\ndelay 60000\noff 1 1\n")
        File(root, "keyLED/1 1 1 1 b").writeText("on 1 1 00FF00\ndelay 60000\noff 1 1\n")
        File(root, "keyLED/1 1 1 1 c").writeText("on 1 1 0000FF\ndelay 60000\noff 1 1\n")
        screen.openPlay()
        pressPad(0)
        pressPad(0)
        screen.onMain {
            val activity = screen.resumed() as PlayActivity
            val controller = PlayActivity::class.java.getDeclaredField("midiController")
                .apply { isAccessible = true }.get(activity) as MidiController
            controller.onAttach()
        }
        pressPad(0)
        screen.instrumentation.waitForIdleSync()
        SystemClock.sleep(200)
        screen.capture("controller-attach-third-mapping")
        assertEquals("Attaching must not restart the sound order", audio.loaded.getValue("third.wav"), audio.plays.last())
        assertEquals("Attaching must not restart the animation order", 0xff0000ff.toInt(),
            screen.onMain { screen.vm().channelManager.get(0, 0)?.color })
    }
}
