package com.kimjisub.launchpad.manager

import com.kimjisub.launchpad.unipack.UniPackFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.util.zip.CRC32
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackArchiveTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private val cp949 = Charset.forName("MS949")

	private val packFiles = linkedMapOf(
		"info" to "title=Pack\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n",
		"keySound" to "1 1 1 소리.wav\n",
		"sounds/" to null,
		"sounds/소리.wav" to "sound",
	)

	/** A zip whose entry names are written in [charset]; java.util.zip sets the UTF-8 flag only for UTF-8. */
	private fun zip(charset: Charset, entries: Map<String, String?>, utf8Flag: Boolean = true): File {
		val bytes = ByteArrayOutputStream().also { out ->
			ZipOutputStream(out, charset).use { zip ->
				for ((name, text) in entries) {
					zip.putNextEntry(ZipEntry(name))
					text?.let { zip.write(it.toByteArray()) }
					zip.closeEntry()
				}
			}
		}.toByteArray()
		if (!utf8Flag) clearUtf8Flag(bytes)
		return tmp.newFile().apply { writeBytes(bytes) }
	}

	/** Clears general purpose bit 11 in every local and central header, as tools that never set it write. */
	private fun clearUtf8Flag(bytes: ByteArray) {
		for (i in 0 until bytes.size - 4) {
			if (bytes[i] != 'P'.code.toByte() || bytes[i + 1] != 'K'.code.toByte()) continue
			val flagOffset = when {
				bytes[i + 2] == 3.toByte() && bytes[i + 3] == 4.toByte() -> i + 7
				bytes[i + 2] == 1.toByte() && bytes[i + 3] == 2.toByte() -> i + 9
				else -> continue
			}
			bytes[flagOffset] = (bytes[flagOffset].toInt() and 0x08.inv()).toByte()
		}
	}

	private fun extracted(archive: File): File = tmp.newFolder().also { PackArchive.extract(archive, it) }

	private fun assertKoreanSoundFound(dir: File) {
		assertEquals("sound", File(dir, "sounds/소리.wav").readText())
		val pack = UniPackFolder(dir).load().loadDetail()
		assertNull(pack.errorDetail)
		assertEquals(1, pack.soundCount)
	}

	private fun unicodePathArchive(path: String, validChecksum: Boolean = true, version: Byte = 1): File {
		val original = "sounds/legacy.wav"
		val checksum = CRC32().apply { update(original.toByteArray()) }.value
		val name = path.toByteArray(Charsets.UTF_8)
		val extra = ByteBuffer.allocate(9 + name.size).order(ByteOrder.LITTLE_ENDIAN)
			.putShort(0x7075.toShort()).putShort((5 + name.size).toShort()).put(version)
			.putInt((if (validChecksum) checksum else checksum + 1).toInt()).put(name).array()
		val bytes = ByteArrayOutputStream().also { out ->
			ZipOutputStream(out, Charsets.ISO_8859_1).use { zip ->
				zip.putNextEntry(ZipEntry(original).apply { this.extra = extra })
				zip.write("sound".toByteArray())
				zip.closeEntry()
			}
		}.toByteArray()
		return tmp.newFile().apply { writeBytes(bytes) }
	}

	@Test
	fun unicodePathExtraField_preservesTheWrittenName() {
		val dir = extracted(unicodePathArchive("sounds/caf\u00e9.wav"))
		assertEquals("sound", File(dir, "sounds/caf\u00e9.wav").readText())
		assertFalse(File(dir, "sounds/legacy.wav").exists())
	}

	@Test
	fun invalidUnicodePathChecksum_usesTheOriginalName() {
		assertEquals("sound", File(extracted(unicodePathArchive("sounds/other.wav", false)), "sounds/legacy.wav").readText())
	}

	@Test
	fun unknownUnicodePathVersion_usesTheOriginalName() {
		assertEquals("sound", File(extracted(unicodePathArchive("sounds/other.wav", version = 2)), "sounds/legacy.wav").readText())
	}

	@Test
	fun unicodePathOutsideTheFolder_isRefused() {
		assertThrows(Exception::class.java) { extracted(unicodePathArchive("../escaped")) }
	}

	@Test
	fun cp949NamesWithoutTheUtf8Flag_areReadAsCp949() {
		assertKoreanSoundFound(extracted(zip(cp949, packFiles)))
	}

	@Test
	fun utf8NamesWithTheUtf8Flag_areReadAsUtf8() {
		assertKoreanSoundFound(extracted(zip(Charsets.UTF_8, packFiles)))
	}

	@Test
	fun utf8NamesWithoutTheUtf8Flag_areReadAsUtf8() {
		assertKoreanSoundFound(extracted(zip(Charsets.UTF_8, packFiles, utf8Flag = false)))
	}

	@Test
	fun asciiNamesAndFolders_extractAsBefore() {
		val dir = extracted(zip(Charsets.UTF_8, mapOf("Pack/" to null, "Pack/info" to "title=Pack", "Pack/empty/" to null)))

		assertEquals("title=Pack", File(dir, "Pack/info").readText())
		assertEquals(true, File(dir, "Pack/empty").isDirectory)
	}

	@Test
	fun anEntryOutsideTheFolder_isRefused() {
		val parent = tmp.newFolder()
		val destination = File(parent, "staged").apply { mkdirs() }

		assertThrows(Exception::class.java) {
			PackArchive.extract(zip(Charsets.UTF_8, mapOf("../escaped" to "x")), destination)
		}
		assertFalse(File(parent, "escaped").exists())
	}

	@Test
	fun aFolderEntryOutsideTheFolder_isRefused() {
		val parent = tmp.newFolder()
		val destination = File(parent, "staged").apply { mkdirs() }

		assertThrows(Exception::class.java) {
			PackArchive.extract(zip(Charsets.UTF_8, mapOf("../escaped/" to null)), destination)
		}
		assertFalse(File(parent, "escaped").exists())
	}
}
