package com.kimjisub.launchpad.unipack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UniPackFolderMissingFileTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private fun createPack(): File {
		val root = tmp.newFolder("pack")
		File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n")
		File(root, "keySound").writeText("1 1 1 a.wav\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		File(root, "keyLed").mkdir()
		File(root, "keyLed/1 1 1 1").writeText("o 1 1 3\nd 100\n")
		File(root, "autoPlay").writeText("o 1 1\nd 100\n")
		return root
	}

	private fun errors(pack: UniPack): String = pack.errorDetail.orEmpty()

	@Test
	fun intactPack_loadsWithoutErrors() {
		val pack = UniPackFolder(createPack()).load().loadDetail()

		assertNull(pack.errorDetail)
		assertFalse(pack.criticalError)
		assertEquals(1, pack.soundCount)
		assertEquals(1, pack.ledTableCount)
		assertEquals(2, pack.autoPlayTable?.elements?.size)
	}

	@Test
	fun autoPlayFileDeletedAfterCheckFile_isRecordedAsError() {
		val root = createPack()
		val pack = UniPackFolder(root).load()
		assertTrue(File(root, "autoPlay").delete())

		pack.loadDetail()

		assertTrue(errors(pack), errors(pack).contains("autoPlay : file was not found"))
		assertFalse(pack.criticalError)
		assertTrue(pack.detailLoaded)
		assertEquals(1, pack.soundCount)
		assertEquals(1, pack.ledTableCount)
		assertNotNull(pack.autoPlayTable)
		assertEquals(0, pack.autoPlayTable?.elements?.size)
	}

	@Test
	fun keyLedFileUnreadable_isRecordedAsError() {
		val root = createPack()
		val pack = UniPackFolder(root).load()
		val ledFile = File(root, "keyLed/1 1 1 1")
		ledFile.setReadable(false, false)
		assumeFalse("cannot make file unreadable (running as root?)", ledFile.canRead())

		try {
			pack.loadDetail()
		} finally {
			ledFile.setReadable(true, false)
		}

		assertTrue(errors(pack), errors(pack).contains("keyLed : [1 1 1 1] file was not found"))
		assertFalse(pack.criticalError)
		assertTrue(pack.detailLoaded)
		assertEquals(1, pack.soundCount)
		assertEquals(0, pack.ledTableCount)
		assertEquals(2, pack.autoPlayTable?.elements?.size)
	}

	@Test
	fun keySoundFileDeletedAfterCheckFile_isCriticalError() {
		val root = createPack()
		val pack = UniPackFolder(root).load()
		assertTrue(File(root, "keySound").delete())

		pack.loadDetail()

		assertTrue(errors(pack), errors(pack).contains("keySound : file was not found"))
		assertTrue(pack.criticalError)
		assertEquals(0, pack.soundCount)
	}

	@Test
	fun infoFileDeletedAfterCheckFile_isCriticalError() {
		val root = createPack()
		val pack = UniPackFolder(root)
		pack.checkFile()
		assertTrue(File(root, "info").delete())

		pack.loadInfo()

		assertTrue(errors(pack), errors(pack).contains("info : file was not found"))
		assertTrue(pack.criticalError)
	}
}
