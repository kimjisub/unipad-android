package com.kimjisub.launchpad

import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Until
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.basefeatures.FeatureScreen
import com.kimjisub.launchpad.basefeatures.RecordingAudio
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class UniPackColorLengthDeviceTest {
    @Test
    fun overlongColorDoesNotReplaceVisibleLightAndPackCanExit() {
        val screen = FeatureScreen()
        val configurator = Configurator.getInstance()
        val idleTimeout = configurator.waitForIdleTimeout
        configurator.waitForIdleTimeout = 500L
        val label = InstrumentationRegistry.getArguments().getString("captureLabel") ?: "after"
        fun capture(name: String) {
            val path = "/data/local/tmp/jis73/$name.png"
            screen.device.executeShellCommand("mkdir -p /data/local/tmp/jis73")
            screen.device.executeShellCommand("screencap -p $path")
            assertTrue("Screenshot missing: $path", screen.device.executeShellCommand("ls -l $path").contains(name))
        }
        val root = kotlin.io.path.createTempDirectory(screen.context.cacheDir.toPath(), "jis73-").toFile()
        val audio = RecordingAudio()
        val doubles = module { single<SoundRunner.Engine> { audio } }
        loadKoinModules(doubles)
        var scenario: androidx.test.core.app.ActivityScenario<com.kimjisub.launchpad.activity.PlayActivity>? = null
        try {
            File(root, "info").writeText("title=Color length test\nproducerName=UniPad test\nbuttonX=8\nbuttonY=8\nchain=2\nsquareButton=true\n")
            File(root, "keySound").writeText("1 1 1 silence.wav\n")
            File(root, "sounds").mkdir()
            val dataSize = 1600
            File(root, "sounds/silence.wav").writeBytes(ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
                put("RIFF".toByteArray()); putInt(36 + dataSize); put("WAVEfmt ".toByteArray())
                putInt(16); putShort(1); putShort(1); putInt(8000); putInt(16000); putShort(2); putShort(16)
                put("data".toByteArray()); putInt(dataSize)
            }.array())
            File(root, "keyLED").mkdir()
            File(root, "keyLED/1 1 1 1").writeText("o 1 1 00ff00\nd 5000\no 1 1 1234567\nd 20000\nf 1 1\n")
            scenario = screen.launch(com.kimjisub.launchpad.activity.PlayActivity::class.java) { putExtra("path", root.path) }
            screen.await("Pack did not load") { screen.ready() }
            if (screen.onMain { screen.vm().unipack.errorDetail != null }) {
                screen.node(By.text(screen.text(R.string.accept)))
                capture("jis73-$label-warning")
                screen.clickText(R.string.accept)
                assertTrue(screen.device.wait(Until.gone(By.text(screen.text(R.string.accept))), 5000))
            }
            screen.await("Pad grid did not lay out") { screen.padBounds().let { pads ->
                pads.size == 64 && pads.all { it.width() > 0 && it.height() > 0 }
            } }
            screen.tapPad(0)
            screen.await("Valid color did not light the pad") { screen.onMain {
                screen.vm().channelManager.get(0, 0)?.color == 0xff00ff00.toInt()
            } }
            SystemClock.sleep(5500)
            capture("jis73-$label-light")
            try {
                assertEquals(1, audio.plays.size)
                assertEquals("Overlong pad color replaced the valid light", 0xff00ff00.toInt(), screen.onMain {
                    val pad = screen.views(screen.resumed().window.decorView).filterIsInstance<PadView>().first()
                    (pad.findViewById<android.view.View>(com.kimjisub.design.R.id.led).background as ColorDrawable).color
                })
            } finally {
                val activity = screen.resumed()
                screen.clickDescription(R.string.menu)
                screen.clickDescription(R.string.quit)
                screen.await("Quit did not close the pack") { screen.onMain { activity.isFinishing || activity.isDestroyed } }
                capture("jis73-$label-exit")

            }
        } finally {
            configurator.waitForIdleTimeout = idleTimeout
            scenario?.close()
            unloadKoinModules(doubles)
            root.deleteRecursively()
        }
    }
}
