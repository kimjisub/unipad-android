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
    val loaded = ConcurrentHashMap<String, Int>()
    val silences = AtomicInteger()
    val stops = AtomicInteger()
    val starts = AtomicInteger()
    private val nextId = AtomicInteger()
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
    override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int {
        plays += soundId
        return plays.size
    }
    override fun stopVoice(stopKey: Int) = Unit
    override fun stopAllVoices() { silences.incrementAndGet() }
}
