package com.kimjisub.launchpad.unipack.conformance

import kotlinx.serialization.json.JsonElement

private const val CORPUS_RESOURCE = "/unipack-conformance/corpus.json"
private const val DIGEST_RESOURCE = "/unipack-conformance/corpus.sha256"

private fun resourceBytes(name: String): ByteArray? = Corpus::class.java.getResourceAsStream(name)?.use { it.readBytes() }

fun loadCorpus(): Corpus {
	val bytes = resourceBytes(CORPUS_RESOURCE) ?: error("$CORPUS_RESOURCE is not on the test classpath")
	return parseCorpus(bytes, resourceBytes(DIGEST_RESOURCE)?.toString(Charsets.UTF_8))
}

data class Outcome(val status: String, val unexpected: Boolean, val detail: String, val storedInputPaths: List<String>? = null, val operationAdapter: String? = null)

fun classify(c: CorpusCase, actual: JsonElement, platform: String = PLATFORM): Outcome {
	if (!c.determined) return Outcome("unverified", false, "no doc or stated intent decides this: ${c.question}")
	val known = c.known[platform]
	if (actual == c.expected) {
		return if (known != null) Outcome("pass", true, "a pinned difference no longer occurs; update divergences.json")
		else Outcome("pass", false, "")
	}
	if (known != null) {
		return if (known.matches(actual)) Outcome(known.status, false, known.note)
		else Outcome("fail", true, "the result changed from the pinned difference")
	}
	return Outcome("fail", true, "differs from the expected result and nothing in the corpus accounts for it")
}
