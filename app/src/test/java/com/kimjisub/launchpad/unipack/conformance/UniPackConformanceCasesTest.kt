package com.kimjisub.launchpad.unipack.conformance

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.AfterClass
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.time.Instant
import java.util.Collections

/**
 * Runs every case of the shared corpus (src/test/resources/unipack-conformance/corpus.json, a byte
 * copy of unipad.io meta/unipack-conformance/corpus.json) through the real parser and runners.
 *
 * UNIPACK_CONFORMANCE_DUMP=1, and only that value, makes this a record-only run: every result is
 * written but no case is asserted, to look at differences before they are pinned in the corpus. Such
 * a run is never a pass: the class fails once at the end, so Gradle neither stores it as a passing
 * result nor reuses it for the next run. A passing run is still stored; run the task with
 * `--rerun --no-build-cache` when android.json has to be written again (see the corpus README).
 */
@RunWith(Parameterized::class)
class UniPackConformanceCasesTest(private val id: String) {

	@Test
	fun case() {
		val c = corpus.cases.first { it.id == id }
		verifyFingerprint(c, corpus)
		val result = resultFor(c, corpus)
		results.add(Result(c.id, c.fingerprint, result.outcome, result.actual))
		if (recordOnly) return
		assertFalse(
			"${c.id}: ${result.outcome.detail}\nactual: ${result.actual}\nexpected: ${c.expected}",
			result.outcome.unexpected,
		)
	}

	private class Result(val id: String, val fingerprint: String, val outcome: Outcome, val actual: JsonElement)

	companion object {
		private val corpus = loadCorpus()
		private val pretty = Json { prettyPrint = true }
		private val results: MutableList<Result> = Collections.synchronizedList(ArrayList())
		private val recordOnly = System.getenv("UNIPACK_CONFORMANCE_DUMP") == "1"
		private const val RECORD_ONLY_NOTICE = "UNIPACK_CONFORMANCE_DUMP=1: record-only run, no case was asserted"

		@JvmStatic
		@Parameterized.Parameters(name = "{0}")
		fun ids(): List<String> = corpus.cases.map { it.id }

		@JvmStatic
		@AfterClass
		fun report() {
			val sorted = synchronized(results) { results.sortedBy { it.id } }
			val dir = File(System.getenv("UNIPACK_CONFORMANCE_OUT") ?: "build/unipack-conformance").apply { mkdirs() }
			val json = buildJsonObject {
				put("platform", PLATFORM)
				put("corpusSha256", corpus.sha256)
				put("generatedAt", Instant.now().toString())
				put("assertions", if (recordOnly) "skipped" else "checked")
				putJsonArray("cases") {
					for (r in sorted) add(buildJsonObject {
						put("id", r.id)
						put("fingerprint", r.fingerprint)
						put("status", r.outcome.status)
						put("detail", r.outcome.detail)
						put("actual", r.actual)
					})
				}
			}
			File(dir, "$PLATFORM.json").writeText(pretty.encodeToString(JsonElement.serializer(), json) + "\n")
			for (r in sorted) {
				val line = buildJsonObject {
					put("platform", PLATFORM); put("id", r.id); put("fingerprint", r.fingerprint); put("status", r.outcome.status)
				}
				println("UNIPACK-CONFORMANCE $line")
			}
			println("UNIPACK-CONFORMANCE-CORPUS " + buildJsonObject {
				put("platform", PLATFORM); put("corpusSha256", corpus.sha256); put("cases", JsonPrimitive(sorted.size))
			})
			if (recordOnly) {
				println("UNIPACK-CONFORMANCE-RECORD-ONLY $RECORD_ONLY_NOTICE")
				fail(RECORD_ONLY_NOTICE)
			}
		}
	}
}
