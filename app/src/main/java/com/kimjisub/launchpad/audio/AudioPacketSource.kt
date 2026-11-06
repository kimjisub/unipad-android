package com.kimjisub.launchpad.audio

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Compressed packets of one audio track in decoding order, and the format a decoder is configured with. */
internal interface AudioPacketSource : Closeable {
    val format: MediaFormat

    /** Copies the current packet to the start of [buffer]; returns its size, or -1 after the last packet. */
    fun readPacket(buffer: ByteBuffer): Int

    /** Presentation time of the current packet. */
    val packetTimeUs: Long

    fun advance()
}

/**
 * Opens the file's first audio track with the platform extractor. When that extractor finds no
 * audio track and the file is Opus in MP4 (Android 9 and earlier type it application/octet-stream),
 * the packets are read from the MP4 sample tables instead.
 */
internal fun openAudioPacketSource(file: File): AudioPacketSource? {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(file.absolutePath)
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                extractor.selectTrack(i)
                return ExtractorPacketSource(extractor, format)
            }
        }
    } catch (e: Exception) {
        extractor.release()
        throw e
    }
    extractor.release()
    return Mp4OpusPacketSource.open(file)
}

private class ExtractorPacketSource(
    private val extractor: MediaExtractor,
    override val format: MediaFormat,
) : AudioPacketSource {
    override fun readPacket(buffer: ByteBuffer): Int = extractor.readSampleData(buffer, 0)
    override val packetTimeUs: Long get() = extractor.sampleTime
    override fun advance() {
        extractor.advance()
    }
    override fun close() = extractor.release()
}

internal class Mp4OpusPacketSource private constructor(
    private val file: RandomAccessFile,
    private val track: Mp4OpusTrack,
) : AudioPacketSource {
    private var index = 0
    private var framesBefore = 0L
    private val packet = ByteArray(track.sizes.maxOrNull() ?: 0)

    /** Configured as Android's own MP4 and Ogg extractors configure the Opus decoder. */
    override val format: MediaFormat = MediaFormat.createAudioFormat(
        MediaFormat.MIMETYPE_AUDIO_OPUS, OPUS_RATE, track.channels,
    ).apply {
        setByteBuffer("csd-0", ByteBuffer.wrap(track.head))
        setByteBuffer("csd-1", nanoseconds(track.preSkip * 1_000_000_000L / OPUS_RATE))
        setByteBuffer("csd-2", nanoseconds(SEEK_PRE_ROLL_NS))
        setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, packet.size)
    }

    override fun readPacket(buffer: ByteBuffer): Int {
        if (index >= track.sizes.size) return -1
        val size = track.sizes[index]
        check(size <= buffer.capacity()) { "Opus packet of $size bytes exceeds the decoder input buffer" }
        file.seek(track.offsets[index])
        file.readFully(packet, 0, size)
        buffer.clear()
        buffer.put(packet, 0, size)
        return size
    }

    override val packetTimeUs: Long get() = framesBefore * 1_000_000L / OPUS_RATE

    override fun advance() {
        framesBefore += track.frames[index]
        index++
    }

    override fun close() = file.close()

    companion object {
        private const val OPUS_RATE = 48_000

        /** RFC 7845 section 4.6 recommends decoding 80 ms before a seek target. */
        private const val SEEK_PRE_ROLL_NS = 80_000_000L

        private fun nanoseconds(value: Long): ByteBuffer =
            ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.nativeOrder()).putLong(value).apply { flip() }

        /** Null when the file is not an MP4 whose first sound track is Opus. */
        fun open(file: File): Mp4OpusPacketSource? {
            val track = try {
                AudioData(file).use(::mp4OpusTrack)
            } catch (_: InvalidAudio) {
                return null
            }
            return Mp4OpusPacketSource(RandomAccessFile(file, "r"), track)
        }
    }
}
