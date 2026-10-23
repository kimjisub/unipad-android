package com.kimjisub.launchpad.unipack.conformance

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
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
        assertEquals(soundIdentityHash(b), actual["sha256"]?.jsonPrimitive?.content)
        assertNotEquals(buildJsonObject { put("archiveEntry", composed); put("sha256", soundIdentityHash(a)) }, actual)
    }

    @Test fun wrongBytesUnderTheRightNameCannotReportTheRightIdentity() {
        val probe = SoundIdentityProbe(archive(), temporary.root)
        assertNotEquals(composed, probe.identity(selected(composed, b))["archiveEntry"]?.jsonPrimitive?.content)
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

    @Test fun aStorageOnlyNormalizationChangeKeepsTheOriginalArchiveIdentity() {
        // A normalization-insensitive filesystem exposes both spellings as the same inode.
        val original = selected(composed, a)
        val alias = File(temporary.root, decomposed)
        if (!alias.exists()) java.nio.file.Files.createLink(alias.toPath(), original.toPath())
        val probe = SoundIdentityProbe(archive(listOf(composed to a)), temporary.root)
        assertEquals(composed, probe.identity(alias)["archiveEntry"]?.jsonPrimitive?.content)
        assertEquals(soundIdentityHash(a), probe.identity(alias)["sha256"]?.jsonPrimitive?.content)
    }
}
