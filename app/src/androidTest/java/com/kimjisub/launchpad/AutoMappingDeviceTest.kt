package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AutoMappingDeviceTest : BaseUITest() {
    private val confirmation = "Fit the autoplay? Waits will match sound lengths. Your current autoplay will be backed up. Simultaneous pads stay together."
    private val restoreLabel = "Restore previous autoplay"
    private val original = "d 20\non 1 1\non 2 1\nd 50\non 3 1\nd 99\n"

    private fun enterPack() {
        launchToMainScreen()
        File(TestUniPack.folder(context), "autoPlay").writeText(original)
        openTestPackInPlay()
        openPlayOptions()
    }

    private fun clickRow(label: String) {
        val selector = By.text(label)
        val row = device.wait(Until.findObject(selector), 2000L)
            ?: device.findObject(By.res("play_options"))?.scrollUntil(Direction.DOWN, Until.findObject(selector))
        assertNotNull("Menu row missing: $label", row)
        row!!.click()
    }

    @Test
    fun confirmingAndRestoringKeepsBothVersionsAcrossReopening() {
        enterPack()
        takeScreenshot("automapping_before_menu")
        clickRow(str(R.string.autoMapping))
        takeScreenshot("automapping_confirmation")
        assertTrue("Mapping must ask before changing the pack", device.wait(Until.hasObject(By.text(confirmation)), 5000L))
        assertEquals(original, File(TestUniPack.folder(context), "autoPlay").readText())
        clickRow("Fit")
        val file = File(TestUniPack.folder(context), "autoPlay")
        assertTrue("Mapping never saved", waitUntil(30_000L) { file.readText() != original })
        assertTrue("Mapping did not finish", waitUntil(30_000L) { device.hasObject(By.text(str(R.string.autoMapping))) })
        val mapped = file.readText()
        assertTrue(mapped, mapped.startsWith("d 1000\nt 1 1\nt 2 1\nd 100\nt 3 1"))
        takeScreenshot("automapping_saved")
        quitPlayToMain()
        openTestPackInPlay()
        openPlayOptions()
        clickRow(restoreLabel)
        takeScreenshot("automapping_restore_confirmation")
        clickRow("Restore")
        assertTrue("Restore did not persist", waitUntil(30_000L) { file.readText() == original })
        assertTrue("Restore did not finish", waitUntil(30_000L) { device.hasObject(By.text(str(R.string.autoMapping))) })
        clickRow(restoreLabel)
        clickRow("Restore")
        assertTrue("Second restore did not swap back", waitUntil(30_000L) { file.readText() == mapped })
        takeScreenshot("automapping_restored_again")
    }

    @Test
    fun cancellingKeepsTheOriginalAndExplainsMissingBackup() {
        enterPack()
        clickRow(str(R.string.autoMapping))
        assertTrue("Mapping confirmation missing", device.wait(Until.hasObject(By.text(confirmation)), 5000L))
        clickRow(str(R.string.cancel))
        assertEquals(original, File(TestUniPack.folder(context), "autoPlay").readText())
        assertTrue("No-backup explanation missing", device.wait(Until.hasObject(By.text("No saved backup")), 5000L))
        assertTrue("Restore must be disabled", !device.findObject(By.text(restoreLabel)).isEnabled)
        takeScreenshot("automapping_cancelled_no_backup")
    }
}
