package com.kimjisub.launchpad.unipack

import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
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
import java.nio.file.Files

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

	/** The layout of a store pack such as _In_My_Mind: upper-case keyLED, an extra info key, c/d/t autoPlay. */
	private fun createStorePack(): File {
		val root = tmp.newFolder("_In_My_Mind")
		File(root, "info").writeText(
			"title=In My Mind\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=2\nsquareButton=true\nPackTool=Faça Unipack 9.21\n"
		)
		File(root, "keySound").writeText("1 1 1 a.wav\n1 1 2 a.wav\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		File(root, "keyLED").mkdir()
		File(root, "keyLED/1 1 1 1").writeText("o 1 1 a 5\nd 100\nf 1 1\n")
		File(root, "keyLED/1 1 2 1").writeText("o 1 2 a 5\nd 100\nf 1 2\n")
		File(root, "autoPlay").writeText("c 1\nd 985\nt 1 1\nd 2621\nt 1 2\n")
		return root
	}

	private val storePackAutoPlayElements = 7

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
	fun keyLedSubdirectories_areIgnoredWithoutErrorsAndDirectFilesStillLoad() {
		val root = createStorePack()
		val nested = File(root, "keyLED/sub")
		assertTrue(nested.mkdir())
		File(nested, "1 2 2 1").writeText("o 2 2 a 9\n")
		assertTrue(File(root, "keyLED/1 3 3 1").mkdir())

		val pack = UniPackFolder(root).load().loadDetail()

		assertNull(pack.errorDetail)
		assertFalse(pack.criticalError)
		assertEquals(2, pack.ledTableCount)
		assertNull(pack.ledGet(0, 1, 1))
		assertNull(pack.ledGet(0, 2, 2))
		for (y in 0..1) {
			val animation = pack.ledGet(0, 0, y)!!
			assertEquals(1, animation.loop)
			assertEquals(3, animation.ledEvents.size)
			val on = animation.ledEvents[0] as LedEvent.On
			assertEquals(0, on.x)
			assertEquals(y, on.y)
			assertEquals(5, on.velocity)
			assertEquals(100, (animation.ledEvents[1] as LedEvent.Delay).delay)
			val off = animation.ledEvents[2] as LedEvent.Off
			assertEquals(0, off.x)
			assertEquals(y, off.y)
		}
	}

	@Test
	fun keyLedBrokenSymbolicLink_isRecordedAsErrorAndDirectFilesStillLoad() {
		val root = createStorePack()
		val link = File(root, "keyLED/1 2 2 1").toPath()
		Files.createSymbolicLink(link, root.toPath().resolve("missing-led"))
		assertTrue(Files.isSymbolicLink(link))
		assertFalse(link.toFile().isFile)
		assertFalse(link.toFile().isDirectory)

		val pack = UniPackFolder(root).load().loadDetail()

		assertEquals("keyLed : 1 2 2 1 is not file", errors(pack))
		assertFalse(pack.criticalError)
		assertTrue(pack.detailLoaded)
		assertEquals(2, pack.ledTableCount)
		assertNotNull(pack.ledGet(0, 0, 0))
		assertNotNull(pack.ledGet(0, 0, 1))
		assertNull(pack.ledGet(0, 1, 1))
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
	fun storePack_loadsWithoutErrors() {
		val pack = UniPackFolder(createStorePack()).load().loadDetail()

		assertNull(pack.errorDetail)
		assertEquals(2, pack.soundCount)
		assertEquals(2, pack.ledTableCount)
		assertEquals(storePackAutoPlayElements, pack.autoPlayTable?.elements?.size)
	}

	// Crash reports f711f61c (4.1.3): autoPlay listed by checkFile, gone when opened after keySound/keyLed.
	@Test
	fun storePack_autoPlayRemovedJustBeforeOpen_isRecordedAsError() {
		val root = createStorePack()
		val pack = UniPackFolder(root).load()

		pack.loadDetailWithProgress { phase, _, _ ->
			if (phase == "autoPlay") assertTrue(File(root, "autoPlay").delete())
		}

		assertTrue(errors(pack), errors(pack).contains("autoPlay : file was not found"))
		assertFalse(pack.criticalError)
		assertTrue(pack.detailLoaded)
		assertEquals(2, pack.soundCount)
		assertEquals(2, pack.ledTableCount)
		assertEquals(0, pack.autoPlayTable?.elements?.size)
	}

	// Crash reports 2f684eea (4.1.3, ENOENT case): a keyLED file gone after checkFile is not listed again.
	@Test
	fun storePack_keyLedFileRemovedAfterCheckFile_loadsTheOtherLeds() {
		val root = createStorePack()
		val pack = UniPackFolder(root).load()
		assertTrue(File(root, "keyLED/1 1 2 1").delete())

		pack.loadDetail()

		assertFalse(pack.criticalError)
		assertTrue(pack.detailLoaded)
		assertEquals(2, pack.soundCount)
		assertEquals(1, pack.ledTableCount)
		assertEquals(storePackAutoPlayElements, pack.autoPlayTable?.elements?.size)
	}

	@Test
	fun reloadAutoPlay_afterAutoPlayFileRemoved_recordsErrorAndClearsTable() {
		val root = createStorePack()
		val pack = UniPackFolder(root).load().loadDetail() as UniPackFolder
		assertEquals(storePackAutoPlayElements, pack.autoPlayTable?.elements?.size)
		assertTrue(File(root, "autoPlay").delete())

		pack.reloadAutoPlay()

		assertTrue(errors(pack), errors(pack).contains("autoPlay : file was not found"))
		assertFalse(pack.criticalError)
		assertEquals(0, pack.autoPlayTable?.elements?.size)
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
