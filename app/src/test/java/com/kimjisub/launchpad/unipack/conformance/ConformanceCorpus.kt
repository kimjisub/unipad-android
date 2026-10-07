package com.kimjisub.launchpad.unipack.conformance

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * The cross-platform conformance corpus (unipad.io meta/unipack-conformance) as this module's copy
 * reads it. Nothing here knows how Android parses a pack; see [ConformanceHarness].
 */
const val PLATFORM = "android"

class CorpusFile(val path: String, val text: String?, val base64: String?, val asset: String?)

class Known(val status: String, val actual: JsonElement, val note: String)

data class CorpusCase(
	val id: String,
	val layer: String,
	val title: String,
	val files: List<CorpusFile>,
	val fingerprint: String,
	val determined: Boolean,
	val expected: JsonElement?,
	val question: String?,
	val scenario: List<JsonObject>,
	val known: Map<String, Known>,
	/** Per platform, why its harness cannot observe this case; it is unverified there and not run. */
	val unobserved: Map<String, String>,
)

class Corpus(val assets: Map<String, String>, val cases: List<CorpusCase>, val sha256: String)

private const val CORPUS_RESOURCE = "/unipack-conformance/corpus.json"

fun sha256Hex(bytes: ByteArray): String =
	MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

fun loadCorpus(): Corpus {
	val bytes = Corpus::class.java.getResourceAsStream(CORPUS_RESOURCE)?.use { it.readBytes() }
		?: error("$CORPUS_RESOURCE is not on the test classpath")
	val root = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
	val assets = root.getValue("assets").jsonObject.mapValues { it.value.jsonObject.getValue("base64").jsonPrimitive.content }
	val cases = root.getValue("cases").jsonArray.map { it.jsonObject.toCase() }
	return Corpus(assets, cases, sha256Hex(bytes))
}

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.content

private fun JsonObject.toCase(): CorpusCase = CorpusCase(
	id = getValue("id").jsonPrimitive.content,
	layer = getValue("layer").jsonPrimitive.content,
	title = getValue("title").jsonPrimitive.content,
	files = getValue("files").jsonArray.map {
		val f = it.jsonObject
		CorpusFile(f.getValue("path").jsonPrimitive.content, f.string("text"), f.string("base64"), f.string("asset"))
	},
	fingerprint = getValue("fingerprint").jsonPrimitive.content,
	determined = getValue("expectation").jsonPrimitive.content == "determined",
	expected = this["expected"],
	question = string("question"),
	scenario = (this["scenario"] as? JsonArray)?.map { it.jsonObject } ?: emptyList(),
	known = (this["known"] as? JsonObject)?.mapValues {
		val k = it.value.jsonObject
		Known(k.getValue("status").jsonPrimitive.content, k.getValue("actual"), k.getValue("note").jsonPrimitive.content)
	} ?: emptyMap(),
	unobserved = (this["unobserved"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap(),
)

fun CorpusFile.bytes(corpus: Corpus): ByteArray = when {
	text != null -> text.toByteArray(Charsets.UTF_8)
	base64 != null -> Base64.getDecoder().decode(base64)
	asset != null -> Base64.getDecoder().decode(corpus.assets.getValue(asset))
	else -> error("file $path has no content")
}

/** sha256 of the files sorted by the UTF-8 bytes of their path; per file `path 0x00 length 0x00 bytes 0x0A`. */
fun fingerprintOf(files: List<CorpusFile>, corpus: Corpus): String {
	val digest = MessageDigest.getInstance("SHA-256")
	val sorted = files.sortedWith { a, b -> compareBytes(a.path.toByteArray(Charsets.UTF_8), b.path.toByteArray(Charsets.UTF_8)) }
	for (file in sorted) {
		val bytes = file.bytes(corpus)
		digest.update(file.path.toByteArray(Charsets.UTF_8))
		digest.update(0)
		digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
		digest.update(0)
		digest.update(bytes)
		digest.update(10)
	}
	return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun compareBytes(a: ByteArray, b: ByteArray): Int {
	for (i in 0 until minOf(a.size, b.size)) {
		val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
		if (d != 0) return d
	}
	return a.size - b.size
}

/** Refuses a case whose files no longer hash to the fingerprint it declares. */
fun verifyFingerprint(c: CorpusCase, corpus: Corpus) {
	val actual = fingerprintOf(c.files, corpus)
	check(actual == c.fingerprint) { "${c.id}: input files hash to $actual, the case declares ${c.fingerprint}" }
}

fun CorpusCase.writeTo(root: File, corpus: Corpus) {
	for (file in files) {
		val target = File(root, file.path)
		requireNotNull(target.parentFile).mkdirs()
		target.writeBytes(file.bytes(corpus))
	}
}

/** "keySound : [..] format is incorrect" -> "keySound:format". */
fun normalizeError(message: String): String {
	val section = Regex("^([A-Za-z.]+)\\s*:").find(message)?.groupValues?.get(1) ?: "unknown"
	val kind = when {
		Regex("format is (incorrect|not found)").containsMatchIn(message) -> "format"
		Regex("\\b(chain|x|y|loop|coordinate|delay) is incorrect\\b|out of range").containsMatchIn(message) -> "range"
		Regex("was not found|directory not found|doesn't exist").containsMatchIn(message) -> "missing-file"
		Regex("was missing").containsMatchIn(message) -> "missing-field"
		else -> "other($message)"
	}
	return "$section:$kind"
}

data class Outcome(val status: String, val unexpected: Boolean, val detail: String)

fun classify(c: CorpusCase, actual: JsonElement, platform: String = PLATFORM): Outcome {
	if (!c.determined) return Outcome("unverified", false, "no doc or stated intent decides this: ${c.question}")
	val known = c.known[platform]
	if (actual == c.expected) {
		return if (known != null) Outcome("pass", true, "a pinned difference no longer occurs; update divergences.json")
		else Outcome("pass", false, "")
	}
	if (known != null) {
		return if (actual == known.actual) Outcome(known.status, false, known.note)
		else Outcome("fail", true, "the result changed from the pinned difference")
	}
	return Outcome("fail", true, "differs from the expected result and nothing in the corpus accounts for it")
}
