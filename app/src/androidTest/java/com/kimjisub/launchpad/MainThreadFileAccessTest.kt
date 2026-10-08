package com.kimjisub.launchpad

import android.content.Intent
import android.os.StrictMode
import android.os.strictmode.Violation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.SettingsActivity
import com.kimjisub.launchpad.activity.ThemeActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * File work on the main thread freezes the screen on slow storage and became ANRs in the field
 * (Crashlytics 3bb8b290: moving the old "Unipad" folder on resume). Each test drives a screen that
 * used to list workspaces, count packs, delete a theme or re-read the autoPlay file on the main
 * thread, and fails if StrictMode sees disk access there from those places.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 28) // StrictMode.ThreadPolicy.Builder.penaltyListener
class MainThreadFileAccessTest : BaseUITest() {

    private val violations = CopyOnWriteArrayList<String>()
    private val cleanup = mutableListOf<File>()

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX)
        }
        cleanup.forEach { it.deleteRecursively() }
    }

    @Test
    fun mainScreenMovesTheOldUnipadFolderOffTheMainThread() {
        val externalDir = requireNotNull(context.getExternalFilesDir(null))
        val legacyDir = File(externalDir, "Unipad")
        val movedPack = File(File(externalDir, "UniPack"), LEGACY_PACK)
        cleanup += listOf(legacyDir, movedPack)
        TestUniPack.folder(context).copyRecursively(File(legacyDir, LEGACY_PACK), overwrite = true)

        watchMainThread()
        launchToMainScreen()

        val row = By.res("main_pack_$LEGACY_PACK")
        val listed = device.wait(Until.hasObject(row), MAIN_TIMEOUT) ||
            device.findObject(By.scrollable(true))?.scrollUntil(Direction.DOWN, Until.findObject(row)) != null
        assertTrue("Pack moved out of the old Unipad folder is not listed", listed)
        assertFalse("Old Unipad folder still holds the pack", File(legacyDir, LEGACY_PACK).exists())
        assertNoWatchedViolations()
    }

    @Test
    fun storageSettingsAndTransferListWorkspacesOffTheMainThread() {
        watchMainThread()
        context.startActivity(
            Intent(context, SettingsActivity::class.java)
                .putExtra(SettingsActivity.EXTRA_INITIAL_CATEGORY, SettingsActivity.CATEGORY_STORAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        val transfer = By.text(str(R.string.transfer_button))
        assertNotNull("Storage settings did not list a workspace", device.wait(Until.findObject(transfer), LAUNCH_TIMEOUT))

        assertTrue("Transfer screen did not open", clickFresh { device.findObject(transfer) })
        assertTrue(
            "Transfer screen did not list its targets",
            device.wait(Until.hasObject(By.text(str(R.string.workspace_backup_documents))), LAUNCH_TIMEOUT)
        )

        // Returning recounts the packs of every workspace.
        device.pressBack()
        assertTrue("Did not return to storage settings", device.wait(Until.hasObject(transfer), LAUNCH_TIMEOUT))
        device.waitForIdle()
        assertNoWatchedViolations()
    }

    @Test
    fun deletingAZipThemeRemovesItsFolderOffTheMainThread() {
        val themeDir = File(File(context.getExternalFilesDir(null), "themes"), THEME_FOLDER)
        cleanup += themeDir
        themeDir.mkdirs()
        File(themeDir, "theme.json").writeText("""{"name": "$THEME_NAME", "author": "UniPad UI Test", "version": "1.0.0"}""")

        watchMainThread()
        context.startActivity(
            Intent(context, ThemeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        val theme = device.wait(Until.findObject(By.text(THEME_NAME)), LAUNCH_TIMEOUT)
        assertNotNull("Test theme is not listed", theme)
        theme!!.longClick()
        assertTrue("Delete was not offered", clickFresh { device.findObject(By.text(str(R.string.delete))) })

        assertTrue("Test theme is still listed", device.wait(Until.gone(By.text(THEME_NAME)), LAUNCH_TIMEOUT))
        assertFalse("Test theme folder was not deleted", themeDir.exists())
        assertNoWatchedViolations()
    }

    @Test
    fun autoMappingReadsTheAutoPlayFileOffTheMainThread() {
        launchToMainScreen()
        openTestPackInPlay()
        openPlayOptions()
        val autoMapping = By.text(AUTO_MAPPING)
        val row = device.wait(Until.findObject(autoMapping), 1000L)
            ?: device.findObject(By.res("play_options"))?.scrollUntil(Direction.DOWN, Until.findObject(autoMapping))
        assertNotNull("Auto Mapping is not offered", row)

        watchMainThread()
        row!!.click()
        assertTrue("Auto Mapping did not start", device.wait(Until.hasObject(By.text(AUTO_MAPPING_RUNNING)), LAUNCH_TIMEOUT))
        assertTrue("Auto Mapping did not finish", device.wait(Until.gone(By.text(AUTO_MAPPING_RUNNING)), AUTO_MAPPING_TIMEOUT))

        assertTrue("Auto Mapping is not offered again", device.wait(Until.hasObject(autoMapping), LAUNCH_TIMEOUT))
        assertNoWatchedViolations()
    }

    private fun watchMainThread() {
        val executor = Executors.newSingleThreadExecutor()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .penaltyListener(executor) { record(it) }
                    .build()
            )
        }
    }

    private fun record(violation: Violation) {
        val frame = violation.stackTrace.firstOrNull { frame ->
            WATCHED.any { "${frame.className}.${frame.methodName}".startsWith(it) }
        } ?: return
        violations += "${violation.javaClass.simpleName} at $frame"
    }

    private fun assertNoWatchedViolations() {
        // The listener runs on its own executor; give the last reports time to arrive.
        Thread.sleep(500)
        assertEquals("Disk access on the main thread", emptyList<String>(), violations.toList())
    }

    private companion object {
        const val LEGACY_PACK = "zz_ui_legacy_unipad_pack"
        const val THEME_FOLDER = "zz_ui_test_theme"
        const val THEME_NAME = "UI Test Theme"
        const val AUTO_MAPPING = "Auto Mapping"
        const val AUTO_MAPPING_RUNNING = "Auto Mapping…"
        const val AUTO_MAPPING_TIMEOUT = 60_000L

        val WATCHED = listOf(
            "com.kimjisub.launchpad.manager.WorkspaceManager.",
            "com.kimjisub.launchpad.tool.ZipThemeImporter.delete",
            "com.kimjisub.launchpad.unipack.UniPackFolder.reloadAutoPlay",
        )
    }
}
