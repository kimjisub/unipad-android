package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.analytics.PackImportSource
import com.kimjisub.launchpad.analytics.RecordingUsageSink
import com.kimjisub.launchpad.analytics.UsageEvent
import com.kimjisub.launchpad.analytics.UsageParam
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.PACK_NAME
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.contentHashes
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.packFiles
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.writePack
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Recorder
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Real importer/downloader and zip4j writes; only the selected output write runs out of space. */
class PackStorageFailureTest {
	private val env = PackInstallTestEnv()
	private val sink = RecordingUsageSink()
	private lateinit var original: File
	private lateinit var originalHash: String
	private lateinit var protectedTree: Map<String, String>
	private val archive = archive("New", 64 * 1024)
	private var failureReached = false

	@Before
	fun setUp() {
		env.setUp()
		writePack(File(env.workspace, PACK_NAME), "Existing")
		File(env.workspace, ".hidden/empty").mkdirs()
		File(env.workspace, ".hidden/notes").writeText("Keep my notes")
		File(env.workspace, "empty").mkdirs()
		original = File(env.root, "$PACK_NAME.zip").apply {
			writeBytes(archive)
			check(setReadOnly())
		}
		originalHash = hash(original)
		protectedTree = tree(env.workspace)
	}

	@After
	fun tearDown() = env.tearDown()

	@Test
	fun cacheCopyWriteFailurePreservesTheSourceAndExistingFoldersAndCanBeRetried() =
		checkFailure(Source.FILE, Point.CACHE)

	@Test
	fun importUnpackWriteFailurePreservesTheSourceAndExistingFoldersAndCanBeRetried() =
		checkFailure(Source.FILE, Point.UNPACK)

	@Test
	fun downloadWriteFailurePreservesExistingFoldersAndCanBeRetried() =
		checkFailure(Source.STORE, Point.DOWNLOAD)

	@Test
	fun storeUnpackWriteFailurePreservesExistingFoldersAndCanBeRetried() =
		checkFailure(Source.STORE, Point.UNPACK)

	@Test
	fun aSecondImportUnpackFailureAlsoPreservesTheFirstInstalledPack() {
		val first = Recorder()
		env.finish(env.import(PackInstallTestEnv.packZip("First"), first))
		assertNull(first.error)
		assertEquals("$PACK_NAME (2)", first.installedFolder?.name)
		assertEquals(PackInstallTestEnv.expectedHashes("First"), contentHashes(first.installedFolder!!))
		protectedTree = tree(env.workspace)
		checkFailure(Source.FILE, Point.UNPACK, retryName = "$PACK_NAME (3)")
	}

	private fun checkFailure(source: Source, point: Point, retryName: String = "$PACK_NAME (2)") {
		failWrite(point)
		val failed = runAttempt(source, "failure")

		assertTrue("the selected output write never failed", failureReached)
		assertNull("the screen was told installation succeeded", failed.installedFolder)
		assertNotNull("the screen's failure callback was not called", failed.error)
		assertTrue("${failed.error}", generateSequence(failed.error) { it.cause }.any { it.message?.contains(NO_SPACE) == true })
		assertEquals(listOf(sink.pack(UsageEvent.PACK_IMPORT,
			UsageParam.RESULT to "failure", UsageParam.IMPORT_SOURCE to source.value,
			UsageParam.ERROR_TYPE to "storage")), sink.events)
		assertProtectedAndClean()

		unmockkConstructor(FileOutputStream::class)
		val retry = runAttempt(source, "retry")
		assertNull("retry failed: ${retry.error}", retry.error)
		assertEquals(retryName, retry.installedFolder?.name)
		assertEquals(archiveFiles("New", 64 * 1024).mapValues { sha256(it.value) }, contentHashes(retry.installedFolder!!))
		assertEquals(protectedTree, tree(env.workspace).filterKeys { it != retryName && !it.startsWith("$retryName/") })
		assertEquals(originalHash, hash(original))
		assertClean()
		assertEquals(listOf("failure", "success"), sink.events.map { it.parameters[UsageParam.RESULT] })
	}

	private fun runAttempt(source: Source, attempt: String): Recorder {
		val recorder = Recorder()
		val usage = sink.analytics.packImport(if (source == Source.FILE) PackImportSource.FILE else PackImportSource.STORE)
		if (source == Source.FILE) {
			// The picked provider exposes only an input stream; never a writable descriptor.
			env.finish(env.import(archive, recorder, usage = usage, openInput = { original.inputStream() }))
		} else {
			val url = "https://test.invalid/$attempt.zip"
			val scope = env.download(url, recorder, usage = usage)
			env.gate(url).open(archive)
			env.finish(scope)
		}
		return recorder
	}

	private fun failWrite(point: Point) {
		mockkConstructor(FileOutputStream::class)
		every { anyConstructed<FileOutputStream>().write(any<ByteArray>(), any(), any()) } answers {
			// Let the real native write put bytes on disk before failing, never replace extraction.
			callOriginal()
			val partial = when (point) {
				Point.CACHE -> env.cache.listFiles().orEmpty().firstOrNull { it.name.startsWith("unipack_import_") && it.length() > 0 }
				Point.DOWNLOAD -> env.workspace.listFiles().orEmpty().firstOrNull { it.extension == "zip" && it.length() > 0 }
				Point.UNPACK -> File(env.workspace, ".unipad-staging").walkTopDown().firstOrNull { it.isFile && it.name == "a.wav" && it.length() > 0 }
			}
			if (partial != null) {
				assertTrue("failure came after the whole output was written", partial.length() < if (point == Point.UNPACK) 64 * 1024 else archive.size)
				if (point == Point.UNPACK) {
					val folder = partial.parentFile!!.parentFile!!
					assertEquals(sha256(archiveFiles("New", 64 * 1024).getValue("info")), hash(File(folder, "info")))
					assertEquals(sha256(archiveFiles("New", 64 * 1024).getValue("keySound")), hash(File(folder, "keySound")))
				}
				failureReached = true
				throw IOException("write failed: ENOSPC ($NO_SPACE)")
			}
		}
	}

	private fun assertProtectedAndClean() {
		assertEquals(originalHash, hash(original))
		assertEquals(protectedTree, tree(env.workspace))
		assertClean()
	}

	private fun assertClean() {
		assertFalse(File(env.workspace, ".unipad-staging").exists())
		assertEquals(emptyList<String>(), env.cache.list().orEmpty().toList())
		assertFalse(env.workspace.listFiles().orEmpty().any { it.extension == "zip" })
	}

	private enum class Source(val value: String) { FILE("file"), STORE("store") }
	private enum class Point { CACHE, UNPACK, DOWNLOAD }

	companion object {
		private const val NO_SPACE = "No space left on device"

		/** Includes directories as well as hashes, so hidden and empty user folders are checked. */
		fun tree(root: File): Map<String, String> = root.walkTopDown().filter { it != root }.associate {
			it.relativeTo(root).invariantSeparatorsPath to if (it.isDirectory) "directory" else hash(it)
		}

		private fun hash(file: File): String = sha256(file.readBytes())
		private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

		fun archiveFiles(title: String, soundSize: Int): Map<String, ByteArray> = packFiles(title) +
			("sounds/a.wav" to ByteArray(soundSize).apply {
				ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).apply {
					put("RIFF".toByteArray())
					putInt(soundSize - 8)
					put("WAVEfmt ".toByteArray())
					putInt(16)
					putShort(1)
					putShort(1)
					putInt(8000)
					putInt(16000)
					putShort(2)
					putShort(16)
					put("data".toByteArray())
					putInt(soundSize - 44)
				}
			})

		/** Stored entries keep silent data large enough for multiple real output writes. */
		fun archive(title: String, soundSize: Int): ByteArray {
			val bytes = ByteArrayOutputStream()
			ZipOutputStream(bytes).use { zip ->
				archiveFiles(title, soundSize).forEach { (name, data) ->
					zip.putNextEntry(ZipEntry(name).apply {
						method = ZipEntry.STORED
						size = data.size.toLong()
						crc = CRC32().apply { update(data) }.value
					})
					zip.write(data)
					zip.closeEntry()
				}
			}
			return bytes.toByteArray()
		}
	}
}
