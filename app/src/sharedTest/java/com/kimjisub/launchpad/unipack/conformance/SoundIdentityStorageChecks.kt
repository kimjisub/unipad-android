package com.kimjisub.launchpad.unipack.conformance

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Run on both host and device storage; no hard links or filesystem aliases are created. */
fun checkStorageOnlyNormalization(root: File) {
    val original = "sounds/cafe\u0301.wav"
    val storedName = "sounds/café.wav"
    val bytes = byteArrayOf(1, 2, 3)
    val archive = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(original)); zip.write(bytes); zip.closeEntry()
        }
    }.toByteArray()
    val stored = File(root, storedName).apply { parentFile!!.mkdirs(); writeBytes(bytes) }
    // Only the composed spelling is stored, even on a normalization-sensitive disk.
    assertEquals(listOf(storedName), root.walkTopDown().filter { it.isFile }
        .map { it.relativeTo(root).invariantSeparatorsPath }.toList())
    val probe = SoundIdentityProbe(archive, root)
    assertEquals(original, probe.identity(stored)["archiveEntry"]?.jsonPrimitive?.content)
    assertEquals(sha256Hex(bytes), probe.identity(stored)["sha256"]?.jsonPrimitive?.content)
    assertEquals("true", probe.diagnostics().single().jsonObject["contentsPreserved"]?.jsonPrimitive?.content)
}
