package com.kimjisub.launchpad.basefeatures

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class KeyLedDirectoryTest : PlaybackScreenTest() {
    @Test fun subdirectoriesDoNotShowWarningsOrPreventPlayback() {
        val root = screen.pack()
        val nested = File(root, "keyLED/sub")
        assertTrue(nested.mkdir())
        File(nested, "1 2 2 1").writeText("o 2 2 a 9\n")
        assertTrue(File(root, "keyLED/1 3 3 1").mkdir())
        val label = InstrumentationRegistry.getArguments().getString("captureLabel") ?: "after"

        screen.launch(PlayActivity::class.java) { putExtra("path", root.path) }
        screen.await("Pack did not load") { screen.ready() }
        if (screen.onMain { screen.vm().unipack.errorDetail != null }) {
            screen.node(By.text(screen.text(R.string.accept)))
        }
        screen.capture("keyled-directory-$label-open")
        assertNull(screen.onMain { screen.vm().unipack.errorDetail })
        assertFalse(screen.has(By.text(screen.text(R.string.accept))))
        assertEquals(1, screen.onMain { screen.vm().unipack.ledTableCount })
        assertNull(screen.onMain { screen.vm().unipack.ledGet(0, 1, 1) })
        assertNull(screen.onMain { screen.vm().unipack.ledGet(0, 2, 2) })

        pressPad(0)
        screen.await("Direct keyLED file did not light the pad") { screen.onMain {
            screen.vm().channelManager.get(0, 0)?.let { it.channel == Channel.LED && it.code == 5 } == true
        } }
        screen.capture("keyled-directory-$label-light")
        screen.await("Direct keyLED file did not turn the light off") { screen.onMain {
            screen.vm().channelManager.get(0, 0)?.channel != Channel.LED
        } }
        val activity = screen.resumed()
        screen.clickDescription(R.string.menu)
        screen.clickDescription(R.string.quit)
        screen.await("Quit did not close the pack") { screen.onMain { activity.isFinishing || activity.isDestroyed } }
    }
}
