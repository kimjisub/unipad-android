package com.kimjisub.launchpad.unipack

import com.kimjisub.launchpad.unipack.struct.AutoPlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** `touch` is `on` + `off` in one line, so a pad whose sound moves the chain moves it on `touch` too. */
class UniPackFolderAutoPlayTouchTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private fun autoPlayOf(keySound: String, autoPlay: String): List<String> {
		val root = tmp.newFolder("pack")
		File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=4\nbuttonY=4\nchain=3\n")
		File(root, "keySound").writeText(keySound)
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		File(root, "sounds/b.wav").writeText("")
		File(root, "autoPlay").writeText(autoPlay)
		val pack = UniPackFolder(root).load().loadDetail()
		return pack.autoPlayTable!!.elements.map {
			when (it) {
				is AutoPlay.Element.On -> "on ${it.currChain} ${it.x} ${it.y} #${it.num}"
				is AutoPlay.Element.Off -> "off ${it.currChain} ${it.x} ${it.y}"
				is AutoPlay.Element.Chain -> "chain ${it.c}"
				is AutoPlay.Element.Delay -> "delay ${it.delay}"
			}
		}
	}

	@Test
	fun touchOnAWormholePad_movesTheChainLikeOn() {
		val elements = autoPlayOf(
			keySound = "1 1 1 a.wav 1 3\n3 2 2 b.wav\n",
			autoPlay = "t 1 1\nd 100\nt 2 2\n",
		)

		assertEquals(
			listOf(
				"on 0 0 0 #0",
				"off 0 0 0",
				"chain 2",
				"delay 100",
				"on 2 1 1 #0",
				"off 2 1 1",
			),
			elements,
		)
	}

	@Test
	fun touchesAcrossWormholesAndChains_followEveryMove() {
		val elements = autoPlayOf(
			keySound = "1 1 1 a.wav\n1 2 2 a.wav 1 2\n1 2 2 b.wav\n2 1 1 b.wav\n",
			autoPlay = "t 1 1\nt 2 2\nt 1 1\nc 1\nt 2 2\n",
		)

		assertEquals(
			listOf(
				"on 0 0 0 #0",
				"off 0 0 0",
				"on 0 1 1 #0",
				"off 0 1 1",
				"chain 1",
				"on 1 0 0 #0",
				"off 1 0 0",
				"chain 0",
				"on 0 1 1 #0",
				"off 0 1 1",
				"chain 1",
			),
			elements,
		)
	}

	@Test
	fun touchAndOnOfTheSameLines_giveTheSameChainsAndQueueNumbers() {
		val keySound = "1 1 1 a.wav 1 2\n1 1 1 b.wav\n2 1 1 b.wav 1 1\n"
		val touches = autoPlayOf(keySound, "t 1 1\nt 1 1\nt 1 1\n")
		tmp.root.resolve("pack").deleteRecursively()
		val ons = autoPlayOf(keySound, "o 1 1\no 1 1\no 1 1\n")

		assertEquals(ons, touches.filterNot { it.startsWith("off") })
	}
}
