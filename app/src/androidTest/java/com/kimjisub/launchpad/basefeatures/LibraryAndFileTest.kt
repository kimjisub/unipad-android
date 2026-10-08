package com.kimjisub.launchpad.basefeatures

import android.os.StrictMode
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.db.AppDatabase
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryAndFileTest {
    private lateinit var screen: FeatureScreen
    @Before fun setUp() {
        screen = FeatureScreen()
        screen.assertDedicatedWorkspace()
        screen.cleanPacks()
    }
    @After fun tearDown() { screen.close() }

    @Test fun launcherEmptyLibrarySettingsThenPopulatedLibrary() {
        val launch = screen.context.packageManager.getLaunchIntentForPackage(screen.context.packageName)!!
        launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        screen.context.startActivity(launch)
        screen.node(By.desc(screen.text(R.string.setting)))
        assertFalse(screen.device.hasObject(By.text(FeatureScreen.TITLE)))
        screen.node(By.text(screen.text(R.string.guide_import_external)))
        screen.capture("empty-library")
        screen.clickDescription(R.string.setting)
        screen.node(By.text(screen.text(R.string.settings_storage)))
        screen.device.pressBack()
        screen.node(By.desc(screen.text(R.string.setting)))
        screen.install()
        screen.launch(MainActivity::class.java)
        screen.node(By.text(FeatureScreen.TITLE))
        screen.capture("populated-library")
    }

    @Test fun filePickerImportsZipShowsResultAndOpensImportedPack() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        screen.install()
        val bytes = screen.zip()
        screen.cleanPacks()
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "basefeatures-file.zip")
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
        }
        val resolver = screen.context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        try {
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            val violations = CopyOnWriteArrayList<String>()
            val listener = Executors.newSingleThreadExecutor()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            var previousPolicy: StrictMode.ThreadPolicy? = null
            instrumentation.runOnMainSync {
                previousPolicy = StrictMode.getThreadPolicy()
                StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads().detectDiskWrites()
                    .penaltyLog()
                    .penaltyListener(listener) { violation ->
                        if (violation.stackTrace.any {
                            it.className.startsWith("com.kimjisub.launchpad.ui.compose.ImportResultDialogKt") ||
                                it.className.startsWith("com.kimjisub.launchpad.tool.UniPackImporter")
                        }) violations += violation.toString()
                    }.build())
            }
            try {
                screen.launch(MainActivity::class.java)
                screen.clickText(R.string.guide_import_external)
                // These are system picker labels; the dedicated emulator's system language is English.
                val file = screen.device.wait(Until.findObject(By.text("basefeatures-file.zip")), 3000)
                if (file == null) {
                    val toolbar = screen.node(By.res("com.google.android.documentsui", "toolbar"))
                    val roots = toolbar.findObject(By.clazz("android.widget.ImageButton"))
                        ?: throw AssertionError("Documents picker navigation button missing")
                    roots.click()
                    // Opening the drawer replaces its accessibility nodes during animation.
                    screen.device.waitForIdle()
                    screen.node(By.text("Downloads")).click()
                    screen.device.waitForIdle()
                }
                // Select explicitly: a delayed injected tap can become a long press and leave
                // DocumentsUI in selection mode without returning the document to the app.
                screen.node(By.text("basefeatures-file.zip")).longClick()
                screen.node(By.text("Select")).click()
                screen.node(By.text(screen.text(R.string.importComplete)))
                screen.node(By.text(FeatureScreen.TITLE))
                screen.node(By.desc("${screen.text(R.string.fileSize)} 0.01 MB"))
                screen.capture("file-import-result")
                screen.device.executeShellCommand("mkdir -p /data/local/tmp/unipad_tests")
                screen.device.executeShellCommand("screencap -p /data/local/tmp/unipad_tests/file-import-result.png")
                screen.clickText(R.string.importPlayNow)
                screen.await("Imported pack did not load", screen::ready)
                assertEquals(FeatureScreen.TITLE, screen.onMain { screen.vm().unipack.title })
                assertEquals("basefeatures-file", screen.onMain { screen.vm().unipack.id })
                screen.capture("file-import-play")
                listener.submit {}.get(10, java.util.concurrent.TimeUnit.SECONDS)
                assertEquals("Import accessed files on the main thread", emptyList<String>(), violations.toList())
            } finally {
                instrumentation.runOnMainSync { StrictMode.setThreadPolicy(previousPolicy!!) }
                listener.shutdownNow()
            }
        } finally {
            resolver.delete(uri, null, null)
        }
    }

    @Test fun deletePackRemovesFilesHistoryAndBookmarkReinstallStartsFresh() {
        screen.install()
        val repo = screen.repo
        repo.getOrCreate(FeatureScreen.PACK_ID)
        repo.recordOpen(FeatureScreen.PACK_ID)
        repo.toggleBookmark(FeatureScreen.PACK_ID)
        val other = FeatureScreen.STORE_ID
        repo.getOrCreate(other)
        repo.recordOpen(other)
        repo.toggleBookmark(other)
        screen.launch(MainActivity::class.java)
        screen.node(By.text(FeatureScreen.TITLE)).click()
        screen.clickDescription(R.string.cd_delete)
        screen.node(By.text(screen.text(R.string.doYouWantToDeleteUniPack)))
        screen.clickText(R.string.accept)
        val dao = AppDatabase.getInstance(screen.context).unipackDAO()
        screen.await("Deleted pack still exists") { !screen.pack().exists() && !dao.exists(FeatureScreen.PACK_ID) }
        assertFalse(screen.device.hasObject(By.text(FeatureScreen.TITLE)))
        assertEquals(1L to true, history(other))
        screen.install()
        screen.launch(MainActivity::class.java)
        screen.node(By.text(FeatureScreen.TITLE))
        assertEquals(0L to false, history(FeatureScreen.PACK_ID))
        screen.capture("delete-reinstall")
    }
    private fun history(id: String): Pair<Long, Boolean>? =
        AppDatabase.getInstance(screen.context).openHelper.readableDatabase
            .query("SELECT openCount, bookmark FROM Unipack WHERE id=?", arrayOf(id)).use {
                if (it.moveToFirst()) it.getLong(0) to (it.getInt(1) == 1) else null
            }
}
