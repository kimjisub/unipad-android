package com.kimjisub.launchpad.unipack

import com.kimjisub.launchpad.unipack.struct.AutoPlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * An autoPlay press moves the chain 100 ms of song time after it, as a hand press does
 * (https://github.com/kimjisub/unipad-android/issues/426). Each event is listed with its song time,
 * and each press with the sound it selects ("-" when that chain maps nothing there).
 */
class UniPackFolderAutoPlayWormholeTimingTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private fun events(keySound: String, autoPlay: String, chains: Int = 3): List<String> {
		val root = tmp.newFolder()
		File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=4\nbuttonY=4\nchain=$chains\n")
		File(root, "keySound").writeText(keySound)
		File(root, "sounds").mkdir()
		for (name in listOf("a.wav", "b.wav", "voice.wav", "voice2.wav", "drum.wav")) File(root, "sounds/$name").writeText("")
		File(root, "autoPlay").writeText(autoPlay)
		val pack = UniPackFolder(root).load().loadDetail()
		// As the runner plays it: an event comes at the sum of the waits above it, never before the one above it.
		var sum = 0L
		var time = 0L
		return pack.autoPlayTable!!.elements.mapNotNull {
			when (it) {
				is AutoPlay.Element.Delay -> { sum += it.delay; time = maxOf(time, sum); null }
				is AutoPlay.Element.On -> "$time on ${it.currChain + 1} ${it.x + 1} ${it.y + 1} " +
					(pack.soundGet(it.currChain, it.x, it.y, it.num)?.file?.name ?: "-")
				is AutoPlay.Element.Off -> "$time off ${it.currChain + 1} ${it.x + 1} ${it.y + 1}"
				is AutoPlay.Element.Chain -> "$time chain ${it.c + 1}" + if (it.scheduled) "" else " (file)"
			}
		} + "$time end"
	}

	private val voiceAndDrum = "1 1 1 a.wav 1 2\n1 1 2 voice.wav\n2 1 2 drum.wav\n"

	@Test
	fun aPressBeforeTheMove_keepsTheOriginalChainsSound() {
		assertEquals(
			listOf("0 on 1 1 1 a.wav", "1 on 1 1 2 voice.wav", "1 off 1 1 2", "100 chain 2", "200 end"),
			events(voiceAndDrum, "on 1 1\nd 1\ntouch 1 2\nd 199\n"),
		)
	}

	@Test
	fun anUnmappedPressBeforeTheMove_isSilentAndDoesNotBringTheMoveForward() {
		assertEquals(
			listOf("0 on 1 1 1 a.wav", "1 on 1 1 2 -", "1 off 1 1 2", "100 chain 2", "200 end"),
			events("1 1 1 a.wav 1 2\n2 1 2 drum.wav\n", "on 1 1\nd 1\ntouch 1 2\nd 199\n"),
		)
	}

	@Test
	fun aPressAt99ms_isOnTheOldChain_andAt100ms_onTheNewOne() {
		assertEquals(
			listOf("0 on 1 1 1 a.wav", "99 on 1 1 2 voice.wav", "100 chain 2", "100 end"),
			events(voiceAndDrum, "on 1 1\nd 99\non 1 2\nd 1\n"),
		)
		assertEquals(
			listOf("0 on 1 1 1 a.wav", "100 chain 2", "100 on 2 1 2 drum.wav", "100 end"),
			events(voiceAndDrum, "on 1 1\nd 100\non 1 2\n"),
		)
	}

	/** Unity's last voice: the moving sound's pad, then 1 ms later the voice second in its pad's queue. */
	@Test
	fun theVoiceSecondInItsQueue_staysOnItsChainOneMillisecondAfterTheMovingSound() {
		val keySound = "3 2 3 a.wav 1 4\n3 2 4 b.wav\n3 2 4 voice.wav\n4 2 4 drum.wav\n"
		assertEquals(
			listOf(
				"0 chain 3 (file)", "0 on 3 2 4 b.wav", "10 on 3 2 3 a.wav", "11 on 3 2 4 voice.wav",
				"110 chain 4", "300 on 4 2 4 drum.wav", "300 end",
			),
			events(keySound, "c 3\non 2 4\nd 10\non 2 3\nd 1\non 2 4\nd 289\non 2 4\n", chains = 4),
		)
	}

	@Test
	fun aWaitIsSplitAtTheMove_withoutLengtheningTheSong() {
		val root = tmp.newFolder()
		File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=4\nbuttonY=4\nchain=2\n")
		File(root, "keySound").writeText("1 1 1 a.wav 1 2\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeText("")
		File(root, "autoPlay").writeText("on 1 1\nd 250\non 1 2\n")
		val elements = UniPackFolder(root).load().loadDetail().autoPlayTable!!.elements

		assertEquals(listOf(100, 150), elements.filterIsInstance<AutoPlay.Element.Delay>().map { it.delay })
	}

	@Test
	fun movesDueAtTheSameTime_applyInPressOrder_andEachResetsTheQueues() {
		val keySound = "1 1 1 a.wav 1 2\n1 1 2 b.wav 1 3\n2 1 3 voice.wav\n3 1 3 voice.wav\n3 1 3 voice2.wav\n"
		assertEquals(
			listOf(
				"0 on 1 1 1 a.wav", "0 on 1 1 2 b.wav", "100 chain 2", "100 chain 3",
				"100 on 3 1 3 voice.wav", "100 on 3 1 3 voice2.wav", "100 end",
			),
			events(keySound, "on 1 1\non 1 2\nd 100\non 1 3\non 1 3\n"),
		)
	}

	@Test
	fun theQueueContinuesUntilTheMoveIsApplied() {
		val keySound = "1 1 1 a.wav 1 2\n1 1 1 b.wav\n2 1 1 voice.wav\n"
		assertEquals(
			listOf("0 on 1 1 1 a.wav", "50 on 1 1 1 b.wav", "100 chain 2", "150 on 2 1 1 voice.wav", "150 end"),
			events(keySound, "on 1 1\nd 50\non 1 1\nd 100\non 1 1\n", chains = 2),
		)
	}

	@Test
	fun aFileChainCancelsThePendingMove() {
		assertEquals(
			listOf("0 on 1 1 1 a.wav", "50 chain 3 (file)", "250 end"),
			events("1 1 1 a.wav 1 2\n", "on 1 1\nd 50\nc 3\nd 200\n"),
		)
	}

	@Test
	fun aMoveDueAtAFileChain_appliesBeforeIt() {
		assertEquals(
			listOf("0 on 1 1 1 a.wav", "100 chain 2", "100 chain 3 (file)", "100 end"),
			events("1 1 1 a.wav 1 2\n", "on 1 1\nd 100\nc 3\n"),
		)
	}

	@Test
	fun movesStillPendingAtTheEnd_applyAtTheFinalSongTimeWithoutAWait() {
		assertEquals(
			listOf("0 on 1 1 1 a.wav", "0 on 1 1 1 a.wav", "0 chain 2", "0 chain 2", "0 end"),
			events("1 1 1 a.wav 1 2\n", "on 1 1\non 1 1\n"),
		)
		assertEquals(
			listOf("0 on 1 1 1 a.wav", "30 chain 2", "30 end"),
			events("1 1 1 a.wav 1 2\n", "on 1 1\nd 30\n"),
		)
	}

	@Test
	fun invalidLinesAndEmptyPads_doNotMoveTheSwitchTime() {
		assertEquals(
			listOf("0 on 1 1 1 a.wav", "40 on 1 4 4 -", "100 chain 2", "120 end"),
			events("1 1 1 a.wav 1 2\n", "on 1 1\nd 40\nbogus\non 9 9\nd x\non 4 4\nd 80\n"),
		)
	}

	/** A negative wait only shortens later waits; it never takes back song time already reached. */
	@Test
	fun aNegativeWaitDoesNotPullTheSwitchBack() {
		assertEquals(
			listOf("50 on 1 1 1 a.wav", "50 on 1 1 2 voice.wav", "150 chain 2", "150 on 2 1 2 drum.wav", "150 end"),
			events(voiceAndDrum, "d 50\nd -50\nd 50\non 1 1\nd -30\non 1 2\nd 130\non 1 2\n"),
		)
	}
}
