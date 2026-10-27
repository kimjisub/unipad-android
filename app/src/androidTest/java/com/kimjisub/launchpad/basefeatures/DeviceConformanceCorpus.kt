package com.kimjisub.launchpad.basefeatures

import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import java.io.File
import java.security.MessageDigest

/** Writes and fingerprints the exact corpus bytes on a device, without reading an expectation. */
internal class DeviceConformanceCorpus(private val screen: FeatureScreen) {
    data class Fixture(val case: JSONObject, val digest: String)

    fun install(id: String, pack: File): Fixture {
        val assets = screen.instrumentation.context.assets
        val bytes = assets.open("unipack-conformance/corpus.json").use { it.readBytes() }
        val digest = sha256(bytes)
        val declared = assets.open("unipack-conformance/corpus.sha256").bufferedReader().use { it.readText() }
        assertEquals("Corpus bytes must match the copied digest", "$digest  corpus.json\n", declared)
        val corpus = JSONObject(bytes.toString(Charsets.UTF_8))
        val cases = corpus.getJSONArray("cases")
        val case = (0 until cases.length()).map(cases::getJSONObject).single { it.getString("id") == id }
        pack.deleteRecursively()
        pack.mkdirs()
        val fingerprint = MessageDigest.getInstance("SHA-256")
        val files = case.getJSONArray("files")
        // Preserve corpus insertion order on disk. Sorting creation would hide a parser that picks
        // the first directory entry instead of the required full-path order (KS-G617).
        for (index in 0 until files.length()) {
            val entry = files.getJSONObject(index)
            File(pack, entry.getString("path")).apply {
                parentFile!!.mkdirs()
                writeBytes(contentOf(entry, corpus))
            }
        }
        // Fingerprints sort UTF-8 path bytes, independently of file creation order.
        val sorted = (0 until files.length()).map(files::getJSONObject).sortedWith { a, b ->
            val left = a.getString("path").toByteArray(Charsets.UTF_8)
            val right = b.getString("path").toByteArray(Charsets.UTF_8)
            var difference = 0
            for (i in 0 until minOf(left.size, right.size)) {
                difference = (left[i].toInt() and 255) - (right[i].toInt() and 255)
                if (difference != 0) break
            }
            if (difference == 0) left.size - right.size else difference
        }
        for (entry in sorted) {
            val path = entry.getString("path")
            val content = contentOf(entry, corpus)
            fingerprint.addFile(path, content)
        }
        assertEquals("Fixture fingerprint", case.getString("fingerprint"), hex(fingerprint.digest()))
        val storedFingerprint = MessageDigest.getInstance("SHA-256")
        for (entry in sorted) {
            val path = entry.getString("path")
            val content = File(pack, path).readBytes()
            storedFingerprint.addFile(path, content)
        }
        assertEquals("Stored fixture bytes", case.getString("fingerprint"), hex(storedFingerprint.digest()))
        return Fixture(case, digest)
    }

    private fun sha256(bytes: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun contentOf(entry: JSONObject, corpus: JSONObject): ByteArray = when {
        entry.has("text") -> entry.getString("text").toByteArray(Charsets.UTF_8)
        entry.has("base64") -> Base64.decode(entry.getString("base64"), Base64.DEFAULT)
        else -> Base64.decode(corpus.getJSONObject("assets").getJSONObject(entry.getString("asset")).getString("base64"), Base64.DEFAULT)
    }

    private fun MessageDigest.addFile(path: String, content: ByteArray) {
        update(path.toByteArray(Charsets.UTF_8))
        update(0.toByte())
        update(content.size.toString().toByteArray(Charsets.US_ASCII))
        update(0.toByte())
        update(content)
        update(10.toByte())
    }
}
