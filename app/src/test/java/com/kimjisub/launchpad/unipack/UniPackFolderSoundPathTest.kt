package com.kimjisub.launchpad.unipack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UniPackFolderSoundPathTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private fun load(stored: String, reference: String): UniPack {
		val root = tmp.newFolder()
		File(root, "info").writeText("title=Paths\nproducerName=Tester\nbuttonX=1\nbuttonY=1\nchain=1\n")
		File(root, "keySound").writeText("1 1 1 $reference\n")
		File(root, "sounds/$stored").apply {
			parentFile!!.mkdirs()
			writeText("selected sound")
		}
		return UniPackFolder(root).load().loadDetail()
	}

	private fun assertSoundFound(stored: String, reference: String) {
		val pack = load(stored, reference)
		assertFalse(pack.criticalError)
		assertNull(pack.errorDetail)
		assertEquals(1, pack.soundCount)
		assertEquals("selected sound", pack.soundGet(0, 0, 0)?.file?.readText())
	}

	@Test
	fun composedReference_findsDecomposedSoundName() {
		assertSoundFound("\u1112\u1161\u11ab.wav", "\ud55c.wav")
	}

	@Test
	fun decomposedReference_findsComposedSoundName() {
		assertSoundFound("\ud55c.wav", "\u1112\u1161\u11ab.wav")
	}

	@Test
	fun canonicalNameMatch_alsoIgnoresCase() {
		assertSoundFound("caf\u0065\u0301.WAV", "caf\u00e9.wav")
	}

	@Test
	fun windowsSeparators_findNestedSound() {
		assertSoundFound("fx/sweep.wav", "fx\\sweep.wav")
	}

	@Test
	fun leadingDotSlash_findsSound() {
		assertSoundFound("a.wav", "./a.wav")
	}

	@Test
	fun leadingSlash_isRelativeToSounds() {
		assertSoundFound("a.wav", "/a.wav")
	}

	@Test
	fun repeatedSlashes_findNestedSound() {
		assertSoundFound("fx/sweep.wav", "fx//sweep.wav")
	}

	@Test
	fun mixedSeparatorsAndDotComponents_findNestedSound() {
		assertSoundFound("fx/sweep.wav", ".\\/fx/./\\sweep.wav")
	}

	@Test
	fun ordinaryNestedPath_stillLoads() {
		assertSoundFound("fx/sweep.wav", "fx/sweep.wav")
	}

	@Test
	fun parentTraversal_doesNotFindAnOutsideSound() {
		val pack = load("a.wav", "../info")
		assertFalse(pack.criticalError)
		assertEquals(0, pack.soundCount)
		assertTrue(pack.errorDetail.orEmpty().contains("sound was not found"))
	}

	@Test
	fun exactName_winsOverCaseInsensitiveCandidate() {
		val dir = tmp.newFolder()
		File(dir, "A.wav").writeText("fallback")
		File(dir, "a.WAV").writeText("exact")
		assumeTrue("requires case-sensitive storage", dir.listFiles()!!.size == 2)
		val index = SoundFileIndex(dir)
		assertEquals("exact", index.find("a.WAV")?.readText())
	}
}
