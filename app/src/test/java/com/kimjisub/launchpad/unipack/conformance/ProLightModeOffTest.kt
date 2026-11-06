package com.kimjisub.launchpad.unipack.conformance

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The play screen starts with Pro light mode off, which the corpus does not measure: it reports Android
 * with the mode on. Off, a keyLED file's round-button and logo lights are hidden on the screen and on the
 * Launchpad, and its pad lights are unchanged. Every corpus playback case that lights a round button or
 * the logo is run both ways; `android-pro-light-off.json` keeps both results.
 */
class ProLightModeOffTest {

	private val corpus = loadCorpus()
	private val pretty = Json { prettyPrint = true }

	@Test
	fun roundAndLogoLightsAreHiddenAndPadLightsKept() {
		val cases = corpus.cases.filter { c ->
			c.layer == "run" && c.determined && PLATFORM !in c.unobserved && (c.expected?.jsonObject?.get("checkpoints") as? JsonArray).orEmpty().any { it.jsonArray.any(::isRound) }
		}
		assertTrue("the corpus has playback cases that light round buttons or the logo", cases.isNotEmpty())
		val observed = cases.map { c ->
			val on = checkpointsOf(actualFor(c, corpus))
			val off = checkpointsOf(actualFor(c, corpus, options = ScreenOptions(proLight = false)))
			Triple(c, on, off)
		}
		save(observed)
		for ((c, on, off) in observed) {
			assertEquals("${c.id}: with Pro light mode on the corpus result holds", checkpointsOf(c.expected), on)
			assertEquals("${c.id}: Pro light mode off hides round and logo lights only", withoutRound(on), off)
		}
	}

	private fun checkpointsOf(result: JsonElement?): JsonArray = result!!.jsonObject.getValue("checkpoints").jsonArray

	/** A light event on a round button or the logo: the corpus puts them at x = -1. */
	private fun isRound(event: JsonElement): Boolean {
		val parts = event.jsonArray
		return parts[0].jsonPrimitive.content in setOf("ledOn", "ledOff") && parts[1].jsonPrimitive.int == -1
	}

	private fun withoutRound(checkpoints: JsonArray): JsonArray = buildJsonArray {
		for (checkpoint in checkpoints) add(buildJsonArray { checkpoint.jsonArray.filterNot(::isRound).forEach { add(it) } })
	}

	private fun save(observed: List<Triple<CorpusCase, JsonArray, JsonArray>>) {
		val dir = File(System.getenv("UNIPACK_CONFORMANCE_OUT") ?: "build/unipack-conformance").apply { mkdirs() }
		val report = buildJsonObject {
			put("platform", PLATFORM)
			put("corpusSha256", corpus.sha256)
			putJsonArray("cases") {
				for ((c, on, off) in observed) add(buildJsonObject {
					put("id", c.id)
					put("fingerprint", c.fingerprint)
					put("proLightOn", buildJsonObject { put("checkpoints", on) })
					put("proLightOff", buildJsonObject { put("checkpoints", off) })
				})
			}
		}
		File(dir, "$PLATFORM-pro-light-off.json").writeText(pretty.encodeToString(JsonElement.serializer(), report) + "\n")
	}
}
