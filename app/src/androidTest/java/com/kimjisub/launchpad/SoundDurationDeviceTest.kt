package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.basefeatures.FeatureScreen
import com.kimjisub.launchpad.tool.AudioTrackDurationReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SoundDurationDeviceTest : BaseUITest() {
    private fun mixedSounds(): File {
        val root = TestUniPack.folder(context)
        val sound = File(root, "sounds")
        File(sound, "empty.wav").writeBytes(byteArrayOf())
        File(sound, "header.wav").writeBytes(File(sound, "silence.wav").readBytes().take(16).toByteArray())
        File(sound, "text.mp3").writeText("This is text, not audio.")
        return root
    }

    @Test
    fun everyCorruptFileIsFollowedByAReadableSound() {
        val root = mixedSounds()
        AudioTrackDurationReader().use { reader ->
            for (name in listOf("empty.wav", "header.wav", "text.mp3")) {
                assertNull(name, reader.durationMs(File(root, "sounds/$name")))
                assertEquals(100.0, checkNotNull(reader.durationMs(File(root, "sounds/silence.wav"))), 0.001)
            }
        }
    }

    @Test
    fun fittingAMixedPackKeepsCorruptWaitsAndUpdatesTheFollowingSounds() {
        launchToMainScreen()
        val root = mixedSounds()
        val names = listOf("empty.wav", "silence.wav", "header.wav", "silence.wav", "text.mp3", "silence.wav", "silence.wav")
        File(root, "keySound").writeText(names.mapIndexed { i, name -> "1 1 ${i + 1} $name\n" }.joinToString(""))
        val original = "t 1 1\nd 47\nt 1 2\nd 67\nt 1 3\nd 89\nt 1 4\nd 101\nt 1 5\nd 131\nt 1 6\nd 149\nt 1 7\n"
        val file = File(root, "autoPlay").apply { writeText(original) }
        openTestPackInPlay()
        openPlayOptions()
        takeScreenshot("mixed_duration_before")
        val selector = By.text(str(R.string.autoMapping))
        val fitRow = device.wait(Until.findObject(selector), 2000L)
            ?: device.findObject(By.res("play_options"))?.scrollUntil(Direction.DOWN, Until.findObject(selector))
        checkNotNull(fitRow) { "Fit autoplay row missing" }.click()
        assertTrue("Confirmation missing", device.wait(Until.hasObject(By.text("Fit")), 5000L))
        device.findObject(By.text("Fit")).click()
        val screen = FeatureScreen()
        assertTrue("Mapping did not save", waitUntil(30_000L) {
            file.readText() != original && screen.onMain { !screen.vm().autoMappingActive }
        })
        assertEquals("d 1000\nt 1 1\nd 47\nt 1 2\nd 100\nt 1 3\nd 89\nt 1 4\nd 100\nt 1 5\nd 131\nt 1 6\nd 100\nt 1 7\n", file.readText())
        assertEquals(original, File(root, "autoPlay_previous").readText())
        assertTrue("Play screen closed", screen.resumed() is com.kimjisub.launchpad.activity.PlayActivity)
        takeScreenshot("mixed_duration_after")
    }
}
