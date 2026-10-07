package com.kimjisub.launchpad.unipack

import com.kimjisub.launchpad.manager.LaunchpadColor
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UniPackFolderColorLengthTest {
	@get:Rule
	val tmp = TemporaryFolder()

	private fun load(lines: String): UniPack {
		val root = tmp.newFolder()
		File(root, "info").writeText("title=Conformance\nproducerName=UniPad Conformance\nbuttonX=4\nbuttonY=3\nchain=2\nsquareButton=true")
		File(root, "keySound").writeText("1 4 3 c.wav")
		File(root, "sounds").mkdir()
		File(root, "sounds/c.wav").writeBytes(byteArrayOf())
		File(root, "keyLed").mkdir()
		File(root, "keyLed/1 1 1").writeText(lines)
		return UniPackFolder(root).load().loadDetail()
	}

	private fun eventValues(events: List<LedEvent>): List<List<Any>> = events.map {
		when (it) {
			is LedEvent.On -> listOf("on", it.x, it.y, it.color, it.velocity)
			is LedEvent.Off -> listOf("off", it.x, it.y)
			is LedEvent.Delay -> listOf("delay", it.delay)
			is LedEvent.Chain -> listOf("chain", it.chain)
		}
	}

	private fun assertEvents(message: String, expected: List<LedEvent>, pack: UniPack) {
		assertEquals(message, eventValues(expected), eventValues(pack.ledGet(0, 0, 0)!!.ledEvents))
	}

	@Test
	fun klM13_sevenDigitColorIsReportedAndSkipped() {
		// KL-M13 input fingerprint: 7c21fd3e45e558cad69336af853868c266c389c42cbf81d2417a297eb9a36744
		val pack = load("o 1 1 1234567\nf 1 1")

		assertFalse(pack.criticalError)
		assertTrue(pack.detailLoaded)
		assertEquals(1, pack.soundCount)
		assertEquals(1, pack.ledTableCount)
		assertEvents("KL-M13", listOf(LedEvent.Off(0, 0)), pack)
		assertEquals("keyLed : [1 1 1].[o 1 1 1234567] format is incorrect", pack.errorDetail?.trim())
	}

	@Test
	fun overlongColorsAreRejectedAcrossAllTargetsWithAndWithoutVelocity() {
		val targets = listOf("1 1", "* 1", "mc 9", "l", "l 0")
		// Leading zeroes still make a token too long, even if its numeric value fits RGB.
		val colors = listOf("1234567", "0000000", "00ffffff", "000000001", "12345678901234567890")
		for (target in targets) for (color in colors) for (suffix in if (target == "l") listOf("") else listOf("", " 7")) {
			val line = "o $target $color$suffix"
			val pack = load("$line\nd 10\nf 1 1\no 1 1 00ff00")
			assertEvents(line, listOf(LedEvent.Delay(10), LedEvent.Off(0, 0), LedEvent.On(0, 0, 0xff00ff00.toInt(), 4)), pack)
			assertEquals(line, "keyLed : [1 1 1].[$line] format is incorrect", pack.errorDetail?.trim())
			assertFalse(line, pack.criticalError)
		}
	}

	@Test
	fun oneToSixDigitColorsRemainOpaqueAcrossAllTargets() {
		val targets = listOf("1 1" to (0 to 0), "* 1" to (-1 to 0), "mc 9" to (-1 to 8), "l" to (-1 to 32), "l 0" to (-1 to 32))
		val colors = listOf("0", "f", "0F", "abc", "AbCd", "abcde", "000000", "FFFFFF", "12aBcD")
		for ((target, position) in targets) for (color in colors) for (suffix in if (target == "l") listOf("") else listOf("", " 7")) {
			val line = "on $target $color$suffix"
			val pack = load(line)
			assertNull(line, pack.errorDetail)
			assertEvents(line, listOf(LedEvent.On(position.first, position.second, color.toInt(16) or 0xff000000.toInt(), if (suffix.isEmpty()) 4 else 7)), pack)
		}
	}

	@Test
	fun automaticColorsAndExplicitVelocityKeepTheirPaletteValues() {
		for (target in listOf("1 1" to (0 to 0), "* 1" to (-1 to 0), "l" to (-1 to 32), "l 0" to (-1 to 32))) {
			for (alias in listOf("a", "auto")) for (velocity in listOf(0, 5, 127)) {
				val line = "o ${target.first} $alias $velocity"
				val pack = load(line)
				assertNull(line, pack.errorDetail)
				assertEvents(line, listOf(LedEvent.On(target.second.first, target.second.second, LaunchpadColor.ARGB[velocity].toInt(), velocity)), pack)
			}
		}
	}
}
