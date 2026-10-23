package com.kimjisub.launchpad.unipack.conformance

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun storageArchive(entries: List<Pair<String, ByteArray>>): ByteArray =
    ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            for ((path, bytes) in entries) {
                zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry()
            }
        }
    }.toByteArray()

/** Run on both host and device storage; no hard links or filesystem aliases are created. */
fun checkStorageOnlyNormalization(root: File) {
    val original = "sounds/cafe\u0301.wav"
    val storedName = "sounds/café.wav"
    val bytes = byteArrayOf(1, 2, 3)
    val archive = storageArchive(listOf(original to bytes))
    val stored = File(root, storedName).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
    // Only the composed spelling is stored, even on a normalization-sensitive disk.
    assertEquals(listOf(storedName), root.walkTopDown().filter { it.isFile }
        .map { it.relativeTo(root).invariantSeparatorsPath }.toList())
    val probe = SoundIdentityProbe(archive, root)
    assertEquals(original, probe.identity(stored)["archiveEntry"]?.jsonPrimitive?.content)
    assertEquals(sha256Hex(bytes), probe.identity(stored)["sha256"]?.jsonPrimitive?.content)
    assertEquals("true", probe.diagnostics().single().jsonObject["contentsPreserved"]?.jsonPrimitive?.content)
}

/** Requires storage that keeps both Unicode spellings as separate files. */
fun checkIdenticalOriginalsStoredSeparately(root: File) {
    val paths = listOf("sounds/café.wav", "sounds/cafe\u0301.wav")
    val bytes = byteArrayOf(1, 2, 3)
    val archive = storageArchive(paths.map { it to bytes })
    for (path in paths) File(root, path).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
    val storedPaths = root.walkTopDown().filter { it.isFile }
        .map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
    assumeTrue("Storage merges Unicode spellings; separate-entry check requires both files",
        storedPaths == paths.toSet())
    val diagnostics = SoundIdentityProbe(archive, root).diagnostics()
    assertEquals(2, diagnostics.size)
    for (item in diagnostics.map { it.jsonObject }) {
        assertEquals(item["archiveEntry"], item["storedPath"])
        assertEquals(sha256Hex(bytes), item["storedSha256"]?.jsonPrimitive?.content)
        assertEquals("true", item["contentsPreserved"]?.jsonPrimitive?.content)
    }
}
