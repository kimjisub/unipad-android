package com.kimjisub.launchpad.ledtiming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Whether the LED commands autoplay sends to the Launchpad keep the pack's order and leave none out,
 * on the real clock through the real LED path ([LedPathRun]). Latency is printed, not asserted: it
 * depends on the machine and its load. Store packs are measured by [StorePackLedTimingTest].
 */
class LedTimingTest {

	@get:Rule
	val tmp = TemporaryFolder()

	@Test
	fun timeline_followsLedRunnerOwnershipAndShutdownRules() {
		val pack = loadPack(
			writePack(
				tmp.newFolder("rules"), buttons = 8,
				keyLed = mapOf(
					"1 1 1 1" to "o 1 1 a 5\nd 10\no 2 2 a 7\nd 10\nf 1 1\nf 2 2\n",
					"1 2 1 0" to "o 2 2 a 9\nd 100\n",
				),
				autoPlay = "o 1 1\nd 5\no 2 1\nd 10\no 1 1\nd 100\nf 2 1\n",
			)
		)

		val timeline = intendedTimeline(pack)

		assertEquals(
			listOf(
				"0 pad 0,0=5",
				"5 pad 1,1=9",
				// The first pad's animation takes (1,1) over, so its own off applies later and the endless one's does not.
				"10 pad 1,1=7",
				// Pressing the first pad again ends its animation and turns off everything it lit.
				"15 pad 0,0=0", "15 pad 1,1=0",
				"15 pad 0,0=5",
				"25 pad 1,1=7",
				"35 pad 0,0=0", "35 pad 1,1=0",
				"105 pad 1,1=9",
				// Releasing the second pad ends its endless animation.
				"115 pad 1,1=0",
			),
			timeline.commands.map { "${it.t0} ${it.target}=${it.code}" },
		)
	}

	/** The last pad's keyLED moves to chain 2, so the final release on that chain ends chain 2's endless animation. */
	@Test
	fun timeline_followsAKeyLedChainChange() {
		val pack = loadPack(
			writePack(
				tmp.newFolder("chain"), buttons = 8, chains = 2,
				keyLed = mapOf("2 2 2 0" to "o 2 2 a 9\nd 50\n", "1 1 1 1" to "o 1 1 a 5\nc 2\n"),
				autoPlay = "c 2\no 2 2\nd 10\nc 1\no 1 1\n",
			)
		)

		val timeline = intendedTimeline(pack, releaseAllAtMs = 120)

		assertEquals(
			listOf("0 pad 1,1=9", "10 pad 0,0=5", "50 pad 1,1=9", "100 pad 1,1=9", "120 pad 1,1=0"),
			timeline.commands.map { "${it.t0} ${it.target}=${it.code}" },
		)
	}

	/** An endless animation the final release does not reach (another chain's) plays until the run gives up. */
	@Test
	fun timeline_stopsAnUnreleasedEndlessAnimationAtTheGiveUpTime() {
		val pack = loadPack(
			writePack(
				tmp.newFolder("unreleased"), buttons = 8, chains = 2,
				keyLed = mapOf("2 2 2 0" to "o 2 2 a 9\nd 50\n"),
				autoPlay = "c 2\no 2 2\nd 10\nc 1\n",
			)
		)

		val timeline = intendedTimeline(pack, releaseAllAtMs = 20, giveUpAtMs = 120)

		assertEquals(listOf(0L, 50L, 100L), timeline.commands.map { it.t0 })
	}

	/** LedRunner keeps playing an endless animation without delays one pass a tick; it still owns its LED until released. */
	@Test
	fun timeline_releasesAnEndlessAnimationWithoutDelays() {
		val pack = loadPack(
			writePack(tmp.newFolder("delayless"), buttons = 8, keyLed = mapOf("1 3 3 0" to "o 3 3 a 7\nf 3 3\n"), autoPlay = "o 3 3\n")
		)

		val timeline = intendedTimeline(pack, releaseAllAtMs = 30)

		assertEquals(
			listOf("0 pad 2,2=7", "0 pad 2,2=0", "0 pad 2,2=7", "30 pad 2,2=0"),
			timeline.commands.map { "${it.t0} ${it.target}=${it.code}" },
		)
		assertEquals(1, timeline.delaylessEndlessAnimations)
	}

	/**
	 * 63 pads pressed 5 ms apart for ten rounds, each flashing itself every millisecond for 40 ms
	 * (repeating a colour, as packs do), the top row also lighting a round LED, and one endless
	 * animation released halfway: about 8,000 commands a second at peak. No two animations touch the
	 * same LED within a few ticks of each other, so the order of every LED is fixed.
	 */
	@Test
	fun densePack_reachesTheDriverInThePacksOrderWithNothingMissing() {
		val keyLed = HashMap<String, String>()
		val flash = (1..8).flatMap { x ->
			(1..8).map { y ->
				val pass = "o $x $y a 5\nd 1\no $x $y a 5\nd 1\no $x $y a 21\nd 1\nf $x $y\nd 1\n"
				val round = if (y == 1) "o mc $x a 9\n" to "f mc $x\n" else "" to ""
				"1 $x $y 1" to round.first + pass.repeat(10) + round.second
			}
		}.toMap()
		keyLed += flash - "1 8 8 1"
		keyLed["1 8 8 0"] = "o 8 8 a 13\nd 60000\n"
		val pads = (1..8).flatMap { x -> (1..8).map { y -> x to y } } - (8 to 8)
		val autoPlay = StringBuilder("o 8 8\n")
		repeat(10) { round ->
			for ((x, y) in pads) autoPlay.append("o $x $y\nd 2\nf $x $y\nd 3\n")
			if (round == 4) autoPlay.append("f 8 8\n")
		}
		val root = writePack(tmp.newFolder("dense"), buttons = 8, keyLed = keyLed, autoPlay = autoPlay.toString())

		val releaseAll = loadPack(root).autoPlayLengthMs() + 200
		val giveUp = releaseAll + 10_000
		val timeline = intendedTimeline(loadPack(root), releaseAll, giveUp)
		val recording = LedPathRun(loadPack(root)).run(releaseAllAtMs = releaseAll, giveUpAtMs = giveUp)
		val measurement = LedTimingMeasurement("in-repo dense pack", timeline, recording)
		println(measurement.describe())

		assertTrue("${timeline.peakPerSecond()} a second at peak", timeline.peakPerSecond() > 5_000)
		assertTrue(measurement.describe(), measurement.deliveredAsHandedOver)
		val result = measurement.packToDriver
		assertEquals(result.differences.joinToString("\n"), 0, result.reordered)
		assertEquals(result.differences.joinToString("\n"), 0, result.missing)
		assertEquals(result.differences.joinToString("\n"), 0, result.extra)
		assertEquals(timeline.commands.size, result.matched)
		assertEquals(0, recording.covered)
	}
}
