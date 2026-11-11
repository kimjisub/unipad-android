package com.kimjisub.launchpad

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.Surface
import com.kimjisub.launchpad.basefeatures.FeatureScreen
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.activity.SplashActivity
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

    /**
     * Mapping reads each sound file's length once, so presses of one file finish at once. Pad 1 1
     * of chain 1 cycles through [SOUND_FILES] silent copies instead, each needing its own media
     * read; the mapping is still reading when the test leaves the app.
     */
    private fun cycleFirstPadThroughSoundFiles() {
        val root = TestUniPack.folder(context)
        val silence = File(root, "sounds/silence.wav")
        val keySound = File(root, "keySound")
        val others = keySound.readLines().filter { it != "1 1 1 silence.wav" }
        val firstPad = (0 until SOUND_FILES).map { i ->
            silence.copyTo(File(root, "sounds/silence_$i.wav"), overwrite = true)
            "1 1 1 silence_$i.wav"
        }
        keySound.writeText((firstPad + others).joinToString("\n", postfix = "\n"))
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
    fun returningThroughTheLauncherIcon_savesWhileThePackListCoversPlay() {
        // An explicit open has a different task-root intent from the launcher. Returning through
        // the real icon then opens a new list above PlayActivity instead of resuming Play directly.
        launchToMainScreen(Intent(context, SplashActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val file = File(TestUniPack.folder(context), "autoPlay")
        val input = "t 1 1\nd 50\n".repeat(3000)
        file.writeText(input)
        cycleFirstPadThroughSoundFiles()
        openTestPackInPlay()
        openPlayOptions()
        val screen = FeatureScreen()
        val vm = screen.vm()
        clickRow(str(R.string.autoMapping))
        clickRow("Fit")
        assertTrue("Mapping did not start", waitUntil(30_000L) {
            screen.onMain { vm.autoMappingActive && vm.autoMappingProgress > 0 }
        })
        SystemClock.sleep(3000)
        assertTrue("The input must still be mapping before leaving", screen.onMain { vm.autoMappingActive })
        assertEquals("Mapping must not have saved before leaving", input, file.readText())
        takeScreenshot("automapping_before_launcher_return")
        device.pressHome()
        assertTrue("Home did not hide PlayActivity", waitUntil(10_000L) {
            screen.onMain { !vm.screenVisible }
        })
        // Use the real launcher icon: a bare activity intent does not reproduce its task flags.
        // UiDevice's package-manager lookup can name Settings instead of the actual home app.
        val homeActivity = device.executeShellCommand(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
        ).trim()
        val launcherPackage = homeActivity.lineSequence().last().substringBefore('/')
        assertTrue("Could not resolve the default home activity: $homeActivity", '/' in homeActivity)
        // Refresh accessibility after the app/launcher window transition, as FeatureScreen does
        // for other window changes. The launcher need not be the root of the entire window tree.
        val launcher = screen.node(By.pkg(launcherPackage))
        val bounds = launcher.visibleBounds
        device.swipe(bounds.centerX(), bounds.bottom - bounds.height() / 10,
            bounds.centerX(), bounds.top + bounds.height() / 4, 20)
        val label = context.applicationInfo.loadLabel(context.packageManager).toString()
        val selector = By.pkg(launcherPackage).text(label)
        if (!screen.has(selector)) {
            screen.node(By.pkg(launcherPackage).scrollable(true))
                .scrollUntil(Direction.DOWN, Until.findObject(selector))
        }
        val icon = screen.node(selector)
        takeScreenshot("automapping_launcher_icon")
        icon.click()
        screen.awaitActivity(MainActivity::class.java)
        takeScreenshot("automapping_list_after_launcher_return")
        assertTrue("The pack list must cover the same PlayActivity", screen.onMain {
            !vm.screenVisible
        })
        assertTrue("Mapping did not save behind the pack list", waitUntil(120_000L) {
            screen.onMain { !vm.autoMappingActive }
        })
        assertTrue(screen.resumed() is MainActivity)
        assertEquals("d 1000\n" + "t 1 1\nd 100\n".repeat(2999) + "t 1 1\n", file.readText())
        assertEquals(input, File(file.parentFile, "autoPlay_previous").readText())
        takeScreenshot("automapping_saved_behind_list")
    }

    @Test
    fun lockingAndRotatingDuringMapping_resumesAndSavesAfterUnlock() {
        launchToMainScreen()
        val file = File(TestUniPack.folder(context), "autoPlay")
        val input = "t 1 1\nd 50\n".repeat(1000)
        file.writeText(input)
        cycleFirstPadThroughSoundFiles()
        openTestPackInPlay()
        openPlayOptions()
        val screen = FeatureScreen()
        val vm = screen.vm()
        clickRow(str(R.string.autoMapping))
        clickRow("Fit")
        assertTrue("Mapping did not start", waitUntil(30_000L) {
            screen.onMain { vm.autoMappingActive && vm.autoMappingProgress > 0 }
        })
        takeScreenshot("automapping_before_lock")
        val activity = screen.resumed()
        val orientation = screen.onMain { activity.requestedOrientation }
        try {
            device.sleep()
            assertTrue("Lock did not hide the play screen", waitUntil(10_000L) {
                screen.onMain { !vm.screenVisible }
            })
            // The duration read already in flight may finish before the next read waits.
            val paused = screen.onMain { vm.autoMappingProgress }
            SystemClock.sleep(1000)
            screen.onMain {
                assertTrue("Mapping must remain pending while locked", vm.autoMappingActive)
                assertTrue("Only the in-flight read may finish behind the lock screen", vm.autoMappingProgress <= paused + 2)
            }
            assertEquals(input, file.readText())

            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            assertTrue("Unlock did not resume mapping", waitUntil(30_000L) {
                screen.onMain { vm.screenVisible && vm.autoMappingProgress > paused }
            })
            takeScreenshot("automapping_after_unlock")
            val rotation = device.displayRotation
            screen.onMain {
                activity.requestedOrientation = if (rotation == Surface.ROTATION_270)
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            }
            assertTrue("Rotation did not keep mapping on the play screen", waitUntil(10_000L) {
                device.displayRotation != rotation && screen.onMain { vm.screenVisible && vm.autoMappingActive }
            })
            takeScreenshot("automapping_after_rotation")
            assertTrue("Mapping saved notice missing", device.wait(
                Until.hasObject(By.text(str(R.string.autoMappingSaved))), 120_000L,
            ))
            takeScreenshot("automapping_saved_after_unlock_and_rotation")
            assertTrue("Mapping remained active after saving", waitUntil(10_000L) {
                screen.onMain { !vm.autoMappingActive }
            })
            assertEquals("d 1000\n" + "t 1 1\nd 100\n".repeat(999) + "t 1 1\n", file.readText())
            assertEquals(input, File(file.parentFile, "autoPlay_previous").readText())
        } finally {
            device.wakeUp()
            device.executeShellCommand("wm dismiss-keyguard")
            screen.onMain { activity.requestedOrientation = orientation }
        }
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

    private companion object {
        const val SOUND_FILES = 500
    }
}
