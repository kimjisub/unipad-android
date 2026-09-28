package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.PACK_NAME
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.contentHashes
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.expectedHashes
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.packZip
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.writePack
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Recorder
import com.kimjisub.launchpad.unipack.UniPack
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Two installs of the same name that overlap (leaving the store and opening it again, a code
 * import next to a store download, a file import next to either) must each keep their own
 * folder, and a failed or cancelled one deletes only what it created itself.
 */
class UniPackInstallOverlapTest {

	private val env = PackInstallTestEnv()

	@Before
	fun setUp() = env.setUp()

	@After
	fun tearDown() = env.tearDown()

	private fun installed(recorder: Recorder): File {
		assertNull("unexpected error: ${recorder.error}", recorder.error)
		return requireNotNull(recorder.installedFolder) { "install did not complete" }
	}

	@Test
	fun twoOverlappingDownloadsInstallSeparately() {
		val first = Recorder()
		val second = Recorder()
		val firstScope = env.download(FIRST_URL, first)
		val secondScope = env.download(SECOND_URL, second)
		env.gate(FIRST_URL).awaitRequest()
		env.gate(SECOND_URL).awaitRequest()

		env.gate(FIRST_URL).open(packZip("First"))
		env.finish(firstScope)
		env.gate(SECOND_URL).open(packZip("Second"))
		env.finish(secondScope)

		val firstFolder = installed(first)
		val secondFolder = installed(second)
		assertNotEquals(firstFolder, secondFolder)
		assertEquals(expectedHashes("First"), contentHashes(firstFolder))
		assertEquals(expectedHashes("Second"), contentHashes(secondFolder))
		assertEquals(listOf(PACK_NAME, "$PACK_NAME (2)"), env.workspaceContents())
	}

	@Test
	fun failingDownloadKeepsTheOverlappingOnesPack() {
		val failed = Recorder()
		val succeeded = Recorder()
		val failedScope = env.download(FIRST_URL, failed)
		val succeededScope = env.download(SECOND_URL, succeeded)
		env.gate(FIRST_URL).awaitRequest()
		env.gate(SECOND_URL).awaitRequest()

		env.gate(SECOND_URL).open(packZip("Succeeded"))
		env.finish(succeededScope)
		env.gate(FIRST_URL).fail()
		env.finish(failedScope)

		assertNotNull(failed.error)
		val folder = installed(succeeded)
		assertEquals(expectedHashes("Succeeded"), contentHashes(folder))
		assertEquals(listOf(folder.name), env.workspaceContents())
	}

	/** The store screen was left while its download waited on the network, then opened again. */
	@Test
	fun downloadCancelledByLeavingTheScreenKeepsTheNewDownloadAndTheExistingPack() {
		writePack(File(env.workspace, PACK_NAME), "Existing")
		val left = Recorder()
		val reopened = Recorder()
		val leftScope = env.download(FIRST_URL, left)
		env.gate(FIRST_URL).awaitRequest()
		leftScope.cancel()
		val reopenedScope = env.download(SECOND_URL, reopened)
		env.gate(SECOND_URL).awaitRequest()

		env.gate(SECOND_URL).open(packZip("Reopened"))
		env.finish(reopenedScope)
		env.gate(FIRST_URL).open(packZip("Left"))
		env.finish(leftScope)

		assertNull(left.installedFolder)
		val folder = installed(reopened)
		assertEquals(expectedHashes("Reopened"), contentHashes(folder))
		assertEquals(expectedHashes("Existing"), contentHashes(File(env.workspace, PACK_NAME)))
		assertEquals(listOf(PACK_NAME, folder.name).sorted(), env.workspaceContents())
	}

	@Test
	fun retryAfterFailedDownloadInstallsUnderTheOriginalName() {
		val failed = Recorder()
		val failedScope = env.download(FIRST_URL, failed)
		env.gate(FIRST_URL).fail()
		env.finish(failedScope)
		assertNotNull(failed.error)
		assertEquals(emptyList<String>(), env.workspaceContents())

		val retried = Recorder()
		val retriedScope = env.download(SECOND_URL, retried)
		env.gate(SECOND_URL).open(packZip("Retried"))
		env.finish(retriedScope)

		val folder = installed(retried)
		assertEquals(PACK_NAME, folder.name)
		assertEquals(expectedHashes("Retried"), contentHashes(folder))
		assertEquals(listOf(PACK_NAME), env.workspaceContents())
	}

	/** A file import that reports its start while a download of the same name completes. */
	private fun importAcrossDownload(importZip: ByteArray, downloaded: Recorder, imported: Recorder) {
		val importer = object : Recorder() {
			override fun onImportStart() {
				val downloadScope = env.download(FIRST_URL, downloaded)
				env.gate(FIRST_URL).open(packZip("Downloaded"))
				env.finish(downloadScope)
			}

			override fun onImportComplete(folder: File, unipack: UniPack) =
				imported.onImportComplete(folder, unipack)

			override fun onException(throwable: Throwable) = imported.onException(throwable)
		}
		env.finish(env.import(importZip, importer))
	}

	@Test
	fun fileImportOverlappingADownloadInstallsSeparately() {
		val downloaded = Recorder()
		val imported = Recorder()

		importAcrossDownload(packZip("Imported"), downloaded, imported)

		val downloadedFolder = installed(downloaded)
		val importedFolder = installed(imported)
		assertNotEquals(downloadedFolder, importedFolder)
		assertEquals(expectedHashes("Downloaded"), contentHashes(downloadedFolder))
		assertEquals(expectedHashes("Imported"), contentHashes(importedFolder))
		assertEquals(listOf(PACK_NAME, "$PACK_NAME (2)"), env.workspaceContents())
	}

	@Test
	fun failingFileImportKeepsTheOverlappingDownload() {
		val downloaded = Recorder()
		val imported = Recorder()

		importAcrossDownload("not a zip".toByteArray(), downloaded, imported)

		assertNotNull(imported.error)
		val folder = installed(downloaded)
		assertEquals(expectedHashes("Downloaded"), contentHashes(folder))
		assertEquals(listOf(folder.name), env.workspaceContents())
	}

	@Test
	fun fileImportKeepsTheExistingPack() {
		writePack(File(env.workspace, PACK_NAME), "Existing")
		val imported = Recorder()

		env.finish(env.import(packZip("Imported"), imported))

		val folder = installed(imported)
		assertEquals("$PACK_NAME (2)", folder.name)
		assertEquals(expectedHashes("Imported"), contentHashes(folder))
		assertEquals(expectedHashes("Existing"), contentHashes(File(env.workspace, PACK_NAME)))
	}

	private companion object {
		const val FIRST_URL = "https://test.invalid/first.zip"
		const val SECOND_URL = "https://test.invalid/second.zip"
	}
}
