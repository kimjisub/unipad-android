package com.kimjisub.launchpad.tool

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.fixture.SymlinkArchive
import com.kimjisub.launchpad.manager.FileManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class SafArchiveSymlinkTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun restoringAZipBackupCannotLeaveLinksToAnotherPack() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val victim = tmp.newFolder("other-pack")
            val sound = File(victim, "sound.wav").apply { writeText("keep") }
            val archive = SymlinkArchive.write(tmp.newFile("backup.zip"),
                mapOf("info" to "title=Pack", "sub" to victim.path), setOf("sub"))
            val context = mockk<Context>()
            val resolver = mockk<ContentResolver>()
            val uri = mockk<Uri>()
            val document = mockk<DocumentFile>()
            every { document.name } returns "backup.zip"
            every { document.uri } returns uri
            every { context.cacheDir } returns tmp.newFolder("cache")
            every { context.contentResolver } returns resolver
            every { resolver.openInputStream(uri) } answers { archive.inputStream() }
            val workspace = tmp.newFolder("workspace")

            val result = SafMigrationHelper(context).transferSafZipToFile(listOf(document), workspace, false) { _, _, _ -> }

            assertEquals(1, result.transferred)
            val imported = File(workspace, "backup")
            assertFalse(File(imported, "sub").exists())
            FileManager.deleteDirectory(imported)
            assertEquals("keep", sound.readText())
        } finally {
            Dispatchers.resetMain()
            unmockkAll()
        }
    }
}
