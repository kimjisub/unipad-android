package com.kimjisub.launchpad.basefeatures

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.manager.PackArchive
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.conformance.identityFileSystem
import com.kimjisub.launchpad.unipack.conformance.observeSoundIdentity
import com.kimjisub.launchpad.unipack.conformance.parseResult
import com.kimjisub.launchpad.unipack.conformance.sha256Hex
import com.kimjisub.launchpad.unipack.conformance.soundIdentityOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The corpus file cases a macOS host cannot store: names that differ only in letter case, and names
 * whose composed and decomposed spellings must stay separate files. Android internal storage keeps
 * both, so the exact corpus bytes are written there and read by the real parser (and, for the sound
 * identity cases, the production ZIP extractor, decoder and sound runner).
 *
 * Each case leaves one record in `conformance-files/<id>.json` with the same `actual` the host
 * harness would report, the status it earns against the corpus, and every stored file's hash.
 * `scripts/merge_device_conformance.py` puts these records into the shared Android result file.
 */
@RunWith(AndroidJUnit4::class)
class ConformanceFileObservationTest : PlaybackScreenTest() {
    @Test fun composedKoreanReferenceFindsDecomposedFile() = observe("KS-G601")
    @Test fun decomposedKoreanReferenceFindsComposedFile() = observe("KS-G602")
    @Test fun composedAccentReferenceFindsDecomposedFile() = observe("KS-G603")
    @Test fun decomposedAccentReferenceFindsComposedFile() = observe("KS-G604")
    @Test fun candidateOrderUsesTheFullPath() = observe("KS-G617")
    @Test fun candidateSearchIncludesEveryFolder() = observe("KS-G618")
    @Test fun exactNameWinsOverFoldedCandidates() = observe("KS-G619")

    @Test fun composedExactNameWinsNormalizationCollision() = observe("KS-G620")
    @Test fun decomposedExactNameWinsNormalizationCollision() = observe("KS-G621")
    @Test fun normalizationFallbackSortsOriginalNames() = observe("KS-G622")
    @Test fun composedExactAccentWinsCollision() = observe("KS-G623")
    @Test fun decomposedExactAccentWinsCollision() = observe("KS-G624")
    @Test fun accentFallbackSortsOriginalNames() = observe("KS-G625")

    @Test fun decomposedAccentKeyLedQueuesByComposedName() = observe("KL-026")
    @Test fun decomposedHangulKeyLedQueuesByComposedName() = observe("KL-028")

    private fun observe(id: String) {
        val root = File(screen.context.filesDir, "conformance-files")
        try {
            val (case, digest) = DeviceConformanceCorpus(screen).install(id, root)
            assertTrue("Only file name fixtures belong here", case.optBoolean("requiresCaseSensitiveFiles") ||
                case.optBoolean("soundIdentity") || id.startsWith("KL-"))
            val probeRoot = File(screen.context.filesDir, "conformance-files-probe").apply { deleteRecursively(); check(mkdirs()) }
            val filesystem = try { identityFileSystem(probeRoot) } finally { probeRoot.deleteRecursively() }
            assertTrue("Internal storage must keep case-distinct names separate", filesystem.flag("caseSensitive"))
            assertTrue("Internal storage must keep composed and decomposed names separate", filesystem.flag("normalizationSensitive"))
            val files = case.getJSONArray("files")
            val declaredPaths = (0 until files.length()).map { files.getJSONObject(it).getString("path") }.sorted()
            val storedFiles = root.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(root).invariantSeparatorsPath }.toList()
            assertEquals("No input file may be merged, renamed or missing", declaredPaths,
                storedFiles.map { it.relativeTo(root).invariantSeparatorsPath })
            val fileEvidence = JSONArray()
            for (file in storedFiles) fileEvidence.put(JSONObject()
                .put("path", file.relativeTo(root).invariantSeparatorsPath)
                .put("bytes", file.length())
                .put("sha256", sha256Hex(file.readBytes())))

            val observation = if (case.optBoolean("soundIdentity")) observeIdentity(case, root) else observeParse(root)
            val expected = Json.parseToJsonElement(case.getJSONObject("expected").toString())
            val actual = observation.getValue("actual")
            // A sound-identity observation brings its own status: extraction that changed a sound's
            // bytes fails, and a selection not traced to its archive entry is unverified, even when
            // the chosen names match.
            val status = observation["status"]?.jsonPrimitive?.content ?: if (actual == expected) "pass" else "fail"
            val report = JSONObject(observation.toString())
                .put("platform", "android").put("id", id)
                .put("fingerprint", case.getString("fingerprint")).put("corpusSha256", digest)
                .put("source", if (case.optBoolean("soundIdentity")) SOURCE_IDENTITY else SOURCE_FILES)
                .put("filesystem", JSONObject(filesystem.toString()))
                .put("sdkInt", Build.VERSION.SDK_INT).put("device", Build.MODEL)
                .put("files", fileEvidence).put("status", status)
            assertTrue("Device file evidence was not saved", TestCaptures.save(screen.device, "conformance-files/$id.json") {
                it.writeText(report.toString(2) + "\n"); true
            })
            assertEquals("$id: actual result on device storage", expected, actual)
            assertEquals("$id: status on device storage (${observation["storage"]})", "pass", status)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun JsonObject.flag(key: String) = getValue(key).jsonPrimitive.content == "true"

    private fun observeParse(root: File): JsonObject {
        val pack = UniPackFolder(root).load().loadDetail()
        val actual: JsonElement = if (pack.criticalError) buildJsonObject { put("loaded", false) } else parseResult(pack, root)
        return buildJsonObject {
            put("actual", actual)
            put("rawErrors", pack.errorDetail)
        }
    }

    /**
     * Installation already proved every original path and byte was preserved. The production ZIP
     * extractor then unpacks them again, and decoder and runner choices are traced back to the ZIP.
     */
    private fun observeIdentity(case: JSONObject, root: File): JsonObject {
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                val entries = case.getJSONArray("files")
                for (i in 0 until entries.length()) {
                    val name = entries.getJSONObject(i).getString("path")
                    zip.putNextEntry(ZipEntry(name)); zip.write(File(root, name).readBytes()); zip.closeEntry()
                }
            }
        }.toByteArray()
        val archive = File(root.parentFile, "identity-input.zip").apply { writeBytes(bytes) }
        try {
            root.deleteRecursively(); check(root.mkdirs())
            PackArchive.extract(archive, root)
            val parsedCase = Json.parseToJsonElement(case.toString()).jsonObject
            val pack = UniPackFolder(root).load().loadDetail()
            // identityTraced and every storage entry's contentsPreserved stay in the record: the merge
            // accepts the selection only when both hold.
            return soundIdentityOutcome(parsedCase, observeSoundIdentity(bytes, root, pack, OboeAudioEngine::decodeOnly))
        } finally { archive.delete() }
    }

    companion object {
        /** The record sources the merge and the report check accept, one per kind of file case. */
        const val SOURCE_FILES = "device-internal-files"
        const val SOURCE_IDENTITY = "device-archive-decoder-runner"
    }
}
