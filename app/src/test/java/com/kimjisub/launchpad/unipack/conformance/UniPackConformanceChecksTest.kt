package com.kimjisub.launchpad.unipack.conformance

import com.kimjisub.launchpad.unipack.UniPackFolder
import io.mockk.every
import io.mockk.just
import io.mockk.Runs
import io.mockk.spyk
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/**
 * A comparison that cannot fail proves nothing. Each test breaks one thing the corpus cases rely on
 * and requires the comparison to notice.
 */
class UniPackConformanceChecksTest {

	private val corpus = loadCorpus()

	private fun byId(id: String) = corpus.cases.first { it.id == id }

	@Test
	fun screenInputCasesCannotPassThroughTheGenericRunner() {
		val neverRun = Deps(loadPack = { error("screen input case reached the generic runner") })
		for (id in listOf("RUN-G606", "RUN-G607", "RUN-G608", "RUN-G609", "RUN-G610", "RUN-G616", "RUN-G617", "RUN-G618")) {
			val result = resultFor(byId(id), corpus, neverRun)
			assertEquals(id, "unverified", result.outcome.status)
			assertEquals(id, JsonNull, result.actual)
		}
	}

	@Test
	fun unknownLayersAreRejectedInsteadOfBeingRunAsPlayback() {
		val unknown = byId("KS-001").copy(layer = "unregistered")
		assertThrows(IllegalArgumentException::class.java) { actualFor(unknown, corpus) }
	}

	@Test
	fun everyG6InputRejectsAChangedFile() {
		val added = corpus.cases.filter { "-G6" in it.id }
		assertEquals("The complete G6 addition must be registered", 49, added.size)
		for (c in added) {
			verifyFingerprint(c, corpus)
			val at = c.files.indexOfFirst { it.text != null }
			check(at >= 0) { "${c.id}: no text file to mutate" }
			val changed = c.files.mapIndexed { index, file ->
				if (index == at) CorpusFile(file.path, file.text + "\n", null, null) else file
			}
			assertThrows("${c.id}: changed input must be rejected", IllegalStateException::class.java) {
				verifyFingerprint(c.copy(files = changed), corpus)
			}
		}
	}

	@Test
	fun noG6CaseAcceptsAnInjectedObservation() {
		val added = corpus.cases.filter { "-G6" in it.id }
		assertEquals(49, added.size)
		for (c in added) {
			val wrong = buildJsonObject { put("injectedObservation", true) }
			assertEquals(c.id, "fail", classify(c, wrong).status)
			assertTrue(c.id, classify(c, wrong).unexpected)
		}
	}

	@Test
	fun inputsWhoseFileNamesChangeAreNotSentToTheParser() {
		val c = byId("KS-G601")
		val root = Files.createTempDirectory("corpus-name-probe").toFile()
		val preserved = try {
			c.writeTo(root, corpus)
			c.files.map { it.path }.sorted() == root.walkTopDown().filter { it.isFile }
				.map { it.relativeTo(root).invariantSeparatorsPath }.sorted().toList()
		} finally { root.deleteRecursively() }
		var parsed = false
		val result = resultFor(c, corpus, Deps(loadPack = {
			parsed = true
			UniPackFolder(it).load().loadDetail()
		}))
		assertEquals("Only faithfully stored inputs may reach the parser", preserved, parsed)
		if (!preserved) {
			assertEquals("unverified", result.outcome.status)
			assertEquals(JsonNull, result.actual)
		}
	}

	private fun withKeySound(c: CorpusCase, text: String) =
		c.copy(files = c.files.map { if (it.path == "keySound") CorpusFile(it.path, text, null, null) else it })

	@Test
	fun aChangedInputFileNoLongerMatchesTheCaseFingerprint() {
		val c = byId("KS-001")
		val altered = withKeySound(c, c.files.first { it.path == "keySound" }.text + "\n1 2 2 c.wav")
		assertNotEquals(c.fingerprint, fingerprintOf(altered.files, corpus))
		assertThrows(IllegalStateException::class.java) { verifyFingerprint(altered, corpus) }
	}

	@Test
	fun aChangedExpectedCoordinateTurnsAPassIntoAFail() {
		val c = byId("KS-001")
		val actual = actualFor(c, corpus)
		assertEquals("pass", classify(c, actual).status)
		val expectedObject = c.expected!!.jsonObject
		val sounds = expectedObject.getValue("sounds").jsonArray
		val first = sounds[0].jsonObject
		val shifted = JsonObject(first + ("x" to JsonPrimitive(first.getValue("x").jsonPrimitive.int + 1)))
		val expected = JsonObject(expectedObject + ("sounds" to JsonArray(listOf<JsonElement>(shifted) + sounds.drop(1))))
		assertEquals("fail", classify(c.copy(expected = expected), actual).status)
	}

	@Test
	fun aChangedInputMakesTheSameExpectedResultFail() {
		val c = byId("KS-001")
		val altered = withKeySound(c, "1 2 1 a.wav\n2 4 3 b.wav")
		assertEquals("fail", classify(c, actualFor(altered, corpus)).status)
	}

	@Test
	fun leavingTheDetailLoadOutFailsAParseCase() {
		val c = byId("KS-001")
		assertEquals("pass", classify(c, actualFor(c, corpus)).status)
		val noDetail = Deps(loadPack = { UniPackFolder(it).load() })
		assertEquals("fail", classify(c, actualFor(c, corpus, noDetail)).status)
	}

	@Test
	fun aLedRunnerWhoseEventOnDoesNothingFailsARunCase() {
		val c = byId("RUN-L-001")
		assertEquals("pass", classify(c, actualFor(c, corpus)).status)
		val silent = Deps(wrapLedRunner = { real -> spyk(real).also { every { it.eventOn(any(), any(), any()) } just Runs } })
		assertEquals("fail", classify(c, actualFor(c, corpus, silent)).status)
	}

	@Test
	fun aCopyThatDoesNotHashToItsCorpusSha256OrHasNoneIsRefused() {
		val bytes = requireNotNull(javaClass.getResourceAsStream("/unipack-conformance/corpus.json")).use { it.readBytes() }
		val digest = requireNotNull(javaClass.getResourceAsStream("/unipack-conformance/corpus.sha256")).use { it.readBytes() }.toString(Charsets.UTF_8)
		assertEquals(corpus.sha256, verifyCorpusDigest(bytes, digest))
		val changed = bytes.copyOf()
		val at = String(bytes, Charsets.UTF_8).indexOf("KS-001")
		changed[at] = (changed[at].toInt() xor 1).toByte()
		assertThrows(IllegalStateException::class.java) { verifyCorpusDigest(changed, digest) }
		assertThrows(IllegalStateException::class.java) { verifyCorpusDigest(bytes, null) }
		assertThrows(IllegalStateException::class.java) { verifyCorpusDigest(bytes, digest.trim()) }
	}

	@Test
	fun autoPlayPressesThatDoNotReachThePadsFailAToPadsCase() {
		val c = byId("RUN-RP-003")
		assertEquals("pass", classify(c, actualFor(c, corpus)).status)
		val notForwarded = c.copy(scenario = c.scenario.map {
			if (it.getValue("do").jsonPrimitive.content == "autoplay") buildJsonObject { put("do", "autoplay") } else it
		})
		assertEquals("fail", classify(c, actualFor(notForwarded, corpus)).status)
	}

	@Test
	fun autoPlayLeftOnFailsTheEndlessSoundReleaseCase() {
		val c = byId("RUN-RP-005")
		assertEquals("pass", classify(c, actualFor(c, corpus)).status)
		val leftOn = c.copy(scenario = c.scenario.filter { it.getValue("do").jsonPrimitive.content != "autoplayOff" })
		assertEquals("fail", classify(c, actualFor(leftOn, corpus)).status)
	}

	@Test
	fun aPinnedDifferenceThatStopsHappeningOrChangesIsUnexpected() {
		val c = byId("KS-001")
		val pinned = c.copy(known = mapOf(PLATFORM to Known("fail", buildJsonObject { put("loaded", false) }, "pinned")))
		assertTrue(classify(pinned, c.expected!!).unexpected)
		assertTrue(classify(pinned, buildJsonObject { put("loaded", true) }).unexpected)
		assertEquals(Outcome("fail", false, "pinned"), classify(pinned, buildJsonObject { put("loaded", false) }))
	}

	@Test
	fun anUndeterminedCaseIsNeverCountedAsAPass() {
		// The corpus may have no undetermined case, so one is made from a determined one.
		val determined = byId("KS-001")
		val c = determined.copy(determined = false, expected = null, question = "still open?")
		val actual = actualFor(c, corpus)
		assertEquals(determined.expected, actual)
		assertEquals("unverified", classify(c, actual).status)
	}

	@Test
	fun aCaseTheCorpusSaysAndroidCannotObserveIsUnverifiedAndNotRun() {
		val c = byId("KS-001").copy(unobserved = mapOf(PLATFORM to "not observable here"))
		val neverRun = Deps(loadPack = { error("the case was run") })
		val result = resultFor(c, corpus, neverRun)
		assertEquals(JsonNull, result.actual)
		assertEquals(Outcome("unverified", false, "not observable here"), result.outcome)
	}
}
