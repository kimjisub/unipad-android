package com.kimjisub.launchpad.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.nio.ByteOrder

/**
 * Low-latency audio engine backed by Oboe (C++ NDK).
 * Decodes audio files using Android MediaCodec on the Kotlin side,
 * then passes raw PCM to the native engine for playback.
 */
object OboeAudioEngine {

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
		val decoded = decodeAudioFile(file) ?: return -1
		return nativeLoadSound(decoded.pcm, decoded.numFrames, decoded.channels, decoded.sampleRate)
	}

	/**
	 * Decode audio file to raw PCM without loading into native engine.
	 * Thread-safe - can be called from multiple threads concurrently.
	 */
	fun decodeOnly(file: File): DecodedAudio? = decodeAudioFile(file)

	/**
	 * Load pre-decoded PCM data into native engine.
	 * Must be called from a single thread (native SoundBank uses mutex but JNI calls should be serialized).
	 */
	fun loadDecoded(decoded: DecodedAudio): Int {
		return nativeLoadSound(decoded.pcm, decoded.numFrames, decoded.channels, decoded.sampleRate)
	}

	fun unloadSound(soundId: Int) = nativeUnloadSound(soundId)
	fun unloadAll() = nativeUnloadAll()

	fun play(soundId: Int, volumeL: Float = 1f, volumeR: Float = 1f, loop: Int = 0): Int {
		return nativePlay(soundId, volumeL, volumeR, loop)
	}

	fun stopVoice(stopKey: Int) = nativeStopVoice(stopKey)

	// ---- Native methods ----
	private external fun nativeStart(): Boolean
	private external fun nativeStop()
	private external fun nativeLoadSound(pcmData: ShortArray, numFrames: Int, channels: Int, sampleRate: Int): Int
	private external fun nativeUnloadSound(soundId: Int)
	private external fun nativeUnloadAll()
	private external fun nativePlay(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int
	private external fun nativeStopVoice(stopKey: Int)

	// ---- Audio decoding ----

	data class DecodedAudio(
		val pcm: ShortArray,
		val numFrames: Int,
		val channels: Int,
		val sampleRate: Int,
	)

	private fun decodeAudioFile(file: File): DecodedAudio? {
		val extractor = MediaExtractor()
		try {
			extractor.setDataSource(file.absolutePath)

			var trackIndex = -1
			for (i in 0 until extractor.trackCount) {
				val format = extractor.getTrackFormat(i)
				val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
				if (mime.startsWith("audio/")) {
					trackIndex = i
					break
				}
			}
			if (trackIndex < 0) return null

			extractor.selectTrack(trackIndex)
			val format = extractor.getTrackFormat(trackIndex)
			val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
			val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
			val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

			// Pre-allocate based on duration estimate
			val durationUs = format.getLongOrDefault(MediaFormat.KEY_DURATION, 0L)
			val estimatedSamples = if (durationUs > 0) {
				((durationUs * sampleRate * channels) / 1_000_000L).toInt()
			} else {
				sampleRate * channels * 2 // fallback: 2 seconds
			}

			val codec = MediaCodec.createDecoderByType(mime)
			codec.configure(format, null, null, 0)
			codec.start()

			// Single growing array instead of chunk list
			var pcm = ShortArray(estimatedSamples)
			var totalSamples = 0
			val bufferInfo = MediaCodec.BufferInfo()
			var inputDone = false
			var outputDone = false
			val timeoutUs = 5_000L

			while (!outputDone) {
				if (!inputDone) {
					val inputIndex = codec.dequeueInputBuffer(timeoutUs)
					if (inputIndex >= 0) {
						val inputBuffer = codec.getInputBuffer(inputIndex)!!
						val bytesRead = extractor.readSampleData(inputBuffer, 0)
						if (bytesRead < 0) {
							codec.queueInputBuffer(inputIndex, 0, 0, 0,
								MediaCodec.BUFFER_FLAG_END_OF_STREAM)
							inputDone = true
						} else {
							codec.queueInputBuffer(inputIndex, 0, bytesRead,
								extractor.sampleTime, 0)
							extractor.advance()
						}
					}
				}

				val outputIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
				if (outputIndex >= 0) {
					if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
						outputDone = true
					}

					val outputBuffer = codec.getOutputBuffer(outputIndex)
					if (outputBuffer != null && bufferInfo.size > 0) {
						outputBuffer.order(ByteOrder.nativeOrder())
						val shortBuf = outputBuffer.asShortBuffer()
						val numShorts = bufferInfo.size / 2

						// Grow array if needed
						if (totalSamples + numShorts > pcm.size) {
							pcm = pcm.copyOf(maxOf(pcm.size * 2, totalSamples + numShorts))
						}
						shortBuf.get(pcm, totalSamples, numShorts)
						totalSamples += numShorts
					}

					codec.releaseOutputBuffer(outputIndex, false)
				}
			}

			codec.stop()
			codec.release()

			if (totalSamples == 0) return null

			// Trim to exact size
			val finalPcm = if (pcm.size == totalSamples) pcm else pcm.copyOf(totalSamples)
			val numFrames = totalSamples / channels
			return DecodedAudio(finalPcm, numFrames, channels, sampleRate)
		} catch (e: Exception) {
			return null
		} finally {
			extractor.release()
		}
	}

	private fun MediaFormat.getLongOrDefault(key: String, default: Long): Long {
		return try { getLong(key) } catch (_: Exception) { default }
	}
}
