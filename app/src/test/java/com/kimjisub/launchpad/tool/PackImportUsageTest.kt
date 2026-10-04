package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.analytics.PackImportSource
import com.kimjisub.launchpad.analytics.RecordingUsageSink
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.analytics.UsageEvent
import com.kimjisub.launchpad.analytics.UsageParam
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.packZip
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Recorder
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import io.mockk.every
import io.mockk.mockkConstructor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.ProtocolException
import java.net.UnknownHostException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * `pack_import` is decided where the pack is really installed: [UniPackImporter] and
 * [UniPackDownloader]. The screens only say where the pack came from, so an import is never counted
 * by both a screen and the worker beneath it.
 */
class PackImportUsageTest {

	private val env = PackInstallTestEnv()
	private val sink = RecordingUsageSink()

	@Before
	fun setUp() = env.setUp()

	@After
	fun tearDown() = env.tearDown()

	private fun importEvent(source: String, result: String, error: String? = null) = sink.pack(
		UsageEvent.PACK_IMPORT,
		*listOfNotNull(
			UsageParam.RESULT to result,
			UsageParam.IMPORT_SOURCE to source,
			error?.let { UsageParam.ERROR_TYPE to it },
		).toTypedArray(),
	)

	private fun usage(source: PackImportSource) = sink.analytics.packImport(source)

	/** Closes the screen handed to the returned future at the moment the unpacked pack's files are read. */
	private fun closeTheScreenWhileThePackIsRead(): CompletableFuture<CoroutineScope> {
		val screen = CompletableFuture<CoroutineScope>()
		mockkConstructor(UniPackFolder::class)
		every { anyConstructed<UniPackFolder>().load() } answers {
			screen.get(10, TimeUnit.SECONDS).cancel()
			callOriginal()
		}
		return screen
	}

	private fun zipWithout(vararg missing: String): ByteArray {
		val bytes = ByteArrayOutputStream()
		ZipOutputStream(bytes).use { zip ->
			PackInstallTestEnv.packFiles("Broken").filterKeys { it !in missing }.forEach { (name, data) ->
				zip.putNextEntry(ZipEntry(name))
				zip.write(data)
				zip.closeEntry()
			}
		}
		return bytes.toByteArray()
	}

	@Test
	fun importedFileIsCountedOnceAfterThePackIsInstalled() {
		var installedWhenLogged = false
		sink.observe { installedWhenLogged = File(env.workspace, PackInstallTestEnv.PACK_NAME).let { it.isDirectory && File(it, "info").isFile } }
		val recorder = Recorder()

		env.finish(env.import(packZip("Faded"), recorder, usage = usage(PackImportSource.FILE)))

		assertNotNull(recorder.installedFolder)
		assertEquals(listOf(importEvent("file", "success")), sink.events)
		assertTrue("the event was sent before the pack's files were in the workspace", installedWhenLogged)
	}

	@Test
	fun notAZipIsACorruptArchiveFailureAndLeavesNoPack() {
		val recorder = Recorder()

		env.finish(env.import("not a zip".toByteArray(), recorder, usage = usage(PackImportSource.FILE)))

		assertNotNull(recorder.error)
		assertEquals(listOf(importEvent("file", "failure", "corrupt_archive")), sink.events)
		assertEquals(emptyList<String>(), env.workspaceContents())
	}

	@Test
	fun aZipWithoutTheInfoFileIsAnInvalidPackFailure() {
		val recorder = Recorder()

		env.finish(env.import(zipWithout("info"), recorder, usage = usage(PackImportSource.FILE)))

		assertNotNull(recorder.error)
		assertEquals(listOf(importEvent("file", "failure", "invalid_pack")), sink.events)
	}

	/** The screen's scope is closed while the import has only reported its start, as when the person leaves the main screen. */
	@Test
	fun leavingTheScreenWhileImportingIsOneCancellationAndInstallsNothing() {
		val started = CountDownLatch(1)
		val released = CountDownLatch(1)
		val listener = object : Recorder() {
			override fun onImportStart() {
				started.countDown()
				released.await(10, TimeUnit.SECONDS)
			}
		}
		val scope = env.import(packZip("Faded"), listener, usage = usage(PackImportSource.FILE))
		assertTrue(started.await(10, TimeUnit.SECONDS))

		scope.cancel()
		released.countDown()
		env.finish(scope)

		assertNull(listener.installedFolder)
		assertEquals(listOf(importEvent("file", "cancelled")), sink.events)
	}

	@Test
	fun downloadedStorePackIsCountedOnceWhenInstalledNotWhenRequested() {
		val recorder = Recorder()
		val scope = env.download(URL, recorder, usage = usage(PackImportSource.STORE))
		env.gate(URL).awaitRequest()

		assertEquals("a request in flight is not a result", emptyList<Any>(), sink.events)

		env.gate(URL).open(packZip("Faded"))
		env.finish(scope)

		assertNotNull(recorder.installedFolder)
		assertEquals(listOf(importEvent("store", "success")), sink.events)
	}

	@Test
	fun serverErrorIsAServerFailure() {
		val recorder = Recorder()
		val scope = env.download(URL, recorder, usage = usage(PackImportSource.STORE))
		env.gate(URL).fail()
		env.finish(scope)

		assertNotNull(recorder.error)
		assertEquals(listOf(importEvent("store", "failure", "server")), sink.events)
	}

	@Test
	fun missingPackIsANotFoundFailure() {
		val recorder = Recorder()
		val scope = env.download(URL, recorder, usage = usage(PackImportSource.CODE))
		env.gate(URL).failWithStatus(404)
		env.finish(scope)

		assertEquals(listOf(importEvent("code", "failure", "not_found")), sink.events)
		assertEquals("Empty response body (HTTP 404)", recorder.error?.message)
	}

	@Test
	fun noNetworkIsANetworkFailure() {
		val recorder = Recorder()
		val scope = env.download(URL, recorder, usage = usage(PackImportSource.STORE))
		env.gate(URL).failWithException(UnknownHostException("api.unipad.io"))
		env.finish(scope)

		assertEquals(listOf(importEvent("store", "failure", "network")), sink.events)
	}

	@Test
	fun aConnectionCutWhileThePackIsDownloadedIsANetworkFailureAndLeavesNoPack() {
		val cuts = listOf(ProtocolException("unexpected end of stream"), EOFException(), IOException("unexpected end of stream on https://host/"))

		for (cut in cuts) {
			val events = RecordingUsageSink()
			val recorder = Recorder()
			val url = "$URL/${cut.javaClass.simpleName}"
			val scope = env.download(url, recorder, usage = events.analytics.packImport(PackImportSource.STORE))
			env.gate(url).openThenBreak(packZip("Faded").copyOf(64), cut)
			env.finish(scope)

			assertEquals("$cut", listOf(importEvent("store", "failure", "network")), events.events)
			assertNull(recorder.installedFolder)
			assertEquals(emptyList<String>(), env.workspaceContents())
		}
	}

	@Test
	fun theSameErrorsWhileAPickedFileIsReadAreFileAccessFailures() {
		val errors = listOf(ProtocolException("unexpected end of stream"), EOFException())

		for (error in errors) {
			val events = RecordingUsageSink()
			val recorder = Recorder()
			val stream = object : InputStream() {
				override fun read(): Int = throw error
			}

			env.finish(env.import(ByteArray(0), recorder, usage = events.analytics.packImport(PackImportSource.FILE), openInput = { stream }))

			assertEquals("$error", listOf(importEvent("file", "failure", "file_access")), events.events)
			assertEquals(emptyList<String>(), env.workspaceContents())
		}
	}

	@Test
	fun aPackThatCannotBeWrittenWhileItIsDownloadedIsAFileAccessFailure() {
		env.cannotOpenFilesForWriting()
		val recorder = Recorder()
		val scope = env.download(URL, recorder, usage = usage(PackImportSource.STORE))
		env.gate(URL).open(packZip("Faded"))
		env.finish(scope)

		assertEquals(listOf(importEvent("store", "failure", "file_access")), sink.events)
	}

	@Test
	fun downloadedPackWithoutInfoIsAnInvalidPackFailure() {
		val recorder = Recorder()
		val scope = env.download(URL, recorder, usage = usage(PackImportSource.STORE))
		env.gate(URL).open(zipWithout("info"))
		env.finish(scope)

		assertEquals(listOf(importEvent("store", "failure", "invalid_pack")), sink.events)
		assertEquals(emptyList<String>(), env.workspaceContents())
	}

	@Test
	fun leavingTheStoreDuringADownloadIsOneCancellationEvenIfTheResponseArrivesLater() {
		val recorder = Recorder()
		val scope = env.download(URL, recorder, usage = usage(PackImportSource.STORE))
		env.gate(URL).awaitRequest()

		scope.cancel()
		env.gate(URL).open(packZip("Late"))
		env.finish(scope)

		assertNull(recorder.installedFolder)
		assertEquals(listOf(importEvent("store", "cancelled")), sink.events)
	}

	/** Unpacking and reading the pack are not interrupted by a cancellation; the store screen closes during them. */
	@Test
	fun leavingTheStoreWhileTheDownloadIsUnpackedIsACancellationBecauseThePackIsDiscarded() {
		val recorder = Recorder()
		val screen = closeTheScreenWhileThePackIsRead()
		val scope = env.download(URL, recorder, usage = usage(PackImportSource.STORE))
		screen.complete(scope)
		env.gate(URL).open(packZip("Faded"))
		env.finish(scope)

		assertNull(recorder.installedFolder)
		assertEquals("the download is discarded when its screen is gone", emptyList<String>(), env.workspaceContents())
		assertEquals(listOf(importEvent("store", "cancelled")), sink.events)
	}

	@Test
	fun aStorePackAnnouncedAsInstalledIsKeptAndCountedEvenIfTheScreenClosesDuringTheAnnouncement() {
		val screen = CompletableFuture<CoroutineScope>()
		val listener = object : Recorder() {
			override fun onInstallComplete(folder: File, unipack: UniPack) {
				super.onInstallComplete(folder, unipack)
				screen.get(10, TimeUnit.SECONDS).cancel()
			}
		}
		val scope = env.download(URL, listener, usage = usage(PackImportSource.STORE))
		screen.complete(scope)
		env.gate(URL).open(packZip("Faded"))
		env.finish(scope)

		assertNotNull(listener.installedFolder)
		assertEquals("only the pack is left, not its archive", listOf(PackInstallTestEnv.PACK_NAME), env.workspaceContents())
		assertEquals(listOf(importEvent("store", "success")), sink.events)
	}

	/** A file import keeps a pack it has finished reading, so the same moment is a success there. */
	@Test
	fun leavingTheMainScreenWhileAFileIsUnpackedKeepsThePackAndCountsItOnce() {
		val recorder = Recorder()
		val screen = closeTheScreenWhileThePackIsRead()
		val scope = env.import(packZip("Faded"), recorder, usage = usage(PackImportSource.FILE))
		screen.complete(scope)
		env.finish(scope)

		assertNull("the closed screen is not told", recorder.installedFolder)
		assertEquals(listOf(PackInstallTestEnv.PACK_NAME), env.workspaceContents())
		assertEquals(listOf(importEvent("file", "success")), sink.events)
	}

	@Test
	fun aCancelAfterTheInstallAddsNoSecondResult() {
		val usage = usage(PackImportSource.CODE)
		val recorder = Recorder()
		val scope = env.download(URL, recorder, usage = usage)
		env.gate(URL).open(packZip("Faded"))
		env.finish(scope)

		usage.cancelled()
		scope.cancel()

		assertEquals(listOf(importEvent("code", "success")), sink.events)
	}

	@Test
	fun sharedReportOfOneCodeImportCountsOnceWhenTheScreenAndTheDownloaderBothReport() {
		val usage = usage(PackImportSource.CODE)
		val recorder = Recorder()
		val scope = env.download(URL, recorder, usage = usage)
		env.gate(URL).fail()
		env.finish(scope)
		usage.cancelled() // the screen closing afterwards

		assertEquals(listOf(importEvent("code", "failure", "server")), sink.events)
	}

	/** Analytics that cannot start, as when Firebase fails to initialise, must not cost the person the pack. */
	@Test
	fun aBrokenAnalyticsSinkDoesNotStopAFileImportOrAStoreDownload() {
		val broken = UsageAnalytics { _, _ -> throw IllegalStateException("Firebase is not initialised") }
		val imported = Recorder()
		val downloaded = Recorder()

		env.finish(env.import(packZip("Faded"), imported, usage = broken.packImport(PackImportSource.FILE)))
		val scope = env.download(URL, downloaded, name = "store", usage = broken.packImport(PackImportSource.STORE))
		env.gate(URL).open(packZip("Faded"))
		env.finish(scope)

		assertNotNull(imported.installedFolder)
		assertNotNull(downloaded.installedFolder)
		assertEquals(listOf(PackInstallTestEnv.PACK_NAME, "store"), env.workspaceContents())
	}

	@Test
	fun noEventCarriesThePackNameOrLocation() {
		val scope = env.download(URL, Recorder(), name = "Secret Pack Title", usage = usage(PackImportSource.STORE))
		env.gate(URL).open(packZip("Secret Pack Title"))
		env.finish(scope)
		env.finish(env.import("bad".toByteArray(), Recorder(), fileName = "Private Song.zip", usage = usage(PackImportSource.FILE)))

		val text = sink.events.joinToString()
		assertTrue(text, listOf("Secret", "Private", env.root.path, "test.invalid", ".zip").none { it in text })
	}

	private companion object {
		const val URL = "https://test.invalid/faded.zip"
	}
}
