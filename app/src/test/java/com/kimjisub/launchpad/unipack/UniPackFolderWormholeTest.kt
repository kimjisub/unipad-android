package com.kimjisub.launchpad.unipack

import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.launchpad.unipack.struct.Sound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The sixth keySound column moves to another chain after the sound plays. A chain the pack does not have
 * used to reach autoPlay as the current chain, and practice mode lit a chain button past the end of the
 * round button table, which closed the app.
 */
class UniPackFolderWormholeTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private fun load(wormhole: String): UniPack {
		val root = tmp.newFolder("pack")
		File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=$CHAIN\n")
		File(root, "keySound").writeText("1 1 1 a.wav 1 $wormhole\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		File(root, "autoPlay").writeText("o 1 1\no 1 2\n")
		return UniPackFolder(root).load().loadDetail()
	}

	private fun autoPlay(pack: UniPack): List<String> =
		pack.autoPlayTable!!.elements.map {
			when (it) {
				is AutoPlay.Element.On -> "on ${it.x},${it.y} chain=${it.currChain}"
				is AutoPlay.Element.Off -> "off ${it.x},${it.y} chain=${it.currChain}"
				is AutoPlay.Element.Chain -> "chain ${it.c}"
				is AutoPlay.Element.Delay -> "delay ${it.delay}"
			}
		}

	private fun assertMoveIgnored(wormhole: String) {
		val pack = load(wormhole)

		assertFalse(pack.criticalError)
		assertEquals(1, pack.soundCount)
		assertEquals(Sound.NO_WORMHOLE, pack.soundGet(0, 0, 0)?.wormhole)
		assertEquals("keySound : [1 1 1 a.wav 1 $wormhole] wormhole is incorrect", pack.errorDetail)
		assertEquals(listOf("on 0,0 chain=0", "on 0,1 chain=0"), autoPlay(pack))
	}

	@Test
	fun zero_meansNoMove() {
		val pack = load("0")

		assertNull(pack.errorDetail)
		assertEquals(Sound.NO_WORMHOLE, pack.soundGet(0, 0, 0)?.wormhole)
		assertEquals(listOf("on 0,0 chain=0", "on 0,1 chain=0"), autoPlay(pack))
	}

	@Test
	fun chainInsideThePack_movesThere() {
		val pack = load("$CHAIN")

		assertNull(pack.errorDetail)
		assertEquals(CHAIN - 1, pack.soundGet(0, 0, 0)?.wormhole)
		assertEquals(listOf("on 0,0 chain=0", "chain ${CHAIN - 1}", "on 0,1 chain=${CHAIN - 1}"), autoPlay(pack))
	}

	@Test
	fun oneChainPastTheLast_isIgnoredWithAWarning() = assertMoveIgnored("${CHAIN + 1}")

	@Test
	fun chainBeyondTheRoundButtons_isIgnoredWithAWarning() = assertMoveIgnored("40")

	@Test
	fun negativeChain_isIgnoredWithAWarning() = assertMoveIgnored("-1")

	companion object {
		private const val CHAIN = 3
	}
}
