package com.kimjisub.launchpad.basefeatures

import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Decodes the real fixture WAVs, records the actual runner requests, and never opens a speaker. */
class RecordingAudio : SoundRunner.Engine {
    val plays = CopyOnWriteArrayList<Int>()
    val loops = CopyOnWriteArrayList<Int>()
    val stoppedVoices = CopyOnWriteArrayList<Int>()
    val loaded = ConcurrentHashMap<String, Int>()
    val silences = AtomicInteger()
    val stops = AtomicInteger()
    val starts = AtomicInteger()
    private val nextId = AtomicInteger()
    private var silencedThrough = 0
    private val decodedFiles = ConcurrentHashMap<OboeAudioEngine.DecodedAudio, String>()
    override fun start(): Boolean {
        starts.incrementAndGet()
        return true
    }
    override fun stop() { stops.incrementAndGet() }
    override fun decode(file: File): OboeAudioEngine.DecodedAudio? = OboeAudioEngine.decodeOnly(file)?.also {
        decodedFiles[it] = file.name
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
        return plays.size
    }
    @Synchronized override fun stopVoice(stopKey: Int) { stoppedVoices += stopKey }
    @Synchronized override fun stopAllVoices() {
        silencedThrough = plays.size
        silences.incrementAndGet()
    }

    /** Clears request history after the previous play screen has closed. */
    @Synchronized fun resetPlaybackRequests() {
        plays.clear()
        loops.clear()
        stoppedVoices.clear()
        silencedThrough = 0
    }

    /** Sound IDs of the infinite loops (`loop == -1`) started and not stopped since. */
    @Synchronized fun ringingLoops(): List<Int> = loops.indices
        .filter { loops[it] == -1 && it + 1 > silencedThrough && it + 1 !in stoppedVoices }
        .map { plays[it] }
}
