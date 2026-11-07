package com.kimjisub.launchpad.tool

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Moving a pack from a backup folder into the app's storage. */
@OptIn(ExperimentalCoroutinesApi::class)
class SafRestoreTransferTest {
	@get:Rule val tmp = TemporaryFolder()
	private val context = mockk<Context>()
	private val resolver = mockk<ContentResolver>()
	private lateinit var target: File
	private val deleted = mutableListOf<String>()

	@Before fun setUp() {
		Dispatchers.setMain(UnconfinedTestDispatcher())
		target = tmp.newFolder("workspace")
		every { context.contentResolver } returns resolver
		every { context.cacheDir } returns tmp.newFolder("cache")
		mockkStatic(DocumentsContract::class)
		mockkStatic(Uri::class)
		every { Uri.fromFile(any()) } answers {
			val file = firstArg<File>()
			mockk<Uri> {
				every { path } returns file.path
				every { scheme } returns "file"
			}
		}
	}

	@After fun tearDown() {
		Dispatchers.resetMain()
		unmockkAll()
		tmp.root.walkTopDown().forEach { it.setReadable(true) }
	}

	@Test fun aCompleteMoveCopiesEverythingAndRemovesTheBackup() = runBlocking {
		val pack = Node.dir("pack", Node.file("info", "title=Original"), Node.dir("sounds", Node.file("s0.wav", "sound")))

		val result = move(pack)

		assertEquals(SafMigrationHelper.TransferResult(1, 1, 0, 0, emptyList<String>()), result)
		assertEquals("title=Original", File(target, "pack/info").readText())
		assertEquals("sound", File(target, "pack/sounds/s0.wav").readText())
		assertEquals(listOf("pack"), deleted)
	}

	@Test fun aSubfolderTheBackupFolderCannotListFailsTheMoveAndKeepsTheBackup() = runBlocking {
		val pack = Node.dir("pack", Node.file("info", "title=Original"),
			Node.dir("sounds", Node.file("s0.wav", "sound")).apply { listing = Listing.NoCursor })

		assertFailedAndKept(move(pack), "sounds")
	}

	@Test fun aSubfolderTheBackupFolderReportsAnErrorForFailsTheMove() = runBlocking {
		val pack = Node.dir("pack", Node.file("info", "title=Original"),
			Node.dir("sounds", Node.file("s0.wav", "sound")).apply { listing = Listing.Error })

		assertFailedAndKept(move(pack), "network unavailable")
	}

	@Test fun aSubfolderStillLoadingFailsTheMove() = runBlocking {
		val pack = Node.dir("pack", Node.file("info", "title=Original"),
			Node.dir("sounds", Node.file("s0.wav", "sound")).apply { listing = Listing.Loading })

		assertFailedAndKept(move(pack), "still loading")
	}

	@Test fun aFileThatCannotBeOpenedFailsTheMoveAndKeepsTheBackup() = runBlocking {
		val pack = Node.dir("pack", Node.file("info", "title=Original"),
			Node.dir("sounds", Node.file("s0.wav", "sound").apply { opens = false }))

		assertFailedAndKept(move(pack), "s0.wav")
	}

	@Test fun aFolderTheAppStorageCannotCreateFailsTheMoveAndKeepsTheBackup() = runBlocking {
		// No file system takes a 300-character name, which a documents provider may still report.
		val tooLong = "f".repeat(300)
		assumeTrue("needs a file system that refuses long names", !File(tmp.root, tooLong).mkdir())
		val pack = Node.dir("pack", Node.file("info", "title=Original"), Node.dir(tooLong))

		assertFailedAndKept(move(pack), tooLong)
	}

	@Test fun aLocalBackupSubfolderThatCannotBeReadFailsTheMoveAndKeepsTheBackup() = runBlocking {
		val backup = tmp.newFolder("backup")
		val pack = File(backup, "pack").apply { mkdir() }
		File(pack, "info").writeText("title=Original")
		val sounds = File(pack, "sounds").apply { mkdir() }
		File(sounds, "s0.wav").writeText("sound")
		every { resolver.openInputStream(any()) } answers { File(firstArg<Uri>().path!!).inputStream() }
		sounds.setReadable(false)
		assumeTrue("needs a file system that enforces permissions", sounds.listFiles() == null)

		val result = SafMigrationHelper(context).transferSafToFile(listOf(DocumentFile.fromFile(pack)), target, true) { _, _, _ -> }
		sounds.setReadable(true)

		assertEquals(0, result.transferred)
		assertEquals(1, result.failed)
		assertTrue(result.errors.single(), result.errors.single().contains("sounds"))
		assertEquals("title=Original", File(pack, "info").readText())
		assertEquals("sound", File(sounds, "s0.wav").readText())
		assertEquals(emptyList<String>(), target.list()!!.toList())
	}

	private fun assertFailedAndKept(result: SafMigrationHelper.TransferResult, errorPart: String) {
		assertEquals(0, result.transferred)
		assertEquals(1, result.failed)
		assertTrue(result.errors.single(), result.errors.single().contains(errorPart))
		assertEquals("the backup was deleted", emptyList<String>(), deleted)
		assertEquals(emptyList<String>(), target.list()!!.toList())
	}

	private suspend fun move(pack: Node): SafMigrationHelper.TransferResult {
		installProvider(pack)
		return SafMigrationHelper(context).transferSafToFile(listOf(pack.document), target, true) { _, _, _ -> }
	}

	private enum class Listing { Complete, NoCursor, Error, Loading }

	/** One entry of a fake documents provider holding the backup folder. */
	private class Node(val name: String, val content: String?, val children: List<Node>) {
		val id = "backup/$name/${System.identityHashCode(this)}"
		var listing = Listing.Complete
		var opens = true
		val uri = mockk<Uri> { every { scheme } returns "content"; every { this@mockk.toString() } returns "content://backup/document/$id" }
		val childrenUri = mockk<Uri> { every { this@mockk.toString() } returns "content://backup/children/$id" }
		lateinit var document: DocumentFile

		companion object {
			fun dir(name: String, vararg children: Node) = Node(name, null, children.toList())
			fun file(name: String, content: String) = Node(name, content, emptyList())
		}
	}

	/**
	 * Answers the resolver's queries for [root]'s tree, and gives each entry the [DocumentFile] the
	 * library builds for it, whose listFiles() returns no entries where the provider's listing fails.
	 */
	private fun installProvider(root: Node) {
		fun install(node: Node) {
			node.children.forEach(::install)
			every { DocumentsContract.getDocumentId(node.uri) } returns node.id
			every { DocumentsContract.buildChildDocumentsUriUsingTree(node.uri, node.id) } returns node.childrenUri
			every { DocumentsContract.buildDocumentUriUsingTree(any(), node.id) } returns node.uri
			every { resolver.openInputStream(node.uri) } answers { if (node.opens) node.content?.byteInputStream() else null }
			every { resolver.query(node.childrenUri, any(), null, null, null) } answers {
				if (node.listing == Listing.NoCursor) null else cursorOf(node)
			}
			node.document = mockk {
				every { uri } returns node.uri
				every { name } returns node.name
				every { isDirectory } returns (node.content == null)
				every { listFiles() } answers {
					if (node.listing == Listing.Complete) node.children.map { it.document }.toTypedArray() else emptyArray()
				}
				every { delete() } answers { deleted += node.name; true }
			}
		}
		install(root)
	}

	private fun cursorOf(node: Node): Cursor {
		val rows = if (node.listing == Listing.Complete) node.children else emptyList()
		var position = -1
		val extras = mockk<Bundle> {
			every { getString(DocumentsContract.EXTRA_ERROR) } returns if (node.listing == Listing.Error) "network unavailable" else null
			every { getBoolean(DocumentsContract.EXTRA_LOADING) } returns (node.listing == Listing.Loading)
		}
		return mockk(relaxUnitFun = true) {
			every { this@mockk.extras } returns extras
			every { moveToNext() } answers { ++position < rows.size }
			every { getString(any()) } answers {
				val row = rows[position]
				when (firstArg<Int>()) {
					0 -> row.id
					1 -> row.name
					else -> if (row.content == null) DocumentsContract.Document.MIME_TYPE_DIR else "audio/wav"
				}
			}
		}
	}
}
