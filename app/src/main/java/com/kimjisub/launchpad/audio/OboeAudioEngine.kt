package com.kimjisub.launchpad.audio

import android.media.MediaCodec
import androidx.annotation.VisibleForTesting
import com.kimjisub.launchpad.BuildConfig
import java.io.File

/**
 * Low-latency audio engine backed by Oboe (C++ NDK).
 * Decodes audio files using Android MediaCodec on the Kotlin side,
 * then passes raw PCM to the native engine for playback.
 */
object OboeAudioEngine {

	private const val LOAD_MEMORY_FAILURE = -2

	init {
		System.loadLibrary("unipad_audio")
	}

	fun start(): Boolean = nativeStart()
	fun stop() = nativeStop()

	/**
	 * Decode an audio file and load PCM into native engine.
	 * Returns soundId or -1 on failure.
	 */
	fun loadSound(file: File): Int {
		val decoded = decodeOnly(file) ?: return -1
		return loadDecoded(decoded)
	}

	/**
	 * Decode audio file to raw PCM without loading into native engine.
	 * Thread-safe - can be called from multiple threads concurrently.
	 * [interrupted] is polled between decoder steps; once it answers true the decoder is released
	 * and [DecodeInterruptedException] is thrown, so the partial PCM is discarded.
	 */
	fun decodeOnly(file: File, interrupted: () -> Boolean = { false }): DecodedAudio? = fileDecoder.decode(file, interrupted)

	/**
	 * Load pre-decoded PCM data into native engine.
	 * Must be called from a single thread (native SoundBank uses mutex but JNI calls should be serialized).
	 */
	fun loadDecoded(decoded: DecodedAudio): Int {
		return nativeLoadSound(decoded.pcm, decoded.numFrames, decoded.channels, decoded.sampleRate).also {
			if (it == LOAD_MEMORY_FAILURE) throw OutOfMemoryError("Native sound allocation failed")
		}
	}

	fun unloadSound(soundId: Int) = nativeUnloadSound(soundId)
	fun unloadAll() = nativeUnloadAll()

	fun play(soundId: Int, volumeL: Float = 1f, volumeR: Float = 1f, loop: Int = 0): Int {
		return nativePlay(soundId, volumeL, volumeR, loop)
	}

	fun stopVoice(stopKey: Int) = nativeStopVoice(stopKey)
	fun stopAllVoices() = nativeStopAllVoices()

	// ---- Native methods ----
	private external fun nativeStart(): Boolean
	private external fun nativeStop()
	private external fun nativeLoadSound(pcmData: ShortArray, numFrames: Int, channels: Int, sampleRate: Int): Int
	private external fun nativeUnloadSound(soundId: Int)
	private external fun nativeUnloadAll()
	private external fun nativePlay(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int
	private external fun nativeStopVoice(stopKey: Int)
	private external fun nativeStopAllVoices()

	// ---- Audio decoding ----

	data class DecodedAudio(
		val pcm: ShortArray,
		val numFrames: Int,
		val channels: Int,
		val sampleRate: Int,
	)

	/** A decode given up because its caller asked it to stop; the file itself may be fine. */
	class DecodeInterruptedException : Exception("Decode interrupted")

	internal fun decodeObservingPcm(file: File, onAllocation: (Int, Int) -> Unit): DecodedAudio? =
		fileDecoder.decode(file, { false }, onAllocation)

	@Volatile
	private var fileDecoder = AudioFileDecoder()

	/**
	 * Debug builds only: hands every later decode a decoder made by [createCodec] (null restores the
	 * platform's), so device tests can reproduce a decoder the media service breaks.
	 */
	@VisibleForTesting
	internal fun createDecodersWith(createCodec: ((String) -> MediaCodec)?) {
		check(BuildConfig.DEBUG) { "Decoder replacement is for debug builds" }
		fileDecoder = if (createCodec == null) AudioFileDecoder() else AudioFileDecoder(createCodec = createCodec)
	}

}
