package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Decodes the real fixture WAVs, records the actual runner requests, and never opens a speaker. */
class RecordingAudio : SoundRunner.Engine {
    val plays = CopyOnWriteArrayList<Int>()
    val loops = CopyOnWriteArrayList<Int>()
    val stoppedVoices = CopyOnWriteArrayList<Int>()
    /** Play and stop requests in the order the runner made them, each ending in its elapsedRealtime. */
    val requests = CopyOnWriteArrayList<List<Any>>()
    val loaded = ConcurrentHashMap<String, Int>()
    val silences = AtomicInteger()
    val stops = AtomicInteger()
    val starts = AtomicInteger()
    private val nextId = AtomicInteger()
    private var silencedThrough = 0
    // Equal PCM can belong to different files; the load token, not its value, identifies a file.
    private val decodedFiles = Collections.synchronizedMap(IdentityHashMap<OboeAudioEngine.DecodedAudio, String>())
    private data class ReusableFixture(val bytes: ByteArray, val decoded: OboeAudioEngine.DecodedAudio)
    @Volatile private var reusableFixture: ReusableFixture? = null

    /** Opts a fixture into one real decode; only byte-identical copies may reuse its PCM. */
    fun reuseDecodedFixture(file: File) {
        reusableFixture = ReusableFixture(file.readBytes(), checkNotNull(OboeAudioEngine.decodeOnly(file)))
    }
    override fun start(): Boolean {
        starts.incrementAndGet()
        return true
    }
    override fun stop() { stops.incrementAndGet() }
    override fun decode(file: File, interrupted: () -> Boolean): OboeAudioEngine.DecodedAudio? {
        if (interrupted()) throw OboeAudioEngine.DecodeInterruptedException()
        val fixture = reusableFixture
        val decoded = if (fixture != null && file.readBytes().contentEquals(fixture.bytes)) {
            if (interrupted()) throw OboeAudioEngine.DecodeInterruptedException()
            fixture.decoded.copy()
        } else OboeAudioEngine.decodeOnly(file, interrupted) ?: return null
        decodedFiles[decoded] = file.name
        return decoded
    }
    override fun load(decoded: OboeAudioEngine.DecodedAudio): Int = nextId.incrementAndGet().also {
        loaded[decodedFiles.getValue(decoded)] = it
    }
    override fun unloadSound(soundId: Int) = Unit
    override fun unloadAll() = Unit
    /** The stop key of a voice is its 1-based position in [plays]. */
    @Synchronized override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int {
        loops += loop
        plays += soundId
        requests += listOf("play", soundId, loop, plays.size, SystemClock.elapsedRealtime())
        return plays.size
    }
    @Synchronized override fun stopVoice(stopKey: Int) {
        stoppedVoices += stopKey
        requests += listOf("stop", stopKey, SystemClock.elapsedRealtime())
    }
    @Synchronized override fun stopAllVoices() {
        requests += listOf("stopAll", SystemClock.elapsedRealtime())
        silencedThrough = plays.size
        silences.incrementAndGet()
    }

    /** Clears request history after the previous play screen has closed. */
    @Synchronized fun resetPlaybackRequests() {
        plays.clear()
        loops.clear()
        stoppedVoices.clear()
        requests.clear()
        silencedThrough = 0
    }

    /** Sound IDs of the infinite loops (`loop == -1`) started and not stopped since. */
    @Synchronized fun ringingLoops(): List<Int> = loops.indices
        .filter { loops[it] == -1 && it + 1 > silencedThrough && it + 1 !in stoppedVoices }
        .map { plays[it] }
}
