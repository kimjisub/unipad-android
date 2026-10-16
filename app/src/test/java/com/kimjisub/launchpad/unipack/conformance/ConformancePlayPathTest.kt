package com.kimjisub.launchpad.unipack.conformance

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Android-only scenarios, not part of the shared corpus, for the two paths the corpus cases rely on
 * without exercising: what a chain change resets, and what leaving and returning to the play screen
 * keeps. Each runs through [actualFor] like a corpus case and states the play screen's result.
 */
class ConformancePlayPathTest {

	private val corpus = loadCorpus()

	private fun case(files: Map<String, String>, scenario: String): CorpusCase {
		val base = corpus.cases.first { it.id == "RUN-S-003" }
		val tones = base.files.filter { it.path.startsWith("sounds/") || it.path == "info" }
		return base.copy(
			id = "ANDROID-PLAY-PATH",
			files = tones + files.map { (path, text) -> CorpusFile(path, text, null, null) },
			scenario = Json.parseToJsonElement(scenario).jsonArray.map { it.jsonObject },
		)
	}

	private fun checkpoints(c: CorpusCase): JsonElement = (actualFor(c, corpus) as JsonObject).getValue("checkpoints")

	private fun json(text: String) = Json.parseToJsonElement(text)

	@Test
	fun returningToAChainStartsItsPadsFromTheFirstSoundAndLightAgain() {
		val c = case(
			mapOf(
				"keySound" to "1 1 1 a.wav\n1 1 1 b.wav",
				"keyLed/1 1 1 1 a" to "o 1 1 FF0000\nd 5\nf 1 1",
				"keyLed/1 1 1 1 b" to "o 1 1 00FF00\nd 5\nf 1 1",
			),
			"""[
				{"do": "press", "x": 0, "y": 0}, {"do": "release", "x": 0, "y": 0}, {"do": "advance", "ms": 20}, {"do": "observe"},
				{"do": "chain", "c": 1}, {"do": "chain", "c": 0},
				{"do": "press", "x": 0, "y": 0}, {"do": "release", "x": 0, "y": 0}, {"do": "advance", "ms": 20}, {"do": "observe"}
			]""",
		)
		val first = """[["sound", 0, 0, 0, "a.wav", 0], ["ledOn", 0, 0, "ffff0000", 4], ["ledOff", 0, 0]]"""
		assertEquals(json("[$first, $first]"), checkpoints(c))
	}

	@Test
	fun padsStillSoundAfterLeavingAndReturningToTheScreen() {
		val c = case(
			mapOf("keySound" to "1 1 1 a.wav"),
			"""[
				{"do": "press", "x": 0, "y": 0}, {"do": "release", "x": 0, "y": 0}, {"do": "observe"},
				{"do": "stop"}, {"do": "start"},
				{"do": "press", "x": 0, "y": 0}, {"do": "release", "x": 0, "y": 0}, {"do": "observe"}
			]""",
		)
		assertEquals(json("""[[["sound", 0, 0, 0, "a.wav", 0]], [["sound", 0, 0, 0, "a.wav", 0]]]"""), checkpoints(c))
	}

	@Test
	fun autoPlayHasPlayedItsFirstStepBeforeTheNextScenarioStep() {
		val c = case(
			mapOf("keySound" to "1 1 1 a.wav", "autoPlay" to "on 1 1\ndelay 100\noff 1 1"),
			"""[{"do": "autoplay"}, {"do": "observe"}]""",
		)
		// The first pass and the wait for it race; one run passing by luck would prove little.
		repeat(20) { assertEquals(json("""[[["autoOn", 0, 0]]]"""), checkpoints(c)) }
	}

	@Test
	fun leavingPausesAutoPlayWhereItIsAndPlayContinuesFromThere() {
		val c = case(
			mapOf("keySound" to "1 1 1 a.wav", "autoPlay" to "on 1 1\ndelay 100\noff 1 1\ndelay 100\ntouch 2 1"),
			"""[
				{"do": "autoplay"}, {"do": "advance", "ms": 50}, {"do": "observe"},
				{"do": "stop"}, {"do": "advance", "ms": 400}, {"do": "start"}, {"do": "advance", "ms": 400}, {"do": "observe"},
				{"do": "resume"}, {"do": "advance", "ms": 400}, {"do": "observe"}
			]""",
		)
		assertEquals(
			json("""[[["autoOn", 0, 0]], [], [["autoOff", 0, 0], ["autoOn", 1, 0], ["autoOff", 1, 0]]]"""),
			checkpoints(c),
		)
	}

	@Test
	fun continuingAutoPlayHasPlayedItsFirstStepBeforeTheNextScenarioStep() {
		val c = case(
			mapOf("keySound" to "1 1 1 a.wav", "autoPlay" to "on 1 1\ndelay 100\noff 1 1\ndelay 100\ntouch 2 1"),
			"""[
				{"do": "autoplay"}, {"do": "advance", "ms": 50}, {"do": "stop"}, {"do": "advance", "ms": 400},
				{"do": "start"}, {"do": "observe"}, {"do": "resume"}, {"do": "observe"}
			]""",
		)
		// Continuing wakes the paused runner before it is allowed to play, so it can read the clock while
		// still paused; a wait that counts those reads ends before the first pass. One run proves little.
		repeat(200) { assertEquals(json("""[[["autoOn", 0, 0]], [["autoOff", 0, 0]]]"""), checkpoints(c)) }
	}
}
