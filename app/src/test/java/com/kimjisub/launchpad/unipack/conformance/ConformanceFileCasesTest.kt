package com.kimjisub.launchpad.unipack.conformance

import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConformanceFileCasesTest {
    private val corpus = loadCorpus()

    // The same archive-identity case with ASCII names also exercises this adapter on macOS,
    // whose filesystem cannot keep both Unicode spellings separate.
    private fun portableIdentityCase(): CorpusCase {
        val source = corpus.cases.first { it.id == "KS-G603" }
        return source.copy(
            files = source.files.map {
                CorpusFile(it.path.replace("cafe\u0301.wav", "tone.wav"),
                    it.text?.replace("café.wav", "tone.wav"), it.base64, it.asset)
            },
            expected = Json.parseToJsonElement(source.expected.toString().replace("cafe\u0301.wav", "tone.wav")),
        )
    }

    @Test fun identityObservationUsesTheArchiveAndSoundRunner() {
        val c = portableIdentityCase()
        val result = resultFor(c, corpus)
        assertEquals(c.expected, result.actual)
        assertEquals("pass", result.outcome.status)
    }

    @Test fun missingDetailLoadFailsAnIdentityCase() {
        val c = portableIdentityCase()
        val result = resultFor(c, corpus, Deps(loadPack = { UniPackFolder(it).load() }))
        assertEquals("fail", result.outcome.status)
        assertTrue(result.outcome.unexpected)
    }

    @Test fun everyRequiredFileCaseIsObservedOrProvesHostNameLoss() {
        val required = System.getProperty("os.name").startsWith("Linux") ||
            System.getenv("UNIPACK_REQUIRE_FILE_CASES") == "1"
        val cases = corpus.cases.filter { it.id in REQUIRED_FILE_CASE_IDS }
        assertEquals(13, cases.size)
        for (c in cases) {
            val result = resultFor(c, corpus)
            if (!required && result.outcome.status == "unverified") {
                assertEquals(c.id, JsonNull, result.actual)
                if (c.requiresCaseSensitiveFiles && !caseSensitiveFileSystem()) {
                    assertTrue(c.id, result.outcome.detail.contains("filesystem cannot preserve"))
                } else {
                    assertNotNull(c.id, result.outcome.storedInputPaths)
                    assertNotEquals(c.id, c.files.map { it.path }.sorted(), result.outcome.storedInputPaths)
                }
            } else {
                assertEquals("${c.id}: ${result.outcome.detail}", "pass", result.outcome.status)
                assertEquals(c.id, c.expected, result.actual)
            }
        }
    }

    companion object {
        val REQUIRED_FILE_CASE_IDS = (listOf("KS-G601", "KS-G602", "KS-G603", "KS-G604") +
            (617..625).map { "KS-G$it" }).toSet()
    }
}
