package com.kimjisub.launchpad.unipack

import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The queue order of several keyLed files on one pad. Android keeps a name as the archive or download
 * wrote it, so the same letters can arrive composed (U+00F1, U+AC00) or decomposed (n + U+0303,
 * U+1100 U+1161). The order is taken here from the names themselves: the JVM on macOS lists names
 * composed, so a test that lists a folder there cannot see a decomposed name.
 */
class UniPackFolderKeyLedOrderTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private fun order(vararg names: String): List<String> =
		inKeyLedOrder(names.map { File(it) }.toTypedArray()).map { it.name }

	@Test
	fun decomposedLatinQueuesWhereItsComposedFormDoes() {
		assertEquals(listOf("1 1 1 1 o", "1 1 1 1 n\u0303"), order("1 1 1 1 n\u0303", "1 1 1 1 o"))
		assertEquals(listOf("1 1 1 1 o", "1 1 1 1 \u00f1"), order("1 1 1 1 \u00f1", "1 1 1 1 o"))
	}

	@Test
	fun decomposedHangulQueuesWhereItsComposedFormDoes() {
		assertEquals(listOf("1 1 1 1 \u3131", "1 1 1 1 \u1100\u1161"), order("1 1 1 1 \u1100\u1161", "1 1 1 1 \u3131"))
		assertEquals(listOf("1 1 1 1 \u3131", "1 1 1 1 \uac00"), order("1 1 1 1 \uac00", "1 1 1 1 \u3131"))
	}

	@Test
	fun namesCompareByUtf16CodeUnits() {
		assertEquals(listOf("1 1 1 1 \ud83d\ude00", "1 1 1 1 \ufffd"), order("1 1 1 1 \ufffd", "1 1 1 1 \ud83d\ude00"))
	}

	@Test
	fun equalKeysKeepTheListingOrder() {
		assertEquals(listOf("1 1 1 1 B", "1 1 1 1 b"), order("1 1 1 1 B", "1 1 1 1 b"))
		assertEquals(listOf("1 1 1 1 b", "1 1 1 1 B"), order("1 1 1 1 b", "1 1 1 1 B"))
		assertEquals(listOf("1 1 1 1 n\u0303", "1 1 1 1 \u00f1"), order("1 1 1 1 n\u0303", "1 1 1 1 \u00f1"))
		assertEquals(listOf("1 1 1 1 \u00f1", "1 1 1 1 n\u0303"), order("1 1 1 1 \u00f1", "1 1 1 1 n\u0303"))
	}

	@Test
	fun theSortedFilesAreTheListedOnes() {
		val decomposed = File("1 1 1 1 \u1100\u1161")
		val sorted = inKeyLedOrder(arrayOf(decomposed, File("1 1 1 1 \u3131")))
		assertSame(decomposed, sorted[1])
		assertEquals("1 1 1 1 \u1100\u1161", sorted[1].name)
	}

	@Test
	fun thePackReadsEachFileUnderItsStoredName() {
		val root = tmp.newFolder("pack")
		File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n")
		File(root, "keySound").writeText("1 1 1 a.wav\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		File(root, "keyLed").mkdir()
		File(root, "keyLed/1 1 1 1 \u1100\u1161").writeText("d 20\n")
		File(root, "keyLed/1 1 1 1 \u3131").writeText("d 10\n")

		val pack = UniPackFolder(root).load().loadDetail()

		assertNull(pack.errorDetail)
		val delays = pack.ledAnimationTable!![0][0][0]!!.map { (it.ledEvents.single() as LedEvent.Delay).delay }
		assertEquals(listOf(10, 20), delays)
	}
}
