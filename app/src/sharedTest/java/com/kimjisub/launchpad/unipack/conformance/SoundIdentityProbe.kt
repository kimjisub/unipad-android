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
import java.security.MessageDigest
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.encoding.Base64

fun soundIdentityHash(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

/** Reads only the actual input ZIP and selected disk bytes; has no access to expected results. */
class SoundIdentityProbe(archive: ByteArray, private val root: File) {
    private data class Entry(val path: String, val hash: String)
    private val entries = buildList {
        ZipInputStream(archive.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) add(Entry(entry.name, soundIdentityHash(zip.readBytes())))
            }
        }
    }

    fun identity(file: File): JsonObject {
        val hash = soundIdentityHash(file.readBytes())
        val candidates = entries.filter { entry ->
            val alias = File(root, entry.path)
            alias.isFile && Files.isSameFile(alias.toPath(), file.toPath()) && entry.hash == hash
        }
        return buildJsonObject {
            put("archiveEntry", candidates.singleOrNull()?.let { JsonPrimitive(it.path) } ?: JsonNull)
            put("sha256", hash)
        }
    }

    /** Storage spellings and overwritten originals are diagnostics, never normalized identities. */
    fun diagnostics(): JsonArray = buildJsonArray {
        for (entry in entries) {
            val alias = File(root, entry.path)
            val stored = root.walkTopDown().filter { it.isFile }.firstOrNull {
                alias.isFile && Files.isSameFile(alias.toPath(), it.toPath())
            }
            add(buildJsonObject {
                put("archiveEntry", entry.path)
                put("originalSha256", entry.hash)
                put("storedPath", stored?.let { JsonPrimitive(it.relativeTo(root).invariantSeparatorsPath) } ?: JsonNull)
                val hash = stored?.let { soundIdentityHash(it.readBytes()) }
                put("storedSha256", hash?.let(::JsonPrimitive) ?: JsonNull)
                put("contentsPreserved", hash == entry.hash)
            })
        }
    }
}

/** The corpus stays a separate input so earlier corpus hashes and observations remain immutable. */
class SoundIdentityCorpus(bytes: ByteArray, digest: String) {
    val sha256 = soundIdentityHash(bytes)
    private val document = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
    val cases = document.getValue("cases").jsonArray.map { it.jsonObject }
        .filter { it["soundIdentity"]?.jsonPrimitive?.booleanOrNull == true }

    init {
        check(digest == "$sha256  corpus.json\n") { "Corpus digest mismatch" }
        check(document.getValue("cases").jsonArray.size == 269)
        check(cases.map { it.getValue("id").jsonPrimitive.content }.toSet() ==
            (listOf("KS-G601", "KS-G602", "KS-G603", "KS-G604") + (620..625).map { "KS-G$it" }).toSet())
    }

    fun archive(case: JsonObject): ByteArray {
        val files = case.getValue("files").jsonArray.map { value ->
            val f = value.jsonObject
            val data = when {
                "text" in f -> f.getValue("text").jsonPrimitive.content.encodeToByteArray()
                "base64" in f -> Base64.decode(f.getValue("base64").jsonPrimitive.content)
                else -> Base64.decode(document.getValue("assets").jsonObject
                    .getValue(f.getValue("asset").jsonPrimitive.content).jsonObject.getValue("base64").jsonPrimitive.content)
            }
            f.getValue("path").jsonPrimitive.content to data
        }
        val fingerprint = MessageDigest.getInstance("SHA-256")
        // The fingerprint orders UTF-8 path bytes, independently of archive insertion order.
        for ((path, data) in files.sortedWith { a, b ->
            val x = a.first.encodeToByteArray(); val y = b.first.encodeToByteArray()
            var comparison = 0
            for (i in 0 until minOf(x.size, y.size)) {
                comparison = (x[i].toInt() and 255) - (y[i].toInt() and 255)
                if (comparison != 0) break
            }
            if (comparison == 0) x.size - y.size else comparison
        }) {
            fingerprint.update(path.encodeToByteArray()); fingerprint.update(0)
            fingerprint.update(data.size.toString().encodeToByteArray()); fingerprint.update(0)
            fingerprint.update(data); fingerprint.update(10)
        }
        check(fingerprint.digest().joinToString("") { "%02x".format(it) } == case.getValue("fingerprint").jsonPrimitive.content)
        return ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                for ((path, data) in files) {
                    zip.putNextEntry(ZipEntry(path)); zip.write(data); zip.closeEntry()
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
                pack.errorDetail?.lines()?.forEach { message ->
                    val section = Regex("^([A-Za-z.]+)\\s*:").find(message)?.groupValues?.get(1) ?: "unknown"
                    val kind = if (Regex("was not found|directory not found|doesn't exist").containsMatchIn(message)) "missing-file" else "other($message)"
                    add(JsonPrimitive("$section:$kind"))
                }
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
