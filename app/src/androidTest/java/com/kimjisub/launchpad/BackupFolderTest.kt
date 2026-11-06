package com.kimjisub.launchpad

import android.content.Intent
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
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
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.tool.SafMigrationHelper
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.regex.Pattern

/**
 * The backup folder (Documents/Unipad) in Settings > Storage and on the restore screen, chosen
 * through the real system folder picker, and the file pickers on devices that have none.
 * Settings exposes SAF backup controls from Android 11; on Android 10 those cases check the
 * legacy workspace screen and the absence of those controls instead.
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
        device.executeShellCommand("mkdir -p $BACKUP_DIR/$BACKED_UP_PACK")
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

        checkBackupSettings {
            assertNotNull("Connect row is not offered", scrollToText(str(R.string.backup_grant_access)))
            assertNull("Disconnect is offered for a folder UniPad cannot open", device.findObject(By.text(str(R.string.backup_revoke))))
            takeScreenshot("backup_settings_permission_gone")
        }
    }

    /**
     * Deleting Documents/Unipad in a Files app while Settings is open takes the permission away;
     * Disconnect then forgets the folder instead of crashing.
     */
    @Test
    fun disconnectAfterThePermissionIsGoneKeepsSettingsOpen() {
        launchToMainScreen()
        openStorageSettings()
        checkBackupSettings {
            clickWhenShown(scrollToText(str(R.string.backup_grant_access)))
            pickInFolderPicker(BACKUP_FOLDER_NAME)

            assertConnectedBackupFolder()
            // The known child used to verify picker entry also triggers the recovery offer.
            val recoveryTitle = By.pkg(PACKAGE_NAME).text(str(R.string.backup_recovery_title))
            assertNotNull("Recovery offer is not shown", device.wait(Until.findObject(recoveryTitle), WAIT_MS))
            assertTrue(
                "Recovery offer does not count the backed-up pack",
                device.hasObject(By.pkg(PACKAGE_NAME).text(AppLanguage.localized(context).getString(R.string.backup_recovery_message, 1))),
            )
            takeScreenshot("backup_settings_recovery_notice")
            clickWhenShown(device.wait(Until.findObject(By.pkg(PACKAGE_NAME).text(str(R.string.backup_recovery_later))), WAIT_MS))
            assertTrue("Recovery offer did not close", device.wait(Until.gone(recoveryTitle), WAIT_MS))

            assertNotNull("Documents/Unipad was not connected", scrollToText(str(R.string.backup_revoke)))
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
    }

    /**
     * What a killed export left in the backup folder is not a pack in Settings: neither in the
     * restore offer after connecting nor in the folder's count, which is 0 once only those remain.
     */
    @Test
    fun settingsCountsOnlyFinishedBackups() {
        device.executeShellCommand("mkdir -p $BACKUP_DIR/$KILLED_EXPORT")
        device.executeShellCommand("touch $BACKUP_DIR/$KILLED_EXPORT/info $BACKUP_DIR/$KILLED_EXPORT_ZIP")
        launchToMainScreen()
        openStorageSettings()
        checkBackupSettings {
            clickWhenShown(scrollToText(str(R.string.backup_grant_access)))
            pickInFolderPicker(BACKUP_FOLDER_NAME)

            val recoveryTitle = By.pkg(PACKAGE_NAME).text(str(R.string.backup_recovery_title))
            assertNotNull("Recovery offer is not shown", device.wait(Until.findObject(recoveryTitle), WAIT_MS))
            assertEquals(
                "Recovery offer counts what a killed export left",
                counted(R.string.backup_recovery_message, 1),
                device.findObject(By.pkg(PACKAGE_NAME).text(anyCount(R.string.backup_recovery_message)))?.text,
            )
            clickWhenShown(device.wait(Until.findObject(By.pkg(PACKAGE_NAME).text(str(R.string.backup_recovery_later))), WAIT_MS))
            assertTrue("Recovery offer did not close", device.wait(Until.gone(recoveryTitle), WAIT_MS))
            assertTrue("Backup folder count is not 1", waitUntil(WAIT_MS) { backupFolderCount() == counted(R.string.workspace_unipack_count, 1) })

            device.executeShellCommand("rm -rf $BACKUP_DIR/$BACKED_UP_PACK")
            // Settings counts again when it comes back to the front.
            clickWhenShown(scrollToText(str(R.string.backup_restore_button)))
            assertTrue("Restore did not open", device.wait(Until.gone(By.pkg(PACKAGE_NAME).text(BACKUP_ROW_TITLE)), WAIT_MS))
            device.pressBack()
            assertTrue(
                "A folder holding only a killed export is counted as ${backupFolderCount()}",
                waitUntil(WAIT_MS) { backupFolderCount() == counted(R.string.workspace_unipack_count, 0) },
            )
            takeScreenshot("backup_settings_only_killed_export")
        }
    }

    private fun counted(resId: Int, count: Int): String = AppLanguage.localized(context).getString(resId, count)

    /** [resId]'s text with any count in its one `%d`. */
    private fun anyCount(resId: Int): Pattern =
        Pattern.compile(counted(resId, COUNT_MARK).split(COUNT_MARK.toString()).joinToString("\\d+") { Pattern.quote(it) })

    /** The pack count shown under the backup folder's row in Settings. */
    private fun backupFolderCount(): String? = try {
        val title = scrollToText(BACKUP_ROW_TITLE)?.visibleBounds
        title?.let { row ->
            device.findObjects(By.pkg(PACKAGE_NAME).text(anyCount(R.string.workspace_unipack_count)))
                .filter { it.visibleBounds.top >= row.bottom && it.visibleBounds.left == row.left }
                .minByOrNull { it.visibleBounds.top }
                ?.text
        }
    } catch (e: StaleObjectException) {
        null
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
        ActivityScenario.launch<TransferActivity>(restoreIntent()).use {
            clickWhenShown(device.wait(Until.findObject(grantAccessButton()), WAIT_MS))
            pickInFolderPicker(BACKUP_FOLDER_NAME)

            // The picker listed the same name, so only UniPad's own screen counts.
            assertNotNull("Backed-up pack is not listed", device.wait(Until.findObject(By.pkg(PACKAGE_NAME).text(BACKED_UP_PACK)), WAIT_MS))
            assertConnectedBackupFolder()
            takeScreenshot("backup_restore_connected")
        }
    }

    /**
     * Moves into Documents/Unipad through the system documents provider: each backup ends up under
     * exactly the pack's name and its source is removed, what a killed export left is neither
     * listed nor kept, and a backup the provider has to save under another name keeps its source.
     */
    @Test
    fun movesIntoTheConnectedFolderTakeThePackName() {
        device.executeShellCommand("mkdir -p $BACKUP_DIR/$KILLED_EXPORT")
        device.executeShellCommand("touch $BACKUP_DIR/$KILLED_EXPORT/info $BACKUP_DIR/.someone-else $BACKUP_DIR/$CLASHING_PACK")
        ActivityScenario.launch<TransferActivity>(restoreIntent()).use {
            clickWhenShown(device.wait(Until.findObject(grantAccessButton()), WAIT_MS))
            pickInFolderPicker(BACKUP_FOLDER_NAME)
            assertNotNull("Backed-up pack is not listed", device.wait(Until.findObject(By.pkg(PACKAGE_NAME).text(BACKED_UP_PACK)), WAIT_MS))
            assertFalse("A killed export is listed for restore", device.hasObject(By.pkg(PACKAGE_NAME).textStartsWith(".unipad-partial-")))
        }
        val folder = requireNotNull(DocumentFile.fromTreeUri(context, prefs.backupSafUri!!.toUri()))
        val workspace = File(context.getExternalFilesDir(null), "backup-provider-${UUID.randomUUID()}").apply { check(mkdir()) }
        try {
            val helper = SafMigrationHelper(context)
            fun pack(name: String) = File(workspace, name).apply {
                check(mkdirs())
                File(this, "info").writeText("title=$name")
                File(this, "sounds").mkdir()
                File(this, "sounds/a.wav").writeText("sound of $name")
            }
            val folderPack = pack("zz_provider_folder")
            val folderResult = runBlocking { helper.transferFileToSaf(listOf(folderPack), folder, true) { _, _, _ -> } }
            val zipPack = pack("zz_provider_zip")
            val zipResult = runBlocking { helper.transferFileToSafZip(listOf(zipPack), folder, true) { _, _, _ -> } }
            val clashingPack = pack(CLASHING_PACK)
            val clashResult = runBlocking { helper.transferFileToSaf(listOf(clashingPack), folder, true) { _, _, _ -> } }
            val names = folder.listFiles().mapNotNull { it.name }.sorted()
            println("Provider backups api=${Build.VERSION.SDK_INT} folder=$folderResult zip=$zipResult clash=$clashResult names=$names")

            assertEquals(SafMigrationHelper.TransferResult(1, 1, 0, 0, emptyList<String>()), folderResult)
            assertFalse("Folder move kept its source", folderPack.exists())
            val folderBackup = requireNotNull(folder.findFile("zz_provider_folder"))
            assertEquals("title=zz_provider_folder", readText(requireNotNull(folderBackup.findFile("info"))))
            assertEquals("sound of zz_provider_folder", readText(requireNotNull(folderBackup.findFile("sounds")?.findFile("a.wav"))))

            assertEquals(SafMigrationHelper.TransferResult(1, 1, 0, 0, emptyList<String>()), zipResult)
            assertFalse("ZIP move kept its source", zipPack.exists())
            val archive = File(context.cacheDir, "provider-backup.zip")
            try {
                context.contentResolver.openInputStream(requireNotNull(folder.findFile("zz_provider_zip.zip")).uri)!!
                    .use { input -> archive.outputStream().use { input.copyTo(it) } }
                ZipFile(archive).use { zip ->
                    assertEquals("sound of zz_provider_zip", zip.getInputStream(zip.getFileHeader("zz_provider_zip/sounds/a.wav")).bufferedReader().use { it.readText() })
                }
            } finally {
                archive.delete()
            }

            assertEquals(1, clashResult.failed)
            assertEquals("title=$CLASHING_PACK", File(clashingPack, "info").readText())
            assertEquals("sound of $CLASHING_PACK", File(clashingPack, "sounds/a.wav").readText())

            assertTrue("Someone else's file was removed", ".someone-else" in names)
            assertEquals("Partial entries are left: $names", emptyList<String>(), names.filter { it.startsWith(".unipad-partial-") })
        } finally {
            FileManager.deleteDirectory(workspace)
        }
    }

    private fun readText(document: DocumentFile): String =
        context.contentResolver.openInputStream(document.uri)!!.bufferedReader().use { it.readText() }

    @Test
    fun settingsWithoutFolderPickerReportsIt() = withoutFolderPicker {
        launchToMainScreen()
        openStorageSettings()
        checkBackupSettings {
            clickWhenShown(scrollToText(str(R.string.backup_grant_access)))

            assertTrue(
                "No message that the device has no picker",
                device.wait(Until.hasObject(By.text(str(R.string.picker_unavailable))), WAIT_MS),
            )
            assertTrue("Settings did not stay open", device.hasObject(By.pkg(PACKAGE_NAME).text(str(R.string.settings_storage))))
            takeScreenshot("backup_settings_no_picker")
        }
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
    private fun scrollToText(text: String): UiObject2? = scrollTo(By.pkg(PACKAGE_NAME).text(text))

    private fun scrollTo(selector: BySelector): UiObject2? {
        device.wait(Until.findObject(selector), SHORT_WAIT_MS)?.let { return it }
        val content = device.wait(Until.findObject(By.scrollable(true).pkg(PACKAGE_NAME)), WAIT_MS) ?: return null
        return content.scrollUntil(Direction.DOWN, Until.findObject(selector))
    }

    /** SAF backup settings start at Android 11; Android 10 must keep its legacy workspace UI. */
    private fun checkBackupSettings(check: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            check()
        } else {
            assertNotNull("App Storage workspace is not shown", scrollToText(str(R.string.workspace_app_storage)))
            val backupControls = Pattern.compile(listOf(R.string.backup_grant_access, R.string.backup_revoke)
                .joinToString("|") { Pattern.quote(str(it)) })
            assertNull("Android 10 must not offer SAF backup settings", scrollTo(By.pkg(PACKAGE_NAME).text(backupControls)))
            assertTrue("Settings did not stay open", device.hasObject(By.pkg(PACKAGE_NAME).text(str(R.string.settings_storage))))
            takeScreenshot("backup_settings_legacy_storage")
        }
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
            val folderEntry = By.pkg(DOCUMENTS_UI).text(folder)
            val contents = By.pkg(DOCUMENTS_UI).text(BACKED_UP_PACK)
            // Only a loaded Documents list can show that the child is absent there.
            assertTrue("Documents did not list $folder", device.wait(Until.hasObject(folderEntry), WAIT_MS))
            assertFalse("Backup contents must not be in the parent folder", device.hasObject(contents))
            clickInPicker(folderEntry)
            // Android 10 uses a toolbar title instead of breadcrumb_text. The known child,
            // absent in Documents above, proves the picker actually entered our folder.
            assertTrue(
                "Picker did not open $folder",
                device.wait(Until.hasObject(contents), WAIT_MS),
            )
            takeScreenshot("backup_picker_inside_unipad")
        }
        // "Use this folder", then "Allow" in the confirmation that follows.
        clickInPicker(confirmButton())
        assertTrue("No confirmation after Use this folder", device.wait(Until.hasObject(By.pkg(DOCUMENTS_UI).res("android:id/button2")), WAIT_MS))
        clickInPicker(confirmButton())
        assertTrue("Folder picker did not close", device.wait(Until.gone(By.pkg(DOCUMENTS_UI)), WAIT_MS))
        assertTrue("UniPad did not come back from the picker", device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), WAIT_MS))
    }

    private fun assertConnectedBackupFolder() {
        val saved = prefs.backupSafUri
        assertNotNull("Connected folder was not saved", saved)
        val uri = saved!!.toUri()
        assertEquals("com.android.externalstorage.documents", uri.authority)
        // Documents is exposed as the home root on Android 10 and under primary on newer OSes.
        val documentId = DocumentsContract.getTreeDocumentId(uri)
        assertTrue("Unexpected backup folder: $uri", documentId in setOf("home:Unipad", "primary:Documents/Unipad"))
        val permission = context.contentResolver.persistedUriPermissions.singleOrNull { it.uri == uri }
        assertNotNull("No lasting permission on $uri", permission)
        assertTrue("Read/write permission was not granted", permission!!.isReadPermission && permission.isWritePermission)
        val directory = DocumentFile.fromTreeUri(context, uri)
        assertNotNull("Connected folder cannot be opened", directory)
        assertEquals(BACKUP_FOLDER_NAME, directory!!.name)
        assertTrue("Connected folder is not readable and writable", directory.isDirectory && directory.canRead() && directory.canWrite())
        assertTrue("Connected folder does not contain the backup", directory.findFile(BACKED_UP_PACK)?.isDirectory == true)
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
        const val CLASHING_PACK = "zz_provider_clash"
        const val KILLED_EXPORT = ".unipad-partial-00000000-0000-0000-0000-000000000000"
        const val KILLED_EXPORT_ZIP = ".unipad-partial-11111111-1111-1111-1111-111111111111.zip"
        const val BACKUP_ROW_TITLE = "Documents/Unipad"
        const val COUNT_MARK = 987_654
        const val UNIPAD_TREE = "content://com.android.externalstorage.documents/tree/primary%3ADocuments%2FUnipad"
        const val PERMISSION_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val DOCUMENTS_UI: Pattern = Pattern.compile(".*documentsui")
    }
}
