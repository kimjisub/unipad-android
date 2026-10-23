package com.kimjisub.launchpad.basefeatures

import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.midi.controller.MidiController
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

    @Test fun unplayablePackClosesInsteadOfOpeningASilentScreen() {
        File(screen.pack(), "sounds").listFiles().orEmpty().filter { it.isFile }.forEach { it.writeBytes(byteArrayOf()) }
        screen.launch(MainActivity::class.java)
        val intent = Intent(screen.context, PlayActivity::class.java).putExtra("path", screen.pack().path)
        ActivityScenario.launch<PlayActivity>(intent).use { scenario ->
            try {
                screen.await("A pack without a decodable sound stayed open") { scenario.state == Lifecycle.State.DESTROYED }
                screen.node(By.text(screen.text(R.string.pack_no_playable_sounds)))
            } finally {
                screen.capture("unplayable-pack-result")
            }
        }
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
