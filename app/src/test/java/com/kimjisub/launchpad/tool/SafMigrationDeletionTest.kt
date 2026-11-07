package com.kimjisub.launchpad.tool

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.PackStaging
import com.kimjisub.launchpad.manager.WorkspaceManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.lingala.zip4j.ZipFile
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class SafMigrationDeletionTest(private val route: String) {
    @get:Rule val tmp = TemporaryFolder()
    private val context = mockk<Context>()
    private val resolver = mockk<ContentResolver>()
    private lateinit var workspace: File
    private lateinit var source: File
    private lateinit var target: File
    private lateinit var cache: File

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        workspace = tmp.newFolder("workspace")
        source = File(workspace, "pack").apply { mkdir() }
        File(source, "info").writeText("title=Original")
        target = tmp.newFolder("target")
        cache = tmp.newFolder("cache")
        every { context.cacheDir } returns cache
        every { context.contentResolver } returns resolver
        // Keep real DocumentFile directory creation and transfer I/O; only Android URI/MIME
        // helpers and the resolver boundary need substitutes on the JVM.
        mockkStatic(Uri::class)
        every { Uri.fromFile(any()) } answers {
            val file = firstArg<File>()
            mockk<Uri> {
                every { path } returns file.path
                every { scheme } returns "file"
            }
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
        tmp.root.walkTopDown().forEach { it.setWritable(true) }
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

    @Test fun aFolderThatCannotBeReadFailsTheMoveAndKeepsTheSource() = runBlocking {
        val sounds = File(source, "sounds").apply { mkdir() }
        File(sounds, "s0.wav").writeText("sound")
        sounds.setReadable(false)
        try {
            assumeTrue("needs a file system that enforces permissions", sounds.listFiles() == null)

            val result = transfer(deleteSource = true)

            assertEquals(0, result.transferred)
            assertEquals(1, result.failed)
            assertTrue(result.errors.single(), result.errors.single().contains("sounds"))
        } finally {
            sounds.setReadable(true)
        }
        assertEquals("title=Original", File(source, "info").readText())
        assertEquals("sound", File(sounds, "s0.wav").readText())
        assertEquals(emptyList<String>(), target.list()!!.toList())
    }

    @Test fun aSubfolderTheBackupFolderCannotCreateFailsTheMoveAndKeepsTheSource() = runBlocking {
        assumeTrue(route == "folder")
        File(source, "sounds").apply { mkdir(); File(this, "s0.wav").writeText("sound") }
        val backupFolder = spyk(DocumentFile.fromFile(target)) {
            every { createDirectory(any()) } answers {
                callOriginal()?.let { entry -> spyk(entry) { every { createDirectory(any()) } returns null } }
            }
        }

        val result = SafMigrationHelper(context).transferFileToSaf(listOf(source), backupFolder, true) { _, _, _ -> }

        assertEquals(0, result.transferred)
        assertEquals(1, result.failed)
        assertTrue(result.errors.single(), result.errors.single().contains("sounds"))
        assertEquals("sound", File(source, "sounds/s0.wav").readText())
        assertEquals(emptyList<String>(), target.list()!!.toList())
    }

    @Test fun aKillWhileDeletingTheSourceLeavesNoHalfDeletedPackInTheList() = runBlocking {
        // A folder whose files cannot be deleted stands in for a kill part way through deletion.
        val sounds = File(source, "sounds").apply { mkdir() }
        File(sounds, "s0.wav").writeText("sound")
        makeReadOnly(sounds)

        val result = transfer(deleteSource = true)

        assertEquals(1, result.transferred)
        assertFalse("half-deleted source stays where packs are listed", source.exists())
        assertEquals(emptyList<File>(), WorkspaceManager.packFolders(workspace))
        val leftovers = PackStaging.setAsideLeftovers(workspace)
        assertTrue("the next start's cleanup does not find the remains",
            leftovers.any { leftover -> leftover.walkTopDown().any { it.name == "s0.wav" } })
        assertCopiedInfo()
    }

    @Test fun aSourceThatCannotBeSetAsideIsKeptWhole() = runBlocking {
        makeReadOnly(workspace)

        val result = transfer(deleteSource = true)

        assertEquals(0, result.transferred)
        assertEquals(1, result.failed)
        assertTrue(result.errors.single(), result.errors.single().contains("the original was kept"))
        assertEquals("title=Original", File(source, "info").readText())
        assertCopiedInfo()
    }

    @Test fun aBackupThatCannotBeRenamedIsRemovedAndTheSourceKept() = runBlocking {
        assumeTrue(route != "file")
        val result = transfer(deleteSource = true, renamedTo = { null })

        assertEquals(0, result.transferred)
        assertEquals(1, result.failed)
        assertEquals("title=Original", File(source, "info").readText())
        assertEquals(emptyList<String>(), target.list()!!.toList())
    }

    @Test fun aBackupThatTheProviderNamesDifferentlyKeepsTheSource() = runBlocking {
        assumeTrue(route != "file")
        val result = transfer(deleteSource = true, renamedTo = { it.replace("pack", "pack (1)") })

        assertEquals(0, result.transferred)
        assertEquals(1, result.failed)
        assertTrue(result.errors.single(), result.errors.single().contains("pack (1)"))
        assertEquals("title=Original", File(source, "info").readText())
        assertEquals(listOf(if (route == "zip") "pack (1).zip" else "pack (1)"), target.list()!!.toList())
    }

    @Test fun onlyPartialEntriesAreRemovedFromTheBackupFolder() = runBlocking {
        assumeTrue(route != "file")
        val leftovers = listOf(
            File(target, ".unipad-partial-0b6f1a52-3f4e-4c55-9d64-0d4b8a3c2e11").apply { mkdir(); File(this, "info").writeText("partial") },
            File(target, ".unipad-partial-7d2c9e40-5a1b-4f63-8e2d-1c9b0a4f6d22.zip").apply { writeText("partial archive") },
        )
        val kept = listOf(
            File(target, ".hidden-note").apply { writeText("someone's file") },
            File(target, "notes.txt").apply { writeText("someone's file") },
            File(target, "other-pack").apply { mkdir(); File(this, "info").writeText("other") },
            File(target, "unipad-partial-looking.zip").apply { writeText("not ours") },
        )

        val result = transfer(deleteSource = false)

        assertEquals(1, result.transferred)
        leftovers.forEach { assertFalse("${it.name} was left", it.exists()) }
        kept.forEach { assertTrue("${it.name} was removed", it.exists()) }
        assertCopiedInfo()
    }

    @Test fun aBackupBeingWrittenIsNotUnderThePackNameAndAnotherExportKeepsIt() = runBlocking {
        assumeTrue(route != "file")
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writes = AtomicInteger()
        every { resolver.openOutputStream(any()) } answers {
            if (writes.getAndIncrement() == 0) {
                writing.countDown()
                check(release.await(10, TimeUnit.SECONDS))
            }
            File(firstArg<Uri>().path!!).outputStream()
        }
        val first = async(Dispatchers.IO) { transfer(deleteSource = true) }
        try {
            assertTrue(writing.await(10, TimeUnit.SECONDS))
            val inProgress = target.list()!!.single()
            assertTrue("unfinished backup is listed as $inProgress", inProgress.startsWith(".unipad-partial-"))

            val second = File(workspace, "second").apply { mkdir(); File(this, "info").writeText("second") }
            val helper = SafMigrationHelper(context)
            val secondResult = if (route == "zip") helper.transferFileToSafZip(listOf(second), DocumentFile.fromFile(target), false) { _, _, _ -> }
                else helper.transferFileToSaf(listOf(second), DocumentFile.fromFile(target), false) { _, _, _ -> }
            assertEquals(1, secondResult.transferred)
            assertTrue("another export removed the running export's entry", File(target, inProgress).exists())
        } finally {
            release.countDown()
        }
        assertEquals(SafMigrationHelper.TransferResult(1, 1, 0, 0, emptyList<String>()), first.await())
        assertFalse(source.exists())
        assertCopiedInfo()
    }

    @Test fun startupCleanupRemovesOnlyTheArchivesKilledTransfersLeft() = runBlocking {
        assumeTrue(route == "zip")
        val leftovers = listOf(
            File(cache, "transfer-4815162342.zip").apply { writeText("left by an earlier version") },
            File(cache, ".unipad-staging/5e3b/transfer.zip").apply { parentFile!!.mkdirs(); writeText("left by a killed transfer") },
        )
        val kept = listOf(File(cache, "theme_import_1.zip"), File(cache, "transfer-notes.txt"), File(cache, "image.png"))
            .onEach { it.writeText("other") }

        cleanUp(setAside = { SafMigrationHelper.setAsideTempArchives(cache) })

        leftovers.forEach { assertFalse("${it.path} was left", it.exists()) }
        kept.forEach { assertTrue("${it.name} was removed", it.exists()) }
        assertEquals(kept.map { it.name }.sorted(), cache.list()!!.sorted())
    }

    @Test fun aTransferStartedDuringStartupCleanupKeepsItsArchive() = runBlocking {
        assumeTrue(route == "zip")
        File(cache, ".unipad-staging/5e3b/transfer.zip").apply { parentFile!!.mkdirs(); writeText("left by a killed transfer") }
        val settingAside = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            PackStaging.startCleanup(scope, setAside = {
                settingAside.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                SafMigrationHelper.setAsideTempArchives(cache)
            }, deleteLeftovers = { it.forEach(FileManager::deleteDirectory) })
            assertTrue(settingAside.await(10, TimeUnit.SECONDS))
            val exporting = async(Dispatchers.IO) { transfer(deleteSource = false) }
            try {
                assertNull("the transfer wrote its archive before the cleanup set leftovers aside",
                    withTimeoutOrNull(1000) { exporting.await() })
            } finally {
                release.countDown()
            }
            assertEquals(1, exporting.await().transferred)
            scope.coroutineContext[Job]!!.children.toList().joinAll()
        } finally {
            release.countDown()
            scope.cancel()
        }
        assertCopiedInfo()
        assertEquals(emptyList<String>(), cache.list()!!.toList())
    }

    private fun makeReadOnly(dir: File) {
        dir.setWritable(false)
        assumeTrue("needs a file system that enforces permissions",
            runCatching { !File(dir, "probe").createNewFile() }.getOrDefault(true))
    }

    private suspend fun cleanUp(setAside: () -> List<File>) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            PackStaging.startCleanup(scope, setAside, deleteLeftovers = { it.forEach(FileManager::deleteDirectory) }).join()
            scope.coroutineContext[Job]!!.children.toList().joinAll()
        } finally {
            scope.cancel()
        }
    }

    /**
     * Runs the route's transfer. [renamedTo], for backup routes, stands in for the provider's rename:
     * the name it gives an entry instead of the requested one, or null for a refused rename.
     */
    private suspend fun transfer(deleteSource: Boolean, renamedTo: ((String) -> String?)? = null): SafMigrationHelper.TransferResult {
        val helper = SafMigrationHelper(context)
        val backupFolder = DocumentFile.fromFile(target).let { if (renamedTo == null) it else renamingFolder(it, renamedTo) }
        return when (route) {
            "file" -> helper.transferFileToFile(listOf(source), target, deleteSource) { _, _, _ -> }
            "folder" -> helper.transferFileToSaf(listOf(source), backupFolder, deleteSource) { _, _, _ -> }
            else -> helper.transferFileToSafZip(listOf(source), backupFolder, deleteSource) { _, _, _ -> }
        }
    }

    private fun renamingFolder(folder: DocumentFile, renamedTo: (String) -> String?): DocumentFile {
        fun renaming(entry: DocumentFile): DocumentFile {
            val file = File(entry.uri.path!!)
            var name: String? = null
            return spyk(entry) {
                every { renameTo(any()) } answers {
                    renamedTo(firstArg())?.let { if (file.renameTo(File(file.parentFile, it))) name = it }
                    name != null
                }
                every { this@spyk.name } answers { name ?: file.name }
            }
        }
        return spyk(folder) {
            every { createDirectory(any()) } answers { callOriginal()?.let(::renaming) }
            every { createFile(any(), any()) } answers { callOriginal()?.let(::renaming) }
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
