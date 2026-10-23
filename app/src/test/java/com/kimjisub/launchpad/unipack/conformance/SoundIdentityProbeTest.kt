package com.kimjisub.launchpad.unipack.conformance

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SoundIdentityProbeTest {
    @get:Rule val temporary = TemporaryFolder()
    private val composed = "sounds/café.wav"
    private val decomposed = "sounds/cafe\u0301.wav"
    private val a = byteArrayOf(1, 2, 3)
    private val b = byteArrayOf(4, 5, 6)

    private fun archive(entries: List<Pair<String, ByteArray>> = listOf(composed to a, decomposed to b)) = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun selected(path: String, bytes: ByteArray): File = File(temporary.root, path).apply {
        parentFile!!.mkdirs(); writeBytes(bytes)
    }

    @Test fun wrongEntryAndContentsRemainDifferentFromTheExpectedIdentity() {
        val probe = SoundIdentityProbe(archive(), temporary.root)
        val actual = probe.identity(selected(decomposed, b))
        assertEquals(decomposed, actual["archiveEntry"]?.jsonPrimitive?.content)
        assertEquals(sha256Hex(b), actual["sha256"]?.jsonPrimitive?.content)
        assertNotEquals(buildJsonObject { put("archiveEntry", composed); put("sha256", sha256Hex(a)) }, actual)
    }

    @Test fun wrongBytesUnderTheRightNameCannotReportTheRightIdentity() {
        val probe = SoundIdentityProbe(archive(), temporary.root)
        val actual = probe.identity(selected(composed, b))
        assertEquals(decomposed, actual["archiveEntry"]?.jsonPrimitive?.content)
        assertEquals(sha256Hex(b), actual["sha256"]?.jsonPrimitive?.content)
    }

    @Test fun overwrittenContentsAreNotReportedAsTheOriginalEntry() {
        val probe = SoundIdentityProbe(archive(), temporary.root)
        val file = selected(composed, a)
        assertEquals(composed, probe.identity(file)["archiveEntry"]?.jsonPrimitive?.content)
        file.writeBytes(b)
        assertNotEquals(composed, probe.identity(file)["archiveEntry"]?.jsonPrimitive?.content)
    }

    @Test fun overwrittenUnselectedContentsCannotTurnAnOtherwiseMatchingResultIntoAPass() {
        val actual = buildJsonObject { put("loaded", true) }
        val case = buildJsonObject { put("id", "example"); put("fingerprint", "input"); put("expected", actual) }
        val observation = buildJsonObject {
            put("actual", actual); put("identityTraced", true)
            put("storage", JsonArray(listOf(buildJsonObject { put("contentsPreserved", false) })))
        }
        assertEquals("fail", soundIdentityOutcome(case, observation)["status"]?.jsonPrimitive?.content)
    }

    @Test fun anUntraceableIdentityRemainsUnverifiedEvenWhenStorageLossIsKnown() {
        val case = buildJsonObject { put("id", "example"); put("fingerprint", "input"); put("expected", JsonNull) }
        val observation = buildJsonObject {
            put("actual", JsonNull); put("identityTraced", false)
            put("storage", JsonArray(listOf(buildJsonObject { put("contentsPreserved", false) })))
        }
        assertEquals("unverified", soundIdentityOutcome(case, observation)["status"]?.jsonPrimitive?.content)
    }

    @Test fun identicalBytesInTwoOriginalNormalizationCandidatesRemainUnverified() {
        val probe = SoundIdentityProbe(archive(listOf(composed to a, decomposed to a)), temporary.root)
        val actual = probe.identity(selected(composed, a))
        assertEquals(JsonNull, actual["archiveEntry"])
        assertEquals(sha256Hex(a), actual["sha256"]?.jsonPrimitive?.content)
    }

    @Test fun matchingBytesUnderADifferentDirectoryCannotEstablishIdentity() {
        val probe = SoundIdentityProbe(archive(listOf(decomposed to a)), temporary.root)
        assertEquals(JsonNull, probe.identity(selected("other/café.wav", a))["archiveEntry"])
        assertEquals("false", probe.diagnostics().single().jsonObject["contentsPreserved"]?.jsonPrimitive?.content)
    }

    @Test fun aStorageOnlyNormalizationChangeKeepsTheOriginalArchiveIdentity() {
        checkStorageOnlyNormalization(temporary.root)
    }

    @Test fun identicalOriginalsStoredSeparatelyKeepBothContents() {
        val probe = SoundIdentityProbe(archive(listOf(composed to a, decomposed to a)), temporary.root)
        val diagnostics = probe.diagnostics(mapOf(composed to sha256Hex(a), decomposed to sha256Hex(a)))
        assertEquals(2, diagnostics.size)
        for (item in diagnostics.map { it.jsonObject }) {
            assertEquals(item["archiveEntry"], item["storedPath"])
            assertEquals(item["originalSha256"], item["storedSha256"])
            assertEquals("true", item["contentsPreserved"]?.jsonPrimitive?.content)
        }
    }

    @Test fun identicalOriginalsCollapsedToOneFileLoseOneStoredEntry() {
        val probe = SoundIdentityProbe(archive(listOf(composed to a, decomposed to a)), temporary.root)
        val diagnostics = probe.diagnostics(mapOf(composed to sha256Hex(a))).map { it.jsonObject }
        assertEquals(listOf("true", "false"), diagnostics.map { it["contentsPreserved"]?.jsonPrimitive?.content })
    }

    @Test fun swappedContentsUnderOriginalNamesAreNotPreserved() {
        val probe = SoundIdentityProbe(archive(), temporary.root)
        val diagnostics = probe.diagnostics(mapOf(composed to sha256Hex(b), decomposed to sha256Hex(a)))
        for (item in diagnostics.map { it.jsonObject }) {
            assertEquals(item["archiveEntry"], item["storedPath"])
            assertNotEquals(item["originalSha256"], item["storedSha256"])
            assertEquals("false", item["contentsPreserved"]?.jsonPrimitive?.content)
        }
    }

    @Test fun swappedContentsUnderCaseVariantOriginalNamesAreNotPreserved() {
        val originals = listOf("sounds/a.wav" to a, "sounds/A.wav" to b)
        for (ordered in listOf(originals, originals.reversed())) {
            val probe = SoundIdentityProbe(archive(ordered), temporary.root)
            val diagnostics = probe.diagnostics(mapOf("sounds/a.wav" to sha256Hex(b), "sounds/A.wav" to sha256Hex(a)))
            assertEquals(2, diagnostics.size)
            for (item in diagnostics.map { it.jsonObject }) {
                assertEquals(item["archiveEntry"], item["storedPath"])
                assertNotEquals(item["originalSha256"], item["storedSha256"])
                assertEquals("false", item["contentsPreserved"]?.jsonPrimitive?.content)
            }
        }
    }

    @Test fun identicalBytesInTwoOriginalCaseCandidatesRemainUnverified() {
        val probe = SoundIdentityProbe(archive(listOf("sounds/a.wav" to a, "sounds/A.wav" to a)), temporary.root)
        val actual = probe.identity(selected("sounds/a.wav", a))
        assertEquals(JsonNull, actual["archiveEntry"])
        assertEquals(sha256Hex(a), actual["sha256"]?.jsonPrimitive?.content)
    }

    @Test fun aStorageOnlyCaseChangeKeepsIdentityAndContents() {
        val probe = SoundIdentityProbe(archive(listOf("sounds/a.wav" to a)), temporary.root)
        val actual = probe.identity(selected("SOUNDS/A.WAV", a))
        assertEquals("sounds/a.wav", actual["archiveEntry"]?.jsonPrimitive?.content)
        val diagnostics = probe.diagnostics().single().jsonObject
        assertEquals("SOUNDS/A.WAV", diagnostics["storedPath"]?.jsonPrimitive?.content)
        assertEquals("true", diagnostics["contentsPreserved"]?.jsonPrimitive?.content)
    }
}
