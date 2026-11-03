package com.kimjisub.launchpad

import android.system.ErrnoException
import android.system.Os
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.tool.SafMigrationHelper
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SafMigrationDeletionDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun internalStorageMovesPreserveTheLinkedPack() = checkStorage(context.filesDir)
    @Test fun externalStorageMovesPreserveOtherPacks() = checkStorage(requireNotNull(context.getExternalFilesDir(null)))

    @Test fun internalLinksMovedToExternalStoragePreserveTheOtherPack() =
        checkStorage(context.filesDir, requireNotNull(context.getExternalFilesDir(null)))

    private fun checkStorage(storage: File, destinationStorage: File = storage) = runBlocking {
        val root = File(storage, "migration-safety-${UUID.randomUUID()}").apply { check(mkdir()) }
        val destinationRoot = if (destinationStorage == storage) root else
            File(destinationStorage, "migration-target-${UUID.randomUUID()}").apply { check(mkdir()) }
        try {
            val otherPack = File(root, "other-pack").apply { check(mkdir()) }
            val sound = File(otherPack, "sound.wav").apply { writeText("keep other pack") }
            val probe = File(root, "probe")
            val supportsLinks = try {
                Os.symlink(otherPack.path, probe.path)
                check(probe.delete())
                true
            } catch (_: ErrnoException) { false }
            println("Migration storage=${storage.path} destination=${destinationStorage.path} supportsSymlinks=$supportsLinks")
            if (storage == context.filesDir) {
                assertTrue("internal storage must exercise existing symbolic links", supportsLinks)
            }
            for (route in listOf("file", "folder", "zip")) {
                val source = File(root, "pack-$route").apply { check(mkdir()) }
                File(source, "info").writeText("title=Original")
                if (supportsLinks) Os.symlink(otherPack.path, File(source, "linked-pack").path)
                val target = File(destinationRoot, "target-$route").apply { check(mkdir()) }
                val helper = SafMigrationHelper(context)
                val result = when (route) {
                    "file" -> helper.transferFileToFile(listOf(source), target, true) { _, _, _ -> }
                    "folder" -> helper.transferFileToSaf(listOf(source), DocumentFile.fromFile(target), true) { _, _, _ -> }
                    else -> helper.transferFileToSafZip(listOf(source), DocumentFile.fromFile(target), true) { _, _, _ -> }
                }
                println("Migration route=$route result=$result sourceExists=${source.exists()} otherSoundExists=${sound.exists()}")
                assertEquals(SafMigrationHelper.TransferResult(1, 1, 0, 0, emptyList<String>()), result)
                assertTrue("$route deleted the other pack sound", sound.isFile)
                assertEquals("keep other pack", sound.readText())
                assertFalse("$route must remove the completed move's source", source.exists())
                if (route == "zip") {
                    // RawDocumentFile may append a MIME extension; this fixture checks the
                    // transferred bytes, not system document-provider naming rules.
                    ZipFile(target.listFiles()!!.single { it.isFile }).use { zip ->
                        assertEquals("title=Original", zip.getInputStream(zip.getFileHeader("${source.name}/info"))
                            .bufferedReader().use { it.readText() })
                    }
                } else {
                    val copiedInfo = if (route == "file") File(target, "${source.name}/info") else
                        File(target, source.name).listFiles()!!.single { it.isFile }
                    assertEquals("title=Original", copiedInfo.readText())
                }
            }
        } finally {
            FileManager.deleteDirectory(root)
            if (destinationRoot != root) FileManager.deleteDirectory(destinationRoot)
        }
    }
}
