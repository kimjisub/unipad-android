package com.kimjisub.launchpad.unipack

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UniPackFolderParsingRulesTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private val info = "title=Rules\nproducerName=Tester\nbuttonX=2\nbuttonY=2\nchain=2\n"

	private fun load(
		metadata: String = info,
		sounds: String = "1 1 1 a.wav",
		ledName: String = "1 1 1",
		led: String? = null,
		autoPlay: String? = null,
	): UniPack {
		val root = tmp.newFolder()
		File(root, "info").writeText(metadata)
		File(root, "keySound").writeText(sounds)
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		led?.let {
			File(root, "keyLED").mkdir()
			File(root, "keyLED/$ledName").writeText(it)
		}
		autoPlay?.let { File(root, "autoPlay").writeText(it) }
		return UniPackFolder(root).load().loadDetail()
	}

	@Test
	fun missingWidth_refusesPack() {
		assertTrue(load(metadata = info.replace("buttonX=2\n", "")).criticalError)
	}

	@Test
	fun missingHeight_refusesPack() {
		assertTrue(load(metadata = info.replace("buttonY=2\n", "")).criticalError)
	}

	@Test
	fun anExplicitZeroSize_keepsTheExistingWarningWithoutRefusingPack() {
		val pack = load(metadata = info.replace("buttonX=2", "buttonX=0"), sounds = "")
		assertFalse(pack.criticalError)
		assertEquals("info : buttonX was missing", pack.errorDetail)
	}

	@Test
	fun invalidLastChainValue_refusesPack() {
		val pack = load(metadata = info + "chain=abc\n")
		assertTrue(pack.criticalError)
		assertEquals("info : chain is not a number\ninfo : chain out of range", pack.errorDetail)
	}

	@Test
	fun absentChain_reportsTheMissingKey() {
		val pack = load(metadata = info.replace("chain=2\n", ""))
		assertTrue(pack.criticalError)
		assertEquals("info : chain was missing\ninfo : chain out of range", pack.errorDetail)
	}

	@Test
	fun explicitZeroChain_reportsRangeWithoutClaimingTheKeyIsMissing() {
		val pack = load(metadata = info + "chain=0\n")
		assertTrue(pack.criticalError)
		assertEquals("info : chain out of range", pack.errorDetail)
	}

	@Test
	fun unusableWebsite_hasNoLink() {
		for (value in listOf("javascript:x", "file:///tmp/a", "intent://x", "mailto:x@y", "https://", "http://?x")) {
			assertNull(value, load(metadata = info + "website=$value\n").website)
		}
	}

	@Test
	fun lastUnusableWebsite_removesAnEarlierLink() {
		assertNull(load(metadata = info + "website=https://unipad.io\nwebsite=javascript:x\n").website)
	}

	@Test
	fun addressWithPort_keepsAnAvailableLink() {
		for (value in listOf("unipad.io:8080/path", "localhost:3000")) {
			assertEquals(value, load(metadata = info + "website=$value\n").website)
		}
	}

	@Test
	fun overflowingLastChainValue_refusesPack() {
		assertTrue(load(metadata = info + "chain=99999999999\n").criticalError)
	}

	@Test
	fun validLastChainValue_replacesAnInvalidEarlierValueWithoutWarning() {
		val pack = load(metadata = "chain=abc\n" + info)
		assertFalse(pack.criticalError)
		assertEquals(2, pack.chain)
		assertNull(pack.errorDetail)
	}

	@Test
	fun blankWebsite_hasNoLink() {
		for (value in listOf("", "   ", "\t")) {
			assertNull(load(metadata = info + "website=$value\n").website)
		}
	}

	@Test
	fun lastBlankWebsite_removesAnEarlierLink() {
		assertNull(load(metadata = info + "website=https://unipad.io\nwebsite=\n").website)
	}

	@Test
	fun nonBlankWebsite_isPreserved() {
		assertEquals("unipad.io/path", load(metadata = info + "website= unipad.io/path \n").website)
	}

	@Test
	fun nonAsciiInfoNumbers_refusePack() {
		for (line in listOf("buttonX=\uff12", "buttonY=\uff12", "chain=\uff12")) {
			assertTrue(line, load(metadata = info + line + "\n").criticalError)
		}
	}

	@Test
	fun nonAsciiSoundNumbers_skipOnlyTheirLine() {
		for (line in listOf("\uff11 1 1 a.wav", "1 \u0661 1 a.wav", "1 1 1 a.wav \uff12", "1 1 1 a.wav 1 \uff12")) {
			val pack = load(sounds = "$line\n1 1 1 a.wav")
			assertEquals(line, "keySound : [$line] format is incorrect", pack.errorDetail)
			assertEquals(line, 1, pack.soundCount)
		}
	}

	@Test
	fun nonAsciiLedFileNumber_skipsTheFile() {
		val pack = load(ledName = "\uff11 1 1", led = "f 1 1")
		assertEquals("keyLed : [\uff11 1 1] format is incorrect", pack.errorDetail)
		assertEquals(0, pack.ledTableCount)
	}

	@Test
	fun nonAsciiLedNumbers_skipOnlyTheirLine() {
		for (line in listOf("d \uff11\uff10\uff10", "o \uff11 1 a 5", "o 1 1 auto \uff15")) {
			val pack = load(led = "$line\nf 1 1")
			assertEquals(line, "keyLed : [1 1 1].[$line] format is incorrect", pack.errorDetail)
			assertEquals(line, 1, pack.ledGet(0, 0, 0)!!.ledEvents.size)
		}
	}

	@Test
	fun nonAsciiAutoPlayNumbers_skipOnlyTheirLine() {
		for (line in listOf("o \uff11 1", "d \uff11\uff10\uff10", "c \uff12")) {
			val pack = load(autoPlay = "$line\no 1 1")
			assertEquals(line, "autoPlay : [$line] format is incorrect", pack.errorDetail)
			assertEquals(line, 1, pack.autoPlayTable!!.elements.size)
		}
	}

	@Test
	fun signedOrNonAsciiHexColors_skipOnlyTheirLine() {
		for (color in listOf("+FF00", "-1", "\uff26\uff260000", "\uff11\uff12\uff13")) {
			for (suffix in listOf("", " 5")) {
				val line = "o 1 1 $color$suffix"
				val pack = load(led = "$line\nf 1 1")
				assertEquals(line, "keyLed : [1 1 1].[$line] format is incorrect", pack.errorDetail)
				assertEquals(line, 1, pack.ledGet(0, 0, 0)!!.ledEvents.size)
			}
		}
	}

	@Test
	fun wrongRoundLightColumnCount_warnsBeforeCheckingItsIndex() {
		for (line in listOf("o mc 99", "o * 0 FF0000 5 6")) {
			val pack = load(led = "$line\nf 1 1")
			assertEquals(line, "keyLed : [1 1 1].[$line] format is incorrect", pack.errorDetail)
			assertEquals(line, 1, pack.ledGet(0, 0, 0)!!.ledEvents.size)
		}
	}

	@Test
	fun validRoundLightColumnCount_withAnInvalidIndex_isStillSilentlySkipped() {
		val pack = load(led = "o mc 99 FF0000\nf 1 1")
		assertNull(pack.errorDetail)
		assertEquals(1, pack.ledGet(0, 0, 0)!!.ledEvents.size)
	}
}
