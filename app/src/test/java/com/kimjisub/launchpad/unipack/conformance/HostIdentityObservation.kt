package com.kimjisub.launchpad.unipack.conformance

import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.manager.PackArchive
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Uses production extraction, parsing and runner selection; native decoding/output remain device checks. */
internal fun hostIdentityResult(c: CorpusCase, corpus: Corpus, deps: Deps): CaseResult {
    require(c.layer == "parse" && !c.screenInputs)
    val scratch = Files.createTempDirectory("conformance-identity").toFile()
    val root = File(scratch, "pack").apply { check(mkdirs()) }
    try {
        // Prove this host can represent the input before blaming production extraction for lost files.
        c.writeTo(root, corpus)
        val paths = root.walkTopDown().filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }.sorted().toList()
        if (paths != c.files.map { it.path }.sorted()) throw InputNamesNotPreserved(paths)
        val bytes = ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                for (file in c.files) {
                    zip.putNextEntry(ZipEntry(file.path)); zip.write(file.bytes(corpus)); zip.closeEntry()
                }
            }
        }.toByteArray()
        val archive = File(scratch, "input.zip").apply { writeBytes(bytes) }
        root.deleteRecursively(); check(root.mkdirs())
        PackArchive.extract(archive, root)
        val observation = observeSoundIdentity(bytes, root, deps.loadPack(root)) {
            // File selection is observed before this substitute; no native decoder or speaker is used.
            OboeAudioEngine.DecodedAudio(ShortArray(1), 1, 1, 44_100)
        }
        val storage = observation.getValue("storage").jsonArray
        val evidence = buildJsonObject {
            put("identityTraced", observation.getValue("identityTraced"))
            put("contentsPreserved", storage.all { it.jsonObject.getValue("contentsPreserved").jsonPrimitive.boolean })
            put("storageEntries", storage.size)
        }
        val actual = observation.getValue("actual")
        val classified = classify(c, actual)
        val outcome = if (!evidence.getValue("identityTraced").jsonPrimitive.boolean ||
            !evidence.getValue("contentsPreserved").jsonPrimitive.boolean) {
            Outcome("fail", true, "archive extraction or runner selection lost original file identity or contents")
        } else classified
        return CaseResult(actual, outcome.copy(storedInputPaths = paths, identityEvidence = evidence))
    } finally { scratch.deleteRecursively() }
}
