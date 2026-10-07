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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A comparison that cannot fail proves nothing. Each test breaks one thing the corpus cases rely on
 * and requires the comparison to notice.
 */
class UniPackConformanceChecksTest {

	private val corpus = loadCorpus()

	private fun byId(id: String) = corpus.cases.first { it.id == id }

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
		val silent = Deps(wrapLedRunner = { real -> spyk(real).also { every { it.eventOn(any(), any()) } just Runs } })
		assertEquals("fail", classify(c, actualFor(c, corpus, silent)).status)
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
		val c = corpus.cases.firstOrNull { !it.determined }
		assertNotNull(c)
		assertEquals("unverified", classify(c!!, actualFor(c, corpus)).status)
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
