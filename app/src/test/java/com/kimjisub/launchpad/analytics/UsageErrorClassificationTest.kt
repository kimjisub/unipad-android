package com.kimjisub.launchpad.analytics

import com.kimjisub.launchpad.tool.PackInstallTestEnv
import net.lingala.zip4j.ZipFile
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.net.ProtocolException
import java.net.ServerSocket
import java.util.zip.ZipException
import kotlin.concurrent.thread
import net.lingala.zip4j.exception.ZipException as Zip4jException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod

/**
 * Where a failure surfaced decides its category: the same [IOException] means a broken connection
 * while a page or pack is fetched and a disk problem while it is written, and an unpack error only
 * means a corrupt archive when the archive itself is at fault.
 */
class UsageErrorClassificationTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private val sink = RecordingUsageSink()
	private val readOnlyFolders = mutableListOf<File>()

	@After
	fun restoreWriteAccess() = readOnlyFolders.forEach { it.setWritable(true) }

	private fun unpack(zip: ByteArray, into: File = tmp.newFolder()) {
		val file = tmp.newFile().apply { writeBytes(zip) }
		ZipFile(file).use { it.extractAll(into.path) }
	}

	private fun thrownBy(what: String = "the call", block: () -> Unit): Throwable {
		try {
			block()
		} catch (e: Throwable) {
			return e
		}
		throw AssertionError("$what did not throw")
	}

	private fun readOnlyFolder(): File = tmp.newFolder().also {
		it.setWritable(false)
		readOnlyFolders += it
	}

	/** The same healthy pack in the layouts packs arrive in; zip4j fails differently on each. */
	private val packShapes = mapOf(
		"root files first" to PackInstallTestEnv.packZip("Faded"),
		"folder entries" to PackInstallTestEnv.packZip("Faded", directoryEntries = true),
		"top folder" to PackInstallTestEnv.packZip("Faded", topFolder = "Faded"),
		"top folder and folder entries (Finder)" to PackInstallTestEnv.packZip("Faded", topFolder = "Faded", directoryEntries = true),
	)

	/** Flips the compressed bytes of [entry] while every header stays readable. */
	private fun damagedBody(zip: ByteArray, entry: String): ByteArray {
		val header = ZipFile(tmp.newFile().apply { writeBytes(zip) }).use { it.getFileHeader(entry) }
		val local = header.offsetLocalHeader.toInt()
		val nameLength = (zip[local + 26].toInt() and 0xFF) or ((zip[local + 27].toInt() and 0xFF) shl 8)
		val extraLength = (zip[local + 28].toInt() and 0xFF) or ((zip[local + 29].toInt() and 0xFF) shl 8)
		val body = local + LOCAL_HEADER_SIZE + nameLength + extraLength
		return zip.copyOf().also { for (i in body until body + header.compressedSize.toInt()) it[i] = (it[i].toInt() xor 0x5A).toByte() }
	}

	private fun encrypted(zip: ByteArray): ByteArray {
		val plain = tmp.newFolder().also { unpack(zip, into = it) }
		val locked = File(tmp.newFolder(), "locked.zip")
		val parameters = ZipParameters().apply {
			isEncryptFiles = true
			encryptionMethod = EncryptionMethod.ZIP_STANDARD
		}
		ZipFile(locked, "secret".toCharArray()).use { zipFile -> plain.listFiles()!!.forEach { zipFile.addFolder(it, parameters) } }
		return locked.readBytes()
	}

	@Test
	fun everyPackShapeUnpacksIntoAWritableFolder() {
		for ((label, zip) in packShapes) {
			val target = tmp.newFolder()

			unpack(zip, into = target)

			assertEquals(label, PackInstallTestEnv.packFiles("Faded").size, target.walkTopDown().count { it.isFile })
		}
	}

	@Test
	fun aHealthyArchiveOfAnyShapeThatCannotBeWrittenIsAFileAccessFailureNotACorruptArchive() {
		val classified = packShapes.mapValues { (label, zip) ->
			val target = readOnlyFolder()
			assumeFalse("a process that can write anywhere cannot reproduce a permission error", target.canWrite())

			val error = thrownBy(label) { unpack(zip, into = target) }

			assertEquals(label, Zip4jException::class.java, error.javaClass)
			UsageErrorType.classify(error)
		}

		assertEquals(packShapes.mapValues { UsageErrorType.FILE_ACCESS }, classified)
	}

	@Test
	fun aFolderFailureCountsOnlyWhenZip4jReportsIt() {
		assertEquals(UsageErrorType.CORRUPT_ARCHIVE, UsageErrorType.classify(ZipException("Could not create directory: /x")))
		assertEquals(UsageErrorType.CORRUPT_ARCHIVE, UsageErrorType.classify(Zip4jException("Wrong password!", Zip4jException.Type.WRONG_PASSWORD)))
		assertEquals(
			UsageErrorType.CORRUPT_ARCHIVE,
			UsageErrorType.classify(Zip4jException("Could not create directory: /x", Zip4jException.Type.CHECKSUM_MISMATCH)),
		)
	}

	@Test
	fun anArchiveWrittenToAFullDiskIsAStorageFailure() {
		val noSpace = IOException("write failed: ENOSPC (No space left on device)")

		assertEquals(UsageErrorType.STORAGE, UsageErrorType.classify(Zip4jException(noSpace)))
		assertEquals(UsageErrorType.STORAGE, UsageErrorType.classify(Zip4jException("Extraction failed", noSpace)))
		assertEquals(UsageErrorType.STORAGE, UsageErrorType.classify(ZipException("Extraction failed").apply { initCause(noSpace) }))
	}

	@Test
	fun anArchiveWithoutWriteCauseIsStillACorruptArchive() {
		val healthy = PackInstallTestEnv.packZip("Faded")
		val flipped = healthy.copyOf().also { for (i in 40 until 60) it[i] = (it[i].toInt() xor 0x5A).toByte() }
		val finder = PackInstallTestEnv.packZip("Faded", topFolder = "Faded", directoryEntries = true)
		val realArchives = mapOf(
			"not a zip" to "not a zip".toByteArray(),
			"cut in half" to healthy.copyOf(healthy.size / 2),
			"flipped bytes" to flipped,
			"folders, cut in half" to finder.copyOf(finder.size / 2),
			"folders, damaged sound body" to damagedBody(finder, "Faded/sounds/a.wav"),
			"folders, damaged info body" to damagedBody(finder, "Faded/info"),
			"encrypted, no password" to encrypted(finder),
		)

		for ((label, bytes) in realArchives) {
			val error = thrownBy(label) { unpack(bytes) }
			assertEquals("$label: ${generateSequence(error) { it.cause }.toList()}", UsageErrorType.CORRUPT_ARCHIVE, UsageErrorType.classify(error))
		}
		assertEquals(UsageErrorType.CORRUPT_ARCHIVE, UsageErrorType.classify(Zip4jException("Zip headers not found. Probably not a zip file")))
		assertEquals(UsageErrorType.CORRUPT_ARCHIVE, UsageErrorType.classify(Zip4jException(EOFException("unexpected end of ZLIB input stream"))))
	}

	@Test
	fun aConnectionCutInTheNetworkStageIsANetworkFailure() {
		val cut = listOf(
			ProtocolException("unexpected end of stream"),
			EOFException(),
			IOException("unexpected end of stream on https://host/..."),
		)

		for (error in cut) {
			assertEquals("$error", UsageErrorType.NETWORK, UsageErrorType.classify(error, FailureStage.NETWORK))
		}
	}

	@Test
	fun theSameErrorsInTheFileStageAreNotNetworkFailures() {
		val error = listOf(ProtocolException("unexpected end of stream"), EOFException(), IOException("Could not open /x for writing"))

		for (e in error) {
			assertEquals("$e", UsageErrorType.FILE_ACCESS, UsageErrorType.classify(e))
			assertEquals("$e", UsageErrorType.FILE_ACCESS, UsageErrorType.classify(e, FailureStage.FILE))
		}
		assertEquals(UsageErrorType.STORAGE, UsageErrorType.classify(IOException("No space left on device"), FailureStage.FILE))
	}

	@Test
	fun aFailureKeepsItsSpecificCategoryInTheNetworkStage() {
		val http = com.kimjisub.launchpad.tool.UniPackDownloader.HttpStatusException(404, "HTTP 404")

		assertEquals(UsageErrorType.NOT_FOUND, UsageErrorType.classify(http, FailureStage.NETWORK))
		assertEquals(UsageErrorType.UNKNOWN, UsageErrorType.classify(IllegalStateException("boom"), FailureStage.NETWORK))
	}

	/** A server that promises a longer body than it sends, then hangs up, read by OkHttp as the app reads a pack. */
	@Test
	fun aRealConnectionCutMidBodyIsANetworkFailureOnlyInTheNetworkStage() {
		ServerSocket(0).use { server ->
			val serving = thread(isDaemon = true) {
				server.accept().use { socket ->
					socket.getInputStream().bufferedReader().let { reader -> while (reader.readLine()?.isNotEmpty() == true) Unit }
					socket.getOutputStream().apply {
						write("HTTP/1.1 200 OK\r\nContent-Length: 1000\r\n\r\nabc".toByteArray())
						flush()
					}
				}
			}
			val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/pack.zip").build()

			val error = thrownBy { OkHttpClient().newCall(request).execute().use { it.body.bytes() } }
			serving.join(5_000)

			assertNotNull(error.message)
			assertEquals("${error.javaClass.name}: ${error.message}", UsageErrorType.NETWORK, UsageErrorType.classify(error, FailureStage.NETWORK))
			assertEquals(UsageErrorType.FILE_ACCESS, UsageErrorType.classify(error, FailureStage.FILE))
		}
	}

	@Test
	fun theStageReachesTheReportedEvent() {
		sink.analytics.packImport(PackImportSource.STORE).failed(ProtocolException("unexpected end of stream"), FailureStage.NETWORK)
		sink.analytics.packImport(PackImportSource.FILE).failed(ProtocolException("unexpected end of stream"))

		assertEquals(
			listOf(
				sink.pack(UsageEvent.PACK_IMPORT, UsageParam.RESULT to "failure", UsageParam.IMPORT_SOURCE to "store", UsageParam.ERROR_TYPE to "network"),
				sink.pack(UsageEvent.PACK_IMPORT, UsageParam.RESULT to "failure", UsageParam.IMPORT_SOURCE to "file", UsageParam.ERROR_TYPE to "file_access"),
			),
			sink.events,
		)
	}

	private companion object {
		const val LOCAL_HEADER_SIZE = 30
	}
}
