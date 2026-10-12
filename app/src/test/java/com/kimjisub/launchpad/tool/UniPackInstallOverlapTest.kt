package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.analytics.PackImportReport
import com.kimjisub.launchpad.analytics.PackImportSource
import com.kimjisub.launchpad.analytics.UsageParam
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

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
	fun cancellationThenReadErrorCleansOnlyItsZipAndAllowsRetry() {
		writePack(File(env.workspace, PACK_NAME), "Existing")
		val left = Recorder()
		val results = java.util.concurrent.CopyOnWriteArrayList<String>()
		val report = PackImportReport(PackImportSource.STORE) { _, params -> results += params.getValue(UsageParam.RESULT) }
		val leftScope = env.download(FIRST_URL, left, usage = report)
		val (reading, fail) = env.gate(FIRST_URL).openBlockedBody(ByteArray(1024) { 7 }, false)
		try {
			assertTrue(reading.await(10, TimeUnit.SECONDS))
			val partial = env.workspace.listFiles()!!.single { it.extension == "zip" }
			assertEquals(1024L, partial.length())
			val imported = Recorder()
			env.finish(env.import(packZip("Imported"), imported))
			val other = installed(imported)
			leftScope.cancel()
			fail.countDown() // The read fails after cancellation, rather than returning a response.
			env.finish(leftScope)
			assertNull(left.error)
			assertEquals(listOf("cancelled"), results.toList())
			assertNull(left.installedFolder)
			assertTrue("cancelled partial ZIP must be removed", !partial.exists())
			assertEquals(expectedHashes("Existing"), contentHashes(File(env.workspace, PACK_NAME)))
			assertEquals(expectedHashes("Imported"), contentHashes(other))
			val retry = Recorder()
			val retryScope = env.download(SECOND_URL, retry)
			env.gate(SECOND_URL).open(packZip("Retried"))
			env.finish(retryScope)
			val retried = installed(retry)
			assertEquals(expectedHashes("Retried"), contentHashes(retried))
			assertEquals(listOf(PACK_NAME, other.name, retried.name).sorted(), env.workspaceContents())
		} finally {
			fail.countDown()
			leftScope.cancel()
			env.finish(leftScope)
		}
	}

	@Test
	fun cancellationStopsWaitingForResponseAndRemovesItsClaimedZip() {
		val left = Recorder()
		val scope = env.download(FIRST_URL, left)
		env.gate(FIRST_URL).awaitRequest()
		try {
			scope.cancel()
			assertTrue(env.gate(FIRST_URL).cancelled.await(1, TimeUnit.SECONDS))
			env.finish(scope)
			assertNull(left.error)
			assertEquals(emptyList<String>(), env.workspaceContents())
		} finally {
			env.gate(FIRST_URL).fail()
			scope.cancel()
			env.finish(scope)
		}
	}

	@Test
	fun cancellationStopsBlockedReadWithoutWaitingForNetworkTimeout() {
		val left = Recorder()
		val scope = env.download(FIRST_URL, left)
		val (reading, fail) = env.gate(FIRST_URL).openBlockedBody(ByteArray(1024), true)
		try {
			assertTrue(reading.await(10, TimeUnit.SECONDS))
			scope.cancel()
			assertTrue("cancel must reach the HTTP call immediately", env.gate(FIRST_URL).cancelled.await(1, TimeUnit.SECONDS))
			env.finish(scope)
			assertNull(left.error)
			assertEquals(emptyList<String>(), env.workspaceContents())
		} finally {
			fail.countDown()
			scope.cancel()
			env.finish(scope)
		}
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

			override fun onImportComplete(folder: File, unipack: UniPack, byteSize: Long) =
				imported.onImportComplete(folder, unipack, byteSize)

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
