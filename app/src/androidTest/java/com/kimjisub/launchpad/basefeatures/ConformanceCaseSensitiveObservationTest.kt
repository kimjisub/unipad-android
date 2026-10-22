package com.kimjisub.launchpad.basefeatures

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Confirms colliding names using real files and the real parser on Android internal storage. */
@RunWith(AndroidJUnit4::class)
class ConformanceCaseSensitiveObservationTest : PlaybackScreenTest() {
    @Test fun composedKoreanReferenceFindsDecomposedFile() = observe("KS-G601")
    @Test fun decomposedKoreanReferenceFindsComposedFile() = observe("KS-G602")
    @Test fun composedAccentReferenceFindsDecomposedFile() = observe("KS-G603")
    @Test fun decomposedAccentReferenceFindsComposedFile() = observe("KS-G604")
    @Test fun candidateOrderUsesTheFullPath() = observe("KS-G617")
    @Test fun candidateSearchIncludesEveryFolder() = observe("KS-G618")
    @Test fun exactNameWinsOverFoldedCandidates() = observe("KS-G619")

    private fun observe(id: String) {
        val root = File(screen.context.filesDir, "conformance-case-sensitive")
        try {
            val (case, digest) = DeviceConformanceCorpus(screen).install(id, root)
            assertTrue("Only sound filename fixtures belong here", case.optBoolean("requiresCaseSensitiveFiles") ||
                id in listOf("KS-G601", "KS-G602", "KS-G603", "KS-G604"))
            val probe = File(root, "case-probe").apply { mkdirs() }
            File(probe, "a").writeText("lower")
            assertFalse("Internal storage must keep case-distinct names separate", File(probe, "A").exists())
            probe.deleteRecursively()
            val files = case.getJSONArray("files")
            val declaredPaths = (0 until files.length()).map { files.getJSONObject(it).getString("path") }.sorted()
            val storedFiles = root.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(root).invariantSeparatorsPath }.toList()
            assertEquals("No input file may be merged or missing", declaredPaths, storedFiles.map { it.relativeTo(root).invariantSeparatorsPath })
            val fileEvidence = JSONArray()
            for (file in storedFiles) fileEvidence.put(JSONObject()
                .put("path", file.relativeTo(root).invariantSeparatorsPath)
                .put("bytes", file.length())
                .put("sha256", MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }))

            val pack = UniPackFolder(root).load().loadDetail()
            val actual = JSONObject().put("loaded", !pack.criticalError)
            if (!pack.criticalError) {
                // These fixtures have only keySound. Reject unexpected extra tables rather than
                // silently normalizing them away; this is not a general LED/auto-play serializer.
                check(pack.ledAnimationTable?.all { chain -> chain.all { row -> row.all { it.isNullOrEmpty() } } } != false)
                check(pack.autoPlayTable == null)
                val info = JSONObject().put("title", pack.title).put("producerName", pack.producerName)
                    .put("buttonX", pack.buttonX).put("buttonY", pack.buttonY).put("chain", pack.chain).put("squareButton", pack.squareButton)
                pack.website?.takeIf { it.isNotEmpty() }?.let { info.put("website", it) }
                val sounds = JSONArray()
                for (c in 0 until pack.chain) for (x in 0 until pack.buttonX) for (y in 0 until pack.buttonY) {
                    val queue = pack.soundTable?.get(c)?.get(x)?.get(y)?.takeIf { it.isNotEmpty() } ?: continue
                    val items = JSONArray()
                    for (sound in queue) items.put(JSONObject()
                        .put("file", sound.file.relativeTo(File(root, "sounds")).invariantSeparatorsPath)
                        .put("loop", sound.loop).put("wormhole", sound.wormhole))
                    sounds.put(JSONObject().put("c", c).put("x", x).put("y", y).put("queue", items))
                }
                val errors = JSONArray()
                pack.errorDetail?.lines()?.forEach { errors.put(normalizeError(it)) }
                actual.put("info", info).put("sounds", sounds).put("keyLedExist", pack.keyLedExist)
                    .put("leds", JSONArray()).put("autoPlay", JSONObject.NULL).put("errors", errors)
            }
            val expected = Json.parseToJsonElement(case.getJSONObject("expected").toString())
            val observed = Json.parseToJsonElement(actual.toString())
            val report = JSONObject().put("platform", "android").put("id", id)
                .put("fingerprint", case.getString("fingerprint")).put("corpusSha256", digest)
                .put("source", "device-internal-files").put("caseSensitiveFileSystem", true)
                .put("files", fileEvidence).put("status", if (expected == observed) "pass" else "fail")
                .put("actual", actual).put("rawErrors", pack.errorDetail ?: JSONObject.NULL)
            assertTrue("Device parser evidence was not saved", TestCaptures.save(screen.device, "conformance-files/$id.json") {
                it.writeText(report.toString(2) + "\n"); true
            })
            assertEquals("$id: actual file parser result", expected, observed)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun normalizeError(message: String): String {
        val section = Regex("^([A-Za-z.]+)\\s*:").find(message)?.groupValues?.get(1) ?: "unknown"
        val kind = when {
            Regex("format is (incorrect|not found)").containsMatchIn(message) -> "format"
            Regex("\\b(chain|x|y|loop|wormhole|coordinate|delay) is incorrect\\b|out of range").containsMatchIn(message) -> "range"
            Regex("was not found|directory not found|doesn't exist").containsMatchIn(message) -> "missing-file"
            Regex("was missing").containsMatchIn(message) -> "missing-field"
            else -> "other($message)"
        }
        return "$section:$kind"
    }
}
