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
	fun collidingSoundNames_keepBothBytesAfterUnwrapMoveDeleteAndReimport() {
		val pairs = listOf("end.wav" to "END.wav", "café.wav" to "cafe\u0301.wav",
			"가.wav" to "\u1100\u1161.wav", "fx/a.wav" to "FX/a.wav")
		for ((first, second) in pairs) {
			val archive = zip(Charsets.UTF_8, linkedMapOf(
				"Pack/info" to packFiles.getValue("info"),
				"Pack/keySound" to "1 1 1 $first\n1 1 2 $second\n",
				"Pack/sounds/$first" to "first", "Pack/sounds/$second" to "second"))
			repeat(2) {
				val dir = extracted(archive)
				FileManager.removeDoubleFolder(dir.path)
				val moved = File(tmp.newFolder(), "moved")
				FileManager.moveDirectory(dir, moved)
				val pack = UniPackFolder(moved).load().loadDetail()
				assertNull(pack.errorDetail)
				assertEquals("first", pack.soundTable!![0][0][0]!!.first().file.readText())
				assertEquals("second", pack.soundTable!![0][0][1]!!.first().file.readText())
				assertEquals("wav", pack.soundTable!![0][0][1]!!.first().file.extension)
				assertEquals(first.substringAfterLast('/'), pack.soundTable!![0][0][0]!!.first().originalName)
				assertEquals(second.substringAfterLast('/'), pack.soundTable!![0][0][1]!!.first().originalName)
				assertEquals(true, pack.delete())
				assertFalse(moved.exists())
			}
		}
	}

	@Test
	fun caseFallback_usesOriginalFullNamesNotAllocatedStorageNames() {
		val dir = extracted(zip(Charsets.UTF_8, linkedMapOf(
			"info" to packFiles.getValue("info"), "keySound" to "1 1 1 End.wav\n",
			"sounds/end.wav" to "lower", "sounds/END.wav" to "upper")))
		val pack = UniPackFolder(dir).load().loadDetail()
		assertNull(pack.errorDetail)
		assertEquals("upper", pack.soundTable!![0][0][0]!!.first().file.readText())
		assertEquals("END.wav", pack.soundTable!![0][0][0]!!.first().originalName)
	}

	@Test
	fun collidingKeyLedNames_keepBothAnimationsAndTheirOriginalQueueOrder() {
		val dir = extracted(zip(Charsets.UTF_8, linkedMapOf(
			"info" to packFiles.getValue("info"), "keySound" to "",
			"keyLED/1 1 1 1 café" to "on 1 1 auto 5\n", "keyLED/1 1 1 1 cafe\u0301" to "on 1 1 auto 6\n")))
		val pack = UniPackFolder(dir).load().loadDetail()
		assertNull(pack.errorDetail)
		assertEquals(listOf(5, 6), pack.ledAnimationTable!![0][0][0]!!.map {
			(it.ledEvents.first() as com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent.On).velocity
		})
	}

	@Test
	fun collidingAutoPlayNames_preserveBothFilesAndPreferTheExactConventionalName() {
		val dir = extracted(zip(Charsets.UTF_8, linkedMapOf(
			"info" to packFiles.getValue("info"), "keySound" to "",
			"autoPlay" to "delay 10\n", "AUTOPLAY" to "delay 20\n")))
		val pack = UniPackFolder(dir).load().loadDetail()
		assertEquals("delay 10\n", (pack as UniPackFolder).autoPlayFile!!.readText())
		assertEquals(true, dir.walkTopDown().filter { it.isFile }.any { it.readText() == "delay 20\n" })
	}

	@Test
	fun ordinaryPack_hasExactlyItsOriginalLayoutIncludingEmptyFolders() {
		val entries = packFiles + linkedMapOf("empty/" to null, "sounds/.unipad-entry-1" to "ordinary")
		val dir = extracted(zip(Charsets.UTF_8, entries))
		assertEquals(entries.keys.map { it.trimEnd('/') }.sorted(), dir.walkTopDown().drop(1)
			.map { it.relativeTo(dir).invariantSeparatorsPath }.toList().sorted())
	}

	@Test
	fun generatedNames_neverOverwriteAnOriginalWithTheSameName() {
		val dir = extracted(zip(Charsets.UTF_8, linkedMapOf(
			"info" to packFiles.getValue("info"),
			"keySound" to "1 1 1 end.wav\n1 1 2 END.wav\n1 1 3 .unipad-entry-1\n",
			"sounds/end.wav" to "lower", "sounds/END.wav" to "upper",
			"sounds/.unipad-entry-1" to "original", "sounds/.unipad-filenames.json" to "user file")))
		val pack = UniPackFolder(dir).load().loadDetail()
		assertNull(pack.errorDetail)
		assertEquals(listOf("lower", "upper", "original"), (0..2).map {
			pack.soundTable!![0][0][it]!!.first().file.readText()
		})
		assertEquals(true, dir.walkTopDown().any { it.isFile && it.readText() == "user file" })
	}

	@Test
	fun aBackupArchiveOfAnInstalledPack_keepsTheOriginalNames() {
		val first = extracted(zip(Charsets.UTF_8, linkedMapOf(
			"info" to packFiles.getValue("info"), "keySound" to "1 1 1 end.wav\n1 1 2 END.wav\n",
			"sounds/end.wav" to "lower", "sounds/END.wav" to "upper")))
		val backup = first.walkTopDown().filter { it.isFile }.associate {
			it.relativeTo(first).invariantSeparatorsPath to it.readText()
		}
		val pack = UniPackFolder(extracted(zip(Charsets.UTF_8, backup))).load().loadDetail()
		assertNull(pack.errorDetail)
		assertEquals("lower", pack.soundTable!![0][0][0]!!.first().file.readText())
		assertEquals("upper", pack.soundTable!![0][0][1]!!.first().file.readText())
	}

	@Test
	fun collidingPackDirectories_areNotMistakenForOneWrappedPack() {
		val dir = extracted(zip(Charsets.UTF_8, linkedMapOf(
			"Pack/info" to packFiles.getValue("info"), "Pack/keySound" to "",
			"pack/info" to packFiles.getValue("info"), "pack/keySound" to "")))
		FileManager.removeDoubleFolder(dir.path)
		assertEquals(2, dir.listFiles()!!.count { it.isDirectory })
		assertEquals(true, UniPackFolder(dir).load().criticalError)
	}

	@Test
	fun caseDistinctSoundsDirectories_areBothSearchedByTheirOriginalPaths() {
		val dir = extracted(zip(Charsets.UTF_8, linkedMapOf(
			"info" to packFiles.getValue("info"), "keySound" to "1 1 1 end.wav\n1 1 2 END.wav\n",
			"sounds/end.wav" to "lower", "SOUNDS/END.wav" to "upper")))
		val pack = UniPackFolder(dir).load().loadDetail()
		assertNull(pack.errorDetail)
		assertEquals("lower", pack.soundTable!![0][0][0]!!.first().file.readText())
		assertEquals("upper", pack.soundTable!![0][0][1]!!.first().file.readText())
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
		val dir = extracted(zip(Charsets.UTF_8, mapOf("./" to null, "Pack/" to null, "Pack/info" to "title=Pack", "Pack/empty/" to null)))

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
