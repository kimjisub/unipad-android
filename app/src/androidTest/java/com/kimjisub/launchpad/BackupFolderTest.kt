package com.kimjisub.launchpad

import android.content.Intent
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.TransferActivity
import com.kimjisub.launchpad.manager.PreferenceManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/**
 * The backup folder (Documents/Unipad) in Settings > Storage and on the restore screen, chosen
 * through the real system folder picker, and the file pickers on devices that have none.
 *
 * Tests that run without a picker disable the apps that handle it and enable them again after.
 * A crash kills this process before that happens; enable them by hand then
 * (`pm enable --user 0 <package>`).
 */
@RunWith(AndroidJUnit4::class)
class BackupFolderTest : BaseUITest() {

    private val prefs by lazy { PreferenceManager(context) }

    @Before
    fun startWithoutBackupFolder() {
        forgetBackupFolder()
        device.executeShellCommand("rm -rf $BACKUP_DIR")
        device.executeShellCommand("mkdir -p $BACKUP_DIR")
    }

    @After
    fun removeBackupFolder() {
        forgetBackupFolder()
        device.executeShellCommand("rm -rf $BACKUP_DIR")
    }

    /** A saved folder whose permission is gone (deleted in a Files app) is asked for again. */
    @Test
    fun settingsAsksForTheFolderAgainWhenItsPermissionIsGone() {
        prefs.backupSafUri = UNIPAD_TREE
        launchToMainScreen()
        openStorageSettings()

        assertNotNull("Connect row is not offered", scrollToText(str(R.string.backup_grant_access)))
        assertNull("Disconnect is offered for a folder UniPad cannot open", device.findObject(By.text(str(R.string.backup_revoke))))
        takeScreenshot("backup_settings_permission_gone")
    }

    /**
     * Deleting Documents/Unipad in a Files app while Settings is open takes the permission away;
     * Disconnect then forgets the folder instead of crashing.
     */
    @Test
    fun disconnectAfterThePermissionIsGoneKeepsSettingsOpen() {
        launchToMainScreen()
        openStorageSettings()
        clickWhenShown(scrollToText(str(R.string.backup_grant_access)))
        pickInFolderPicker(BACKUP_FOLDER_NAME)

        assertNotNull("Documents/Unipad was not connected", scrollToText(str(R.string.backup_revoke)))
        assertTrue("Connected folder was not saved: ${prefs.backupSafUri}", prefs.backupSafUri.orEmpty().endsWith("%2F$BACKUP_FOLDER_NAME"))
        takeScreenshot("backup_settings_connected")

        deleteBackupFolderLikeAFilesApp()
        assertTrue("Deleting the folder left its permission", context.contentResolver.persistedUriPermissions.isEmpty())
        // Found while the content was still scrolling; tap where it came to rest.
        device.waitForIdle()
        clickWhenShown(device.wait(Until.findObject(By.pkg(PACKAGE_NAME).text(str(R.string.backup_revoke))), WAIT_MS))

        assertTrue("Saved folder was not forgotten", waitUntil(WAIT_MS) { prefs.backupSafUri == null })
        // The backup card shrinks to this one row, which takes the place of the rows above it.
        assertTrue(
            "Connect row did not come back",
            device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).text(str(R.string.backup_grant_access))), WAIT_MS),
        )
        assertTrue("Settings did not stay open", device.hasObject(By.pkg(PACKAGE_NAME).text(str(R.string.settings_storage))))
        takeScreenshot("backup_settings_disconnected")
    }

    /** Restore asks for the folder again when the saved one can no longer be opened, instead of listing nothing. */
    @Test
    fun restoreAsksForTheFolderAgainWhenItsPermissionIsGone() {
        prefs.backupSafUri = UNIPAD_TREE
        ActivityScenario.launch<TransferActivity>(restoreIntent()).use {
            assertNotNull("Grant access is not offered", device.wait(Until.findObject(grantAccessButton()), WAIT_MS))
            takeScreenshot("backup_restore_permission_gone")
        }
    }

    /** The restore screen refuses Documents itself like Settings does, so backups never land at its top level. */
    @Test
    fun restoreRefusesAFolderOtherThanUnipad() {
        ActivityScenario.launch<TransferActivity>(restoreIntent()).use { scenario ->
            clickWhenShown(device.wait(Until.findObject(grantAccessButton()), WAIT_MS))
            pickInFolderPicker(null)

            // The picker's answer reaches the screen before it resumes.
            assertTrue("Restore screen did not resume", waitUntil(WAIT_MS) { scenario.state == Lifecycle.State.RESUMED })
            assertNull("Documents was saved as the backup folder", prefs.backupSafUri)
            assertTrue(
                "Permission on Documents was kept",
                context.contentResolver.persistedUriPermissions.isEmpty(),
            )
            assertNotNull("Grant access is no longer offered", device.wait(Until.findObject(grantAccessButton()), WAIT_MS))
            takeScreenshot("backup_restore_wrong_folder")
        }
    }

    /** The restore screen accepts Documents/Unipad and lists what is in it. */
    @Test
    fun restoreAcceptsTheUnipadFolder() {
        device.executeShellCommand("mkdir -p $BACKUP_DIR/$BACKED_UP_PACK")
        ActivityScenario.launch<TransferActivity>(restoreIntent()).use {
            clickWhenShown(device.wait(Until.findObject(grantAccessButton()), WAIT_MS))
            pickInFolderPicker(BACKUP_FOLDER_NAME)

            // The picker listed the same name, so only UniPad's own screen counts.
            assertNotNull("Backed-up pack is not listed", device.wait(Until.findObject(By.pkg(PACKAGE_NAME).text(BACKED_UP_PACK)), WAIT_MS))
            assertTrue("Connected folder was not saved: ${prefs.backupSafUri}", prefs.backupSafUri.orEmpty().endsWith("%2F$BACKUP_FOLDER_NAME"))
            takeScreenshot("backup_restore_connected")
        }
    }

    @Test
    fun settingsWithoutFolderPickerReportsIt() = withoutFolderPicker {
        launchToMainScreen()
        openStorageSettings()
        clickWhenShown(scrollToText(str(R.string.backup_grant_access)))

        assertTrue(
            "No message that the device has no picker",
            device.wait(Until.hasObject(By.text(str(R.string.picker_unavailable))), WAIT_MS),
        )
        assertTrue("Settings did not stay open", device.hasObject(By.pkg(PACKAGE_NAME).text(str(R.string.settings_storage))))
        takeScreenshot("backup_settings_no_picker")
    }

    @Test
    fun restoreWithoutFolderPickerStaysOpen() = withoutFolderPicker {
        ActivityScenario.launch<TransferActivity>(restoreIntent()).use { scenario ->
            clickWhenShown(device.wait(Until.findObject(grantAccessButton()), WAIT_MS))
            assertStaysOpen()
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
            takeScreenshot("backup_restore_no_picker")
        }
    }

    @Test
    fun themeImportWithoutFilePickerStaysOpen() = withoutFilePicker {
        launchToMainScreen()
        openSettings()
        device.findObject(By.text(str(R.string.settings_theme))).click()
        clickWhenShown(device.wait(Until.findObject(By.desc(str(R.string.theme_add_title))), WAIT_MS))
        clickWhenShown(device.wait(Until.findObject(By.text(str(R.string.theme_add_zip))), WAIT_MS))

        assertStaysOpen()
        assertTrue("Theme screen did not stay open", device.hasObject(By.desc(str(R.string.theme_add_title))))
        takeScreenshot("theme_import_no_picker")
    }

    @Test
    fun packImportWithoutFilePickerStaysOpen() = withoutFilePicker {
        launchToMainScreen()
        clickWhenShown(device.wait(Until.findObject(By.desc(str(R.string.import_unipack))), WAIT_MS))

        assertStaysOpen()
        assertTrue("Main screen did not stay open", waitForMainScreen())
        takeScreenshot("pack_import_no_picker")
    }

    private fun openStorageSettings() {
        openSettings()
        device.findObject(By.text(str(R.string.settings_storage))).click()
        device.waitForIdle()
    }

    /** Scrolls the open settings content until [text] is shown. */
    private fun scrollToText(text: String): UiObject2? {
        device.wait(Until.findObject(By.text(text)), SHORT_WAIT_MS)?.let { return it }
        val content = device.wait(Until.findObject(By.scrollable(true).pkg(PACKAGE_NAME)), WAIT_MS) ?: return null
        return content.scrollUntil(Direction.DOWN, Until.findObject(By.text(text)))
    }

    private fun clickWhenShown(target: UiObject2?) {
        assertNotNull("Click target is not shown", target)
        target!!.click()
        device.waitForIdle()
    }

    /**
     * Uses the system folder picker, which opens at Documents: opens [folder] when given, then
     * "Use this folder" and the confirmation that follows.
     */
    private fun pickInFolderPicker(folder: String?) {
        assertTrue("Folder picker did not open", device.wait(Until.hasObject(By.pkg(DOCUMENTS_UI)), FILE_PICKER_TIMEOUT))
        if (folder != null) {
            clickInPicker(By.pkg(DOCUMENTS_UI).text(folder))
            assertTrue(
                "Picker did not open $folder",
                device.wait(Until.hasObject(By.pkg(DOCUMENTS_UI).res(BREADCRUMB).text(folder)), WAIT_MS),
            )
        }
        // "Use this folder", then "Allow" in the confirmation that follows.
        clickInPicker(confirmButton())
        assertTrue("No confirmation after Use this folder", device.wait(Until.hasObject(By.pkg(DOCUMENTS_UI).res("android:id/button2")), WAIT_MS))
        clickInPicker(confirmButton())
        assertTrue("Folder picker did not close", device.wait(Until.gone(By.pkg(DOCUMENTS_UI)), WAIT_MS))
        assertTrue("UniPad did not come back from the picker", device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), WAIT_MS))
    }

    /** The picker reloads its list after opening a folder, so a button found a moment ago can be replaced. */
    private fun clickInPicker(selector: BySelector) {
        repeat(STALE_RETRIES) { attempt ->
            val target = device.wait(Until.findObject(selector), WAIT_MS)
            assertNotNull("Not shown in the picker: $selector", target)
            try {
                target!!.click()
                device.waitForIdle()
                return
            } catch (e: StaleObjectException) {
                if (attempt == STALE_RETRIES - 1) throw e
            }
        }
    }

    private fun confirmButton(): BySelector = By.pkg(DOCUMENTS_UI).res("android:id/button1")

    private fun grantAccessButton(): BySelector = By.pkg(PACKAGE_NAME).text(str(R.string.migration_grant_access))

    private fun restoreIntent() = Intent(context, TransferActivity::class.java)
        .putExtra(TransferActivity.EXTRA_SOURCE_TYPE, "backup")

    /** The tap has had time to crash the app or open something else; neither may happen. */
    private fun assertStaysOpen() {
        device.waitForIdle()
        Thread.sleep(1500L)
        assertTrue("UniPad did not stay in front", device.hasObject(By.pkg(PACKAGE_NAME)))
    }

    private fun withoutFolderPicker(block: () -> Unit) =
        withoutHandlers("-a android.intent.action.OPEN_DOCUMENT_TREE", block = block)

    private fun withoutFilePicker(block: () -> Unit) = withoutHandlers(
        "-a android.intent.action.OPEN_DOCUMENT -c android.intent.category.OPENABLE -t */*",
        "-a android.intent.action.GET_CONTENT -c android.intent.category.OPENABLE -t application/zip",
        block = block,
    )

    /** Deleting through the documents provider, as a Files app does, also revokes every grant on the folder. */
    private fun deleteBackupFolderLikeAFilesApp() {
        val tree = prefs.backupSafUri!!.toUri()
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        assertTrue("Could not delete the backup folder", DocumentsContract.deleteDocument(context.contentResolver, folder))
    }

    private fun releaseHeldPermissions() {
        context.contentResolver.persistedUriPermissions.forEach {
            context.contentResolver.releasePersistableUriPermission(it.uri, PERMISSION_FLAGS)
        }
    }

    private fun forgetBackupFolder() {
        releaseHeldPermissions()
        prefs.backupSafUri = null
    }

    private companion object {
        const val WAIT_MS = 5_000L
        const val SHORT_WAIT_MS = 1_000L
        const val STALE_RETRIES = 3
        const val BACKUP_FOLDER_NAME = "Unipad"
        const val BACKUP_DIR = "/sdcard/Documents/$BACKUP_FOLDER_NAME"
        const val BACKED_UP_PACK = "zz_backup_folder_pack"
        const val UNIPAD_TREE = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FUnipad"
        const val PERMISSION_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val DOCUMENTS_UI: Pattern = Pattern.compile(".*documentsui")
        val BREADCRUMB: Pattern = Pattern.compile(".*:id/breadcrumb_text")
    }
}
