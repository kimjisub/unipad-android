package com.kimjisub.launchpad.unipack

import com.kimjisub.launchpad.manager.LaunchpadColor
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * keyLED `l` lines address the Launchpad logo. iOS (#10) reads them as the circle index 32, just past
 * the 32 round buttons, and the drivers send that index to the logo; these lines used to be skipped here.
 */
class UniPackFolderLogoLedTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private fun load(keyLed: String): UniPack {
		val root = tmp.newFolder("pack")
		File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\n")
		File(root, "keySound").writeText("1 1 1 a.wav\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		File(root, "keyLed").mkdir()
		File(root, "keyLed/1 1 1").writeText(keyLed)
		return UniPackFolder(root).load().loadDetail()
	}

	private fun events(pack: UniPack): List<String> =
		pack.ledGet(0, 0, 0)!!.ledEvents.map {
			when (it) {
				is LedEvent.On -> "on ${it.x},${it.y} color=${Integer.toHexString(it.color)} vel=${it.velocity}"
				is LedEvent.Off -> "off ${it.x},${it.y}"
				is LedEvent.Delay -> "delay ${it.delay}"
				is LedEvent.Chain -> "chain ${it.chain}"
			}
		}

	@Test
	fun logoLines_becomeCircleIndex32_inEveryIosForm() {
		val pack = load(
			"""
			o l FF0000
			o l a 5
			on l auto 9
			o l 0 00FF00
			o l 0 a 13
			o l 0 0000FF 21
			f l
			off l
			""".trimIndent()
		)

		assertNull(pack.errorDetail)
		assertEquals(
			listOf(
				"on -1,32 color=ffff0000 vel=4",
				"on -1,32 color=${Integer.toHexString(LaunchpadColor.ARGB[5].toInt())} vel=5",
				"on -1,32 color=${Integer.toHexString(LaunchpadColor.ARGB[9].toInt())} vel=9",
				"on -1,32 color=ff00ff00 vel=4",
				"on -1,32 color=${Integer.toHexString(LaunchpadColor.ARGB[13].toInt())} vel=13",
				"on -1,32 color=ff0000ff vel=21",
				"off -1,32",
				"off -1,32",
			),
			events(pack)
		)
	}

	@Test
	fun roundLedNumbersOutsideTheRing_neverReachTheLogoIndex() {
		// `mc 33` used to become circle index 32 too; now that 32 lights the logo it must not.
		val pack = load(
			"""
			o mc 32 a 3
			o mc 33 a 3
			o * 0 a 3
			f mc 33
			f mc 32
			""".trimIndent()
		)

		assertNull(pack.errorDetail)
		assertEquals(listOf("on -1,31 color=${Integer.toHexString(LaunchpadColor.ARGB[3].toInt())} vel=3", "off -1,31"), events(pack))
	}

	@Test
	fun malformedLogoLines_areReportedAndSkipped_likeOtherLines() {
		val pack = load(
			"""
			o l
			o l ZZZZZZ
			o l a 200
			o l 0 a 5 9
			o l a 5
			""".trimIndent()
		)

		assertEquals(listOf("on -1,32 color=${Integer.toHexString(LaunchpadColor.ARGB[5].toInt())} vel=5"), events(pack))
		assertEquals(4, pack.errorDetail!!.lines().count { it.contains("format is incorrect") })
	}

	@Test
	fun padAndRoundLines_parseAsBefore() {
		val pack = load(
			"""
			o 1 1 FF0000
			o 8 8 a 5
			o 2 3 00FF00 7
			o * 1 a 72
			o mc 9 0000FF
			d 100
			f 1 1
			f * 1
			c 2
			""".trimIndent()
		)

		assertNull(pack.errorDetail)
		assertEquals(
			listOf(
				"on 0,0 color=ffff0000 vel=4",
				"on 7,7 color=${Integer.toHexString(LaunchpadColor.ARGB[5].toInt())} vel=5",
				"on 1,2 color=ff00ff00 vel=7",
				"on -1,0 color=${Integer.toHexString(LaunchpadColor.ARGB[72].toInt())} vel=72",
				"on -1,8 color=ff0000ff vel=4",
				"delay 100",
				"off 0,0",
				"off -1,0",
				"chain 1",
			),
			events(pack)
		)
	}
}
