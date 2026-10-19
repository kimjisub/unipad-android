package com.kimjisub.launchpad.unipack

import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A pack's numbers are 32-bit integers on every platform. A larger one is a format error and only its
 * line or file is skipped; one that fits is range-checked as usual. Making a 1-based number 0-based keeps
 * the smallest one as it is: 1 less wrapped to the largest, so a chain move to it went to the last chain
 * instead of the first and a loop count to it played the sound practically forever.
 */
class UniPackFolderIntegerRangeTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private fun load(
		keySound: String = "1 1 1 a.wav\n",
		keyLed: Map<String, String> = emptyMap(),
		autoPlay: String? = null,
	): UniPack {
		val root = tmp.newFolder()
		File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=2\nbuttonY=2\nchain=$CHAIN\n")
		File(root, "keySound").writeText(keySound)
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		if (keyLed.isNotEmpty()) {
			File(root, "keyLed").mkdir()
			keyLed.forEach { (name, text) -> File(root, "keyLed/$name").writeText(text) }
		}
		autoPlay?.let { File(root, "autoPlay").writeText(it) }
		return UniPackFolder(root).load().loadDetail()
	}

	private fun ledEvents(pack: UniPack, c: Int = 0, x: Int = 0, y: Int = 0): List<String> =
		pack.ledGet(c, x, y)!!.ledEvents.map {
			when (it) {
				is LedEvent.On -> "on ${it.x},${it.y}"
				is LedEvent.Off -> "off ${it.x},${it.y}"
				is LedEvent.Delay -> "delay ${it.delay}"
				is LedEvent.Chain -> "chain ${it.chain}"
			}
		}

	private fun autoPlay(pack: UniPack): List<String> =
		pack.autoPlayTable!!.elements.map {
			when (it) {
				is AutoPlay.Element.On -> "on ${it.x},${it.y}"
				is AutoPlay.Element.Off -> "off ${it.x},${it.y}"
				is AutoPlay.Element.Chain -> "chain ${it.c}"
				is AutoPlay.Element.Delay -> "delay ${it.delay}"
			}
		}

	@Test
	fun numbersPast32Bits_areFormatErrors_andTheNextLineIsKept() {
		for (token in TOO_LARGE) {
			for (line in listOf("$token 1 1 a.wav", "1 $token 1 a.wav", "1 1 1 a.wav $token", "1 1 1 a.wav 1 $token")) {
				val pack = load(keySound = "$line\n1 1 1 a.wav\n")
				assertEquals(line, "keySound : [$line] format is incorrect", pack.errorDetail)
				assertEquals(line, 1, pack.soundCount)
			}
			// A file name is at most 255 bytes, so the 400-digit number is left to the lines.
			for (name in listOf("$token 1 1", "1 1 $token", "1 1 1 $token").filter { it.length <= 255 }) {
				val pack = load(keyLed = mapOf(name to "f 1 1", "1 2 2" to "f 1 1"))
				assertEquals(name, "keyLed : [$name] format is incorrect", pack.errorDetail)
				assertEquals(name, listOf("off 0,0"), ledEvents(pack, x = 1, y = 1))
			}
			for (line in listOf("o $token 1 a 5", "f 1 $token", "o mc $token a 5", "d $token", "c $token")) {
				val pack = load(keyLed = mapOf("1 1 1" to "$line\nf 1 1"))
				assertEquals(line, "keyLed : [1 1 1].[$line] format is incorrect", pack.errorDetail)
				assertEquals(line, listOf("off 0,0"), ledEvents(pack))
			}
			for (line in listOf("o $token 1", "t 1 $token", "c $token", "d $token")) {
				val pack = load(autoPlay = "$line\no 1 1\n")
				assertEquals(line, "autoPlay : [$line] format is incorrect", pack.errorDetail)
				assertEquals(line, listOf("on 0,0"), autoPlay(pack))
			}
		}
	}

	@Test
	fun the32BitBounds_areNumbers_andCoordinatesOutsideThePackAreRangeErrors() {
		for (token in listOf("2147483647", "-2147483648")) {
			assertEquals("keySound : [1 $token 1 a.wav] x is incorrect", load(keySound = "1 $token 1 a.wav\n").errorDetail)
			assertEquals("keyLed : [$token 1 1] chain is incorrect", load(keyLed = mapOf("$token 1 1" to "f 1 1")).errorDetail)
			assertEquals("autoPlay : [c $token] chain is incorrect", load(autoPlay = "c $token\n").errorDetail)
			val delay = load(autoPlay = "d $token\n")
			assertNull(delay.errorDetail)
			assertEquals(listOf("delay $token"), autoPlay(delay))
		}
	}

	@Test
	fun chainMoveOfTheSmallestNumber_goesToTheFirstChain() {
		val pack = load(keySound = "1 1 1 a.wav 1 -2147483648\n", autoPlay = "o 1 1\n")

		assertFalse(pack.criticalError)
		assertEquals(0, pack.soundGet(0, 0, 0)?.wormhole)
		assertEquals(listOf("on 0,0", "chain 0"), autoPlay(pack))
	}

	@Test
	fun loopOfTheSmallestNumber_staysTheSmallest_soTheSoundPlaysOnce() {
		val pack = load(keySound = "1 1 1 a.wav -2147483648\n")

		assertNull(pack.errorDetail)
		assertEquals(Int.MIN_VALUE, pack.soundGet(0, 0, 0)?.loop)
	}

	@Test
	fun keyLedChainAndPadOfTheSmallestNumber_stayTheSmallest() {
		val pack = load(keyLed = mapOf("1 1 1" to "o -2147483648 1 a 5\nc -2147483648\nf 1 1"))

		assertNull(pack.errorDetail)
		assertEquals(listOf("on ${Int.MIN_VALUE},0", "chain ${Int.MIN_VALUE}", "off 0,0"), ledEvents(pack))
	}

	private companion object {
		const val CHAIN = 3
		val TOO_LARGE = listOf(
			"2147483648", "-2147483649", "99999999999", "9007199254740993", "9223372036854775808",
			"-9223372036854775809", "9".repeat(400),
		)
	}
}
