package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.audio.OboeAudioEngine
import java.io.File
import java.util.IdentityHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Stands in for the native engine and records what the runner asks of it; nothing is decoded or heard.
 * A voice's stop key is its 1-based position in [plays]. Files named in [undecodable] fail to decode,
 * and while [decodeGate] is closed every decode waits for it.
 */
internal class RecordingSoundEngine(
	private val undecodable: Set<String> = emptySet(),
	private val decodeGate: CountDownLatch? = null,
) : SoundRunner.Engine {
	private val decoded = IdentityHashMap<OboeAudioEngine.DecodedAudio, String>()
	private val names = HashMap<Int, String>()
	val plays = CopyOnWriteArrayList<Pair<String, Int>>()
	val stops = CopyOnWriteArrayList<Int>()

	override fun start() = true
	override fun stop() {}
	override fun decode(file: File): OboeAudioEngine.DecodedAudio? {
		decodeGate?.await(10, TimeUnit.SECONDS)
		if (file.name in undecodable) return null
		return OboeAudioEngine.DecodedAudio(ShortArray(1), 1, 1, 44_100).also { synchronized(this) { decoded[it] = file.name } }
	}
	override fun load(decoded: OboeAudioEngine.DecodedAudio): Int = synchronized(this) {
		names.size.also { names[it] = this.decoded.getValue(decoded) }
	}
	override fun unloadSound(soundId: Int) {}
	override fun unloadAll() {}
	@Synchronized override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int {
		plays += names.getValue(soundId) to loop
		return plays.size
	}
	override fun stopVoice(stopKey: Int) {
		stops += stopKey
	}
	override fun stopAllVoices() {}
}
