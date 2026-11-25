package com.kimjisub.launchpad.audio

import android.media.MediaCodec
import android.media.MediaFormat
import com.kimjisub.launchpad.audio.OboeAudioEngine.DecodeInterruptedException
import com.kimjisub.launchpad.audio.OboeAudioEngine.DecodedAudio
import com.kimjisub.launchpad.tool.Log
import java.io.File
import java.nio.ByteOrder

/**
 * Decodes one audio file to 16-bit PCM with a platform decoder.
 *
 * The media service sometimes breaks a decoder it has just handed out (an UNKNOWN_ERROR that
 * releases it before `configure`) or loses a buffer between the app and the decoder in the middle
 * of a file. Both surface as [IllegalStateException] (including [MediaCodec.CodecException]) from a
 * decoder call, and the same file decodes on a fresh decoder. Only those failures start the file
 * over, up to [MAX_ATTEMPTS] in all; a file the extractor cannot read, a track without a type, a
 * decode that stalls after its last packet or one with no frames fails at once, as before.
 *
 * Holds no state, so one instance serves every loading thread.
 */
internal class AudioFileDecoder(
	private val openSource: (File) -> AudioPacketSource? = ::openAudioPacketSource,
	private val createCodec: (String) -> MediaCodec = MediaCodec::createDecoderByType,
	private val pause: (Long) -> Unit = Thread::sleep,
) {

	/**
	 * Returns the decoded file, or null when it cannot be decoded. [interrupted] is polled between
	 * decoder steps and before each new attempt; once it answers true the decoder is released and
	 * [DecodeInterruptedException] is thrown. [onAllocation] sees the PCM buffer growth of the
	 * attempt that produced the result only, so an abandoned attempt is never counted twice.
	 */
	fun decode(file: File, interrupted: () -> Boolean, onAllocation: ((Int, Int) -> Unit)? = null): DecodedAudio? {
		var attempt = 1
		while (true) {
			val allocations = onAllocation?.let { mutableListOf<Pair<Int, Int>>() }
			try {
				val decoded = decodeOnce(file, interrupted, allocations?.let { list -> { old: Int, new: Int -> list += old to new } })
				allocations?.forEach { (old, new) -> onAllocation?.invoke(old, new) }
				if (decoded != null && attempt > 1) Log.err("decode recovered: ${file.name} on attempt $attempt of $MAX_ATTEMPTS")
				return decoded
			} catch (e: DecoderFailure) {
				if (attempt == MAX_ATTEMPTS) {
					Log.err("decode failed: ${file.name} (decoder ${e.stage.label} failed on attempt $attempt of $MAX_ATTEMPTS)", e.cause)
					return null
				}
				Log.err("decoder ${e.stage.label} failed on attempt $attempt of $MAX_ATTEMPTS, retrying: ${file.name}: ${e.cause}")
				if (interrupted()) throw DecodeInterruptedException()
				pause(RETRY_PAUSE_MS * attempt)
				attempt++
			} catch (e: DecodeInterruptedException) {
				throw e
			} catch (e: Exception) {
				Log.err("decode failed: ${file.name}", e)
				return null
			}
		}
	}

	private fun decodeOnce(file: File, interrupted: () -> Boolean, onAllocation: ((Int, Int) -> Unit)?): DecodedAudio? {
		// Opening the file is already a call into the media service.
		if (interrupted()) throw DecodeInterruptedException()
		val decodeInspection = AudioFileInspector.inspectForDecode(file)
		if (interrupted()) throw DecodeInterruptedException()
		return (openSource(file) ?: return null).use { source ->
			val format = source.format
			val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
			val codecConfig = format.getByteBuffer("csd-0")?.duplicate()
			var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
			var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
			var channelMask = if (format.containsKey(MediaFormat.KEY_CHANNEL_MASK)) format.getInteger(MediaFormat.KEY_CHANNEL_MASK) else 0
			// Ask for 16-bit PCM explicitly; FLAC/WAV tracks can otherwise decode to float, which
			// asShortBuffer() would read as noise.
			format.setInteger(MediaFormat.KEY_PCM_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)

			if (interrupted()) throw DecodeInterruptedException()
			val codec = during(DecoderStage.SETUP) { createCodec(mime) }
			try {
				val decoderName = during(DecoderStage.SETUP) {
					codec.configure(format, null, null, 0)
					codec.start()
					codec.name
				}

				// Single growing array instead of chunk list
				var pcmBuffer: PcmBuffer? = null
				val bufferInfo = MediaCodec.BufferInfo()
				var inputDone = false
				var outputDone = false
				// Some OEM decoders answer TRY_AGAIN_LATER forever on a truncated file; without a
				// budget the loading dialog never closes.
				var idleRounds = 0

				while (!outputDone) {
					if (interrupted()) throw DecodeInterruptedException()
					if (!inputDone) {
						val inputIndex = during(DecoderStage.IO) { codec.dequeueInputBuffer(TIMEOUT_US) }
						if (inputIndex >= 0) {
							val inputBuffer = during(DecoderStage.IO) { codec.getInputBuffer(inputIndex) }!!
							val bytesRead = source.readPacket(inputBuffer)
							if (bytesRead < 0) {
								during(DecoderStage.IO) {
									codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
								}
								inputDone = true
							} else {
								during(DecoderStage.IO) { codec.queueInputBuffer(inputIndex, 0, bytesRead, source.packetTimeUs, 0) }
								source.advance()
							}
						}
					}

					val outputIndex = during(DecoderStage.IO) { codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US) }
					if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
						// HE-AAC (SBR) decodes at twice the container rate; keep the real output layout.
						val out = during(DecoderStage.IO) { codec.outputFormat }
						if (out.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
						if (out.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
						channelMask = if (out.containsKey(MediaFormat.KEY_CHANNEL_MASK)) out.getInteger(MediaFormat.KEY_CHANNEL_MASK) else 0
					} else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
						if (inputDone && ++idleRounds > MAX_IDLE_ROUNDS) {
							Log.err("decode stalled after EOS: ${file.name}")
							return null
						}
					}
					if (outputIndex >= 0) {
						idleRounds = 0
						if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
							outputDone = true
						}

						val outputBuffer = during(DecoderStage.IO) { codec.getOutputBuffer(outputIndex) }
						if (outputBuffer != null && bufferInfo.size > 0) {
							outputBuffer.order(ByteOrder.nativeOrder())
							outputBuffer.position(bufferInfo.offset)
							outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
							val shortBuf = outputBuffer.asShortBuffer()
							// Wait for real PCM and the decoder's output layout before allocating.
							val buffer = pcmBuffer ?: PcmBuffer(
								initialPcmCapacity(sampleRate, channels, decodeInspection.inspection, decodeInspection.fullPacketFrames), onAllocation,
							).also { pcmBuffer = it }
							buffer.append(shortBuf)
						}

						during(DecoderStage.IO) { codec.releaseOutputBuffer(outputIndex, false) }
					}
				}

				during(DecoderStage.IO) { codec.stop() }

				val buffer = pcmBuffer ?: return null
				val totalSamples = buffer.sampleCount
				val pcm = buffer.pcm ?: return null

				val numFrames = totalSamples / channels
				if (numFrames <= 0) return null
				val speakers = if (decoderEmitsVorbisOrder(mime, decoderName, channels)) {
					vorbisOrderToChannelMask(pcm, channels, totalSamples)
				} else if (decoderEmitsLegacyQuadAacOrder(mime, decoderName, channels, android.os.Build.VERSION.SDK_INT, codecConfig)) {
					legacyQuadAacOrderToChannelMask(pcm, totalSamples)
				} else decoderChannelMask(channels, channelMask)
				DecodedAudio(stereoPcm(pcm, channels, speakers, totalSamples), numFrames, minOf(channels, 2), sampleRate)
			} finally {
				// Released on every path, before any retry asks for a new one; a codec leaked per
				// failed file exhausted the device's codec instances and made every later sound fail.
				codec.release()
			}
		}
	}

	private enum class DecoderStage(val label: String) { SETUP("setup"), IO("input/output") }

	/** A decoder call failed on the decoder's side; the file itself was not shown to be at fault. */
	private class DecoderFailure(val stage: DecoderStage, override val cause: IllegalStateException) : Exception(cause)

	private inline fun <T> during(stage: DecoderStage, call: () -> T): T = try {
		call()
	} catch (e: IllegalStateException) {
		throw DecoderFailure(stage, e)
	}

	companion object {
		const val MAX_ATTEMPTS = 3

		/**
		 * Wait before the second attempt; the third waits twice as long. The service tears the
		 * failed decoder and its buffer pool down asynchronously, and asking again at once can land
		 * on the same pool. A few hundred milliseconds at most per affected file keeps pack opening
		 * time unchanged for everyone else.
		 */
		const val RETRY_PAUSE_MS = 100L

		private const val TIMEOUT_US = 5_000L
		private const val MAX_IDLE_ROUNDS = 2_000 // 2000 x 5 ms = 10 s of nothing after EOS
	}
}
