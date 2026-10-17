package com.kimjisub.launchpad.unipack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UniPackFolderInfoTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private fun load(info: String): UniPack {
		val root = tmp.newFolder()
		File(root, "info").writeText(info)
		File(root, "keySound").writeText("1 1 1 a.wav\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		return UniPackFolder(root).load()
	}

	@Test
	fun lineWithoutEquals_isSkippedWithoutWarning() {
		val pack = load("title=Test\nA wrapped\ndescription line\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n")

		assertNull(pack.errorDetail)
		assertFalse(pack.criticalError)
		assertEquals("Test", pack.title)
		assertEquals(8, pack.buttonX)
	}

	@Test
	fun gridSizeNotANumber_refusesPackWithOneMessage() {
		val pack = load("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=abc\nchain=1\n")

		assertTrue(pack.criticalError)
		assertEquals("info : buttonY is not a number", pack.errorDetail)
	}

	@Test
	fun repeatedGridSize_usesLastValue() {
		val pack = load("title=Test\nproducerName=Tester\nbuttonX=abc\nbuttonX=8\nbuttonY=8\nchain=1\n")

		assertNull(pack.errorDetail)
		assertFalse(pack.criticalError)
		assertEquals(8, pack.buttonX)
	}
}
