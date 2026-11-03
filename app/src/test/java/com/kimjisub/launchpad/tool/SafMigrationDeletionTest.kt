package com.kimjisub.launchpad.tool

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.lingala.zip4j.ZipFile
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.io.IOException
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class SafMigrationDeletionTest(private val route: String) {
    @get:Rule val tmp = TemporaryFolder()
    private val context = mockk<Context>()
    private val resolver = mockk<ContentResolver>()
    private lateinit var source: File
    private lateinit var target: File

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        source = tmp.newFolder("pack")
        File(source, "info").writeText("title=Original")
        target = tmp.newFolder("target")
        every { context.cacheDir } returns tmp.newFolder("cache")
        every { context.contentResolver } returns resolver
        // Keep real DocumentFile directory creation and transfer I/O; only Android URI/MIME
        // helpers and the resolver boundary need substitutes on the JVM.
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } answers {
            val file = firstArg<File>()
            mockk<Uri> { every { path } returns file.path }
        }
        mockkStatic(MimeTypeMap::class)
        val mimeTypes = mockk<MimeTypeMap>()
        every { mimeTypes.getMimeTypeFromExtension(any()) } returns null
        every { mimeTypes.getExtensionFromMimeType(any()) } returns null
        every { MimeTypeMap.getSingleton() } returns mimeTypes
        every { resolver.openOutputStream(any()) } answers { File(firstArg<Uri>().path!!).outputStream() }
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test fun movingAPackWithAnExistingLinkPreservesTheOtherPack() = runBlocking {
        val otherPack = tmp.newFolder("other-pack")
        val sound = File(otherPack, "sound.wav").apply { writeText("keep other pack") }
        Files.createSymbolicLink(File(source, "linked-pack").toPath(), otherPack.toPath())

        val result = transfer(deleteSource = true)

        assertEquals(SafMigrationHelper.TransferResult(1, 1, 0, 0, emptyList<String>()), result)
        assertTrue("other pack sound was deleted", sound.isFile)
        assertEquals("keep other pack", sound.readText())
        assertFalse("completed move must remove its source", source.exists())
        assertCopiedInfo()
    }

    @Test fun copyingKeepsTheSourceAndCompleteDestination() = runBlocking {
        val result = transfer(deleteSource = false)

        assertEquals(1, result.transferred)
        assertEquals(0, result.failed)
        assertEquals("title=Original", File(source, "info").readText())
        assertCopiedInfo()
    }

    @Test fun aNameCollisionSkipsTheMoveAndPreservesBothCopies() = runBlocking {
        val existing = if (route == "zip") File(target, "pack.zip").apply { writeText("existing backup") }
            else File(target, "pack").apply { mkdir(); File(this, "info").writeText("existing pack") }

        val result = transfer(deleteSource = true)

        assertEquals(SafMigrationHelper.TransferResult(1, 0, 1, 0, emptyList<String>()), result)
        assertEquals("title=Original", File(source, "info").readText())
        assertEquals(if (route == "zip") "existing backup" else "existing pack",
            (if (existing.isFile) existing else File(existing, "info")).readText())
    }

    @Test fun aCopyFailureKeepsTheSourceAndRemovesThePartialDestination() = runBlocking {
        if (route == "file") {
            Files.createSymbolicLink(File(source, "missing-sound").toPath(), File(tmp.root, "missing").toPath())
        } else {
            every { resolver.openOutputStream(any()) } throws IOException("write refused")
        }

        val result = transfer(deleteSource = true)

        assertEquals(0, result.transferred)
        assertEquals(1, result.failed)
        assertEquals("title=Original", File(source, "info").readText())
        assertEquals(emptyList<String>(), target.listFiles()!!.map { it.name })
    }

    private suspend fun transfer(deleteSource: Boolean): SafMigrationHelper.TransferResult {
        val helper = SafMigrationHelper(context)
        return when (route) {
            "file" -> helper.transferFileToFile(listOf(source), target, deleteSource) { _, _, _ -> }
            "folder" -> helper.transferFileToSaf(listOf(source), DocumentFile.fromFile(target), deleteSource) { _, _, _ -> }
            else -> helper.transferFileToSafZip(listOf(source), DocumentFile.fromFile(target), deleteSource) { _, _, _ -> }
        }
    }

    private fun assertCopiedInfo() {
        if (route == "zip") {
            ZipFile(File(target, "pack.zip")).use { zip ->
                assertEquals("title=Original", zip.getInputStream(zip.getFileHeader("pack/info")).bufferedReader().use { it.readText() })
            }
        } else {
            assertEquals("title=Original", File(target, "pack/info").readText())
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun routes() = listOf(arrayOf("file"), arrayOf("folder"), arrayOf("zip"))
    }
}
