package com.kimjisub.launchpad.unipack.conformance

import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.runner.ChainObserver
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.text.Normalizer
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Reads only the actual input ZIP and selected disk bytes; has no access to expected results. */
class SoundIdentityProbe(archive: ByteArray, private val root: File) {
    private data class Entry(val path: String, val hash: String)
    private val entries = buildList {
        ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) add(Entry(entry.name, sha256Hex(zip.readBytes())))
            }
        }
    }

    fun identity(file: File): JsonObject {
        val hash = sha256Hex(file.readBytes())
        val path = file.relativeTo(root).invariantSeparatorsPath
        val matchingBytes = entries.filter { it.hash == hash }
        val candidates = equivalentPaths(matchingBytes, path)
        return buildJsonObject {
            put("archiveEntry", candidates.singleOrNull()?.let { JsonPrimitive(it.path) } ?: JsonNull)
            put("sha256", hash)
        }
    }

    private fun normalized(path: String): String = Normalizer.normalize(path, Normalizer.Form.NFC)

    private fun equivalentPaths(files: List<Entry>, path: String): List<Entry> =
        files.filter { normalized(it.path).equals(normalized(path), ignoreCase = true) }

    private fun matchingPaths(files: List<Entry>, path: String): List<Entry> =
        files.filter { normalized(it.path) == normalized(path) }.ifEmpty {
            equivalentPaths(files, path)
        }

    /** Storage spellings are diagnostics; only matching names AND bytes establish preservation. */
    fun diagnostics(): JsonArray {
        val storedFiles = root.walkTopDown().filter { it.isFile }.associate { file ->
            file.relativeTo(root).invariantSeparatorsPath to sha256Hex(file.readBytes())
        }
        return diagnostics(storedFiles)
    }

    /** A path/hash snapshot also allows case-sensitive storage to be checked on any host. */
    internal fun diagnostics(storedHashes: Map<String, String>): JsonArray {
        val storedFiles = storedHashes.map { (path, hash) -> Entry(path, hash) }
        val remaining = storedFiles.toMutableList()
        val matched = mutableMapOf<Entry, Entry>()
        // Reserve exact spellings first, independent of ZIP order. When distinct spellings
        // exist on disk, swapped bytes must not be reassigned to a different original name.
        for (entry in entries) {
            val exact = remaining.find { it.path == entry.path } ?: continue
            if (exact.hash == entry.hash || equivalentPaths(storedFiles, entry.path).size > 1) {
                matched[entry] = exact
                remaining.remove(exact)
            }
        }
        // A disk may normalize or change case. Match remaining bytes once each so two
        // identical originals collapsed into one file cannot both count as preserved.
        for (entry in entries.filterNot { it in matched }) {
            val stored = matchingPaths(remaining.filter { it.hash == entry.hash }, entry.path).firstOrNull()
                ?: continue
            matched[entry] = stored
            remaining.remove(stored)
        }
        return buildJsonArray {
            for (entry in entries) {
                val matchedFile = matched[entry]
                // If bytes were overwritten, keep the original path's current bytes as a diagnostic.
                val stored = matchedFile ?: matchingPaths(storedFiles, entry.path).singleOrNull()
                add(buildJsonObject {
                    put("archiveEntry", entry.path)
                    put("originalSha256", entry.hash)
                    put("storedPath", stored?.let { JsonPrimitive(it.path) } ?: JsonNull)
                    put("storedSha256", stored?.let { JsonPrimitive(it.hash) } ?: JsonNull)
                    put("contentsPreserved", matchedFile?.hash == entry.hash)
                })
            }
        }
    }
}

/** The corpus stays a separate input so earlier corpus hashes and observations remain immutable. */
class SoundIdentityCorpus(bytes: ByteArray, digest: String) {
    private val corpus = parseCorpus(bytes, digest)
    val sha256 = corpus.sha256
    private val document = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
    val cases = document.getValue("cases").jsonArray.map { it.jsonObject }
        .filter { it["soundIdentity"]?.jsonPrimitive?.booleanOrNull == true }

    init {
        check(document.getValue("cases").jsonArray.size == 269)
        check(cases.map { it.getValue("id").jsonPrimitive.content }.toSet() ==
            (listOf("KS-G601", "KS-G602", "KS-G603", "KS-G604") + (620..625).map { "KS-G$it" }).toSet())
    }

    fun archive(case: JsonObject): ByteArray {
        val input = case.toCase()
        verifyFingerprint(input, corpus)
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                for (file in input.files) {
                    zip.putNextEntry(ZipEntry(file.path)); zip.write(file.bytes(corpus)); zip.closeEntry()
                }
            }
        }.toByteArray()
    }
}

fun identityFileSystem(root: File, measuredType: String? = null): JsonObject {
    val probe = File(root, "filesystem-probe").apply { check(mkdirs()) }
    try {
        File(probe, "a").writeBytes(byteArrayOf(1))
        File(probe, "é").writeBytes(byteArrayOf(2))
        return buildJsonObject {
            put("type", measuredType ?: try { Files.getFileStore(root.toPath()).type() }
                catch (_: SecurityException) { "unavailable: sandbox denies getFileStore" })
            put("caseSensitive", !File(probe, "A").exists())
            put("normalizationSensitive", !File(probe, "e\u0301").exists())
        }
    } finally { probe.deleteRecursively() }
}

/** Records actual runner decode/load/play selection; opening a speaker is deliberately excluded. */
private class IdentityEngine(
    private val probe: SoundIdentityProbe,
    private val decodeAudio: (File) -> OboeAudioEngine.DecodedAudio?,
) : SoundRunner.Engine {
    private val decoded = IdentityHashMap<OboeAudioEngine.DecodedAudio, JsonObject>()
    private val loaded = mutableMapOf<Int, JsonObject>()
    val plays = mutableListOf<JsonObject>()
    private var next = 0
    override fun start() = true
    override fun stop() = Unit
    override fun decode(file: File): OboeAudioEngine.DecodedAudio? {
        val identity = probe.identity(file)
        return decodeAudio(file)?.also { synchronized(this) { decoded[it] = identity } }
    }
    @Synchronized override fun load(decoded: OboeAudioEngine.DecodedAudio): Int = (++next).also {
        loaded[it] = this.decoded.getValue(decoded)
    }
    @Synchronized fun identity(id: Int): JsonObject? = loaded[id]
    override fun unloadSound(soundId: Int) = Unit
    override fun unloadAll() = Unit
    @Synchronized override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int {
        plays += loaded.getValue(soundId)
        return plays.size
    }
    override fun stopVoice(stopKey: Int) = Unit
    override fun stopAllVoices() = Unit
}

/** Expectations enter only after this function returns, in [soundIdentityOutcome]. */
fun observeSoundIdentity(
    archive: ByteArray, root: File, pack: UniPack,
    decodeAudio: (File) -> OboeAudioEngine.DecodedAudio?,
): JsonObject {
    val probe = SoundIdentityProbe(archive, root)
    val engine = IdentityEngine(probe, decodeAudio)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val done = CountDownLatch(1)
    var failure: Throwable? = null
    val chain = ChainObserver().apply { range = 0 until pack.chain }
    val runner = SoundRunner(pack, chain, object : SoundRunner.LoadingListener {
        override fun onStart(soundCount: Int) = Unit
        override fun onProgressTick() = Unit
        override fun onEnd() { done.countDown() }
        override fun onException(throwable: Throwable) { failure = throwable; done.countDown() }
    }, scope, engine)
    try {
        check(done.await(30, TimeUnit.SECONDS)) { "Runner load timed out" }
        failure?.let { throw it }
        var traced = true
        val sounds = buildJsonArray {
            for (c in 0 until pack.chain) for (x in 0 until pack.buttonX) for (y in 0 until pack.buttonY) {
                val queue = pack.soundTable?.get(c)?.get(x)?.get(y)?.toList()?.takeIf { it.isNotEmpty() } ?: continue
                val observed = buildJsonArray {
                    chain.value = c
                    for (sound in queue) {
                        val identity = engine.identity(sound.id)
                        val before = engine.plays.size
                        runner.soundOn(x, y)
                        val played = engine.plays.drop(before).singleOrNull()
                        if (identity == null || identity["archiveEntry"] == JsonNull || played != identity) traced = false
                        add(buildJsonObject {
                            if (identity != null) identity.forEach { (k, v) -> put(k, v) }
                            else { put("archiveEntry", JsonNull); put("sha256", JsonNull) }
                            put("loop", sound.loop); put("wormhole", sound.wormhole)
                        })
                        runner.soundOff(x, y)
                    }
                }
                add(buildJsonObject { put("c", c); put("x", x); put("y", y); put("queue", observed) })
            }
        }
        // These fixtures contain no lights or autoplay. Refuse unexpected structures rather than hide them.
        check(!pack.keyLedExist && pack.autoPlayTable == null && pack.ledTableCount == 0)
        val actual = buildJsonObject {
            put("loaded", !pack.criticalError)
            putJsonObject("info") {
                put("title", pack.title); put("producerName", pack.producerName)
                put("buttonX", pack.buttonX); put("buttonY", pack.buttonY); put("chain", pack.chain)
                put("squareButton", pack.squareButton)
                pack.website?.takeIf { it.isNotEmpty() }?.let { put("website", it) }
            }
            put("sounds", sounds); put("keyLedExist", pack.keyLedExist)
            put("leds", JsonArray(emptyList())); put("autoPlay", JsonNull)
            putJsonArray("errors") {
                pack.errorDetail?.lines()?.forEach { add(JsonPrimitive(normalizeError(it))) }
            }
        }
        return buildJsonObject {
            put("actual", actual); put("identityTraced", traced)
            put("storage", probe.diagnostics()); put("playRequests", JsonArray(engine.plays.toList()))
            put("audioOutput", "unverified: no output stream or waveform capture")
        }
    } finally { runner.destroy(); scope.cancel() }
}

fun soundIdentityOutcome(case: JsonObject, observation: JsonObject): JsonObject = JsonObject(observation + mapOf(
    "id" to case.getValue("id"), "fingerprint" to case.getValue("fingerprint"),
    "status" to JsonPrimitive(when {
        observation.getValue("identityTraced").jsonPrimitive.boolean.not() -> "unverified"
        observation.getValue("storage").jsonArray.any { !it.jsonObject.getValue("contentsPreserved").jsonPrimitive.boolean } -> "fail"
        observation.getValue("actual") == case.getValue("expected") -> "pass"
        else -> "fail"
    }),
))
