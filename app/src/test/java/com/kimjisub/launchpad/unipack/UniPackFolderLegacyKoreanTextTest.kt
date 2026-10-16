package com.kimjisub.launchpad.unipack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.charset.Charset

/**
 * Text files that are neither UTF-8 nor BOM-marked UTF-16 are read as CP949, the "ANSI" encoding old
 * Korean Windows Notepad saved them in, as iOS reads them.
 */
class UniPackFolderLegacyKoreanTextTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private val cp949 = Charset.forName("MS949")

	private fun pack(info: ByteArray, keySound: ByteArray): UniPack {
		val root = tmp.newFolder("pack")
		File(root, "info").writeBytes(info)
		File(root, "keySound").writeBytes(keySound)
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		return UniPackFolder(root).load().loadDetail()
	}

	private val info = "title=봄날 연주\nproducerName=지은이\nbuttonX=8\nbuttonY=8\nchain=1\n"

	@Test
	fun cp949Info_readsTheKoreanTitleAndProducer() {
		val pack = pack(info.toByteArray(cp949), "1 1 1 a.wav\n".toByteArray(cp949))

		assertNull(pack.errorDetail)
		assertEquals("봄날 연주", pack.title)
		assertEquals("지은이", pack.producerName)
	}

	@Test
	fun cp949KeySoundWithAKoreanFileName_playsThatFile() {
		val root = tmp.newFolder("named")
		File(root, "info").writeBytes(info.toByteArray(cp949))
		File(root, "keySound").writeBytes("1 1 1 소리.wav\n".toByteArray(cp949))
		File(root, "sounds").mkdir()
		File(root, "sounds/소리.wav").writeText("")

		val pack = UniPackFolder(root).load().loadDetail()

		assertNull(pack.errorDetail)
		assertEquals("소리.wav", pack.soundGet(0, 0, 0)?.file?.name)
	}

	@Test
	fun utf8TextWithoutBom_staysUtf8() {
		val pack = pack(info.toByteArray(Charsets.UTF_8), "1 1 1 a.wav\n".toByteArray(Charsets.UTF_8))

		assertNull(pack.errorDetail)
		assertEquals("봄날 연주", pack.title)
	}

	@Test
	fun utf16WithBom_staysUtf16() {
		val pack = pack(
			byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + info.toByteArray(Charsets.UTF_16LE),
			"1 1 1 a.wav\n".toByteArray(Charsets.UTF_8),
		)

		assertNull(pack.errorDetail)
		assertEquals("봄날 연주", pack.title)
	}
}
