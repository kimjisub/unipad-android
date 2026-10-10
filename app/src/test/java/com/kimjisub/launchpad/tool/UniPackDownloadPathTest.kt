package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.api.BaseApiService
import com.kimjisub.launchpad.api.file.FileApi
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.contentHashes
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.expectedHashes
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.packZip
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Recorder
import com.sun.net.httpserver.HttpServer
import io.mockk.every
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.io.IOException
import net.lingala.zip4j.exception.ZipException
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/** Real HTTP transport and archive processing, with only the Android file/notification APIs mocked. */
class UniPackDownloadPathTest {
	private val env = PackInstallTestEnv()
	private lateinit var server: HttpServer
	private lateinit var baseUrl: String

	@Before
	fun setUp() {
		env.setUp()
		server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
		baseUrl = "http://127.0.0.1:${server.address.port}/"
		every { FileApi.service } returns BaseApiService.createRetrofitService(baseUrl, FileApi.FileService::class.java)
		server.start()
	}

	@After
	fun tearDown() {
		if (::server.isInitialized) server.stop(0)
		env.tearDown()
	}

	private fun serve(status: Int, bytes: ByteArray, announcedLength: Long = bytes.size.toLong()) {
		server.createContext("/pack.zip") { exchange ->
			exchange.responseHeaders.add("Content-Type", "application/zip")
			exchange.sendResponseHeaders(status, announcedLength)
			try {
				exchange.responseBody.write(bytes)
			} finally {
				// A deliberately truncated response can throw on close after writing its partial body.
				runCatching { exchange.close() }
			}
		}
	}

	@Test
	fun downloadedAndLocallyImportedPackHaveTheSameFiles() {
		val zip = packZip("Self-authored fixture")
		serve(200, zip)
		val downloaded = Recorder()
		val imported = Recorder()

		env.finish(env.download(baseUrl + "pack.zip", downloaded))
		env.finish(env.import(zip, imported))

		assertNull(downloaded.error)
		assertNull(imported.error)
		assertEquals(expectedHashes("Self-authored fixture"), contentHashes(requireNotNull(downloaded.installedFolder)))
		assertEquals(contentHashes(requireNotNull(downloaded.installedFolder)), contentHashes(requireNotNull(imported.installedFolder)))
		assertEquals(listOf("pack", "pack (2)"), env.workspaceContents())
	}

	// Listing workspaces creates folders; resolved by the screen that started the install, it ran
	// on the main thread.
	@Test
	fun downloadAndImportResolveTheWorkspaceOnTheirIoThread() {
		val zip = packZip("Self-authored fixture")
		serve(200, zip)

		env.finish(env.download(baseUrl + "pack.zip", Recorder()))
		env.finish(env.import(zip, Recorder()))

		assertEquals(2, env.workspaceResolvedOn.size)
		assertTrue(
			env.workspaceResolvedOn.toString(),
			env.workspaceResolvedOn.all { it.startsWith("DefaultDispatcher-worker") },
		)
	}

	@Test
	fun serverErrorReportsFailureAndRemovesTemporaryArchive() {
		serve(503, "Service unavailable".toByteArray())
		val result = Recorder()

		env.finish(env.download(baseUrl + "pack.zip", result))

		assertNotNull(result.error)
		assertTrue("${result.error}", result.error is IOException)
		assertTrue("${result.error}", result.error?.message?.contains("HTTP 503") == true)
		assertNull(result.installedFolder)
		assertEquals(emptyList<String>(), env.workspaceContents())
	}

	@Test
	fun interruptedResponseReportsFailureAndRemovesPartialArchive() {
		val zip = packZip("Interrupted fixture")
		serve(200, zip.copyOf(zip.size / 2), zip.size.toLong())
		val result = Recorder()

		env.finish(env.download(baseUrl + "pack.zip", result))

		assertNotNull(result.error)
		assertTrue("${result.error}", result.error is IOException)
		assertNull(result.installedFolder)
		assertEquals(emptyList<String>(), env.workspaceContents())
	}

	@Test
	fun successfulHttpResponseContainingHtmlDoesNotInstallAsAPack() {
		serve(200, "<html>Download page</html>".toByteArray())
		val result = Recorder()

		env.finish(env.download(baseUrl + "pack.zip", result))

		assertNotNull(result.error)
		assertTrue("${result.error}", result.error is ZipException)
		assertNull(result.installedFolder)
		assertEquals(emptyList<String>(), env.workspaceContents())
	}
}
