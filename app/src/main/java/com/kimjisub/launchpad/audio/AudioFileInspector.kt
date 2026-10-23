package com.kimjisub.launchpad.audio

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Reads physical audio structure, never a player's advertised duration or a file extension. */
object AudioFileInspector {
    fun inspect(file: File): AudioInspection = try {
        AudioData(file).use { input ->
            val facts = when {
                input.matches(0, "RIFF") -> inspectWave(input)
                input.matches(0, "OggS") -> inspectOgg(input)
                input.matches(0, "fLaC") -> inspectFlac(input)
                input.matches(4, "ftyp") || input.matches(4, "moov") || input.matches(4, "mdat") -> inspectMp4(input)
                else -> try { inspectMpegOrAdts(input) } catch (_: InvalidAudio) { inspectMp4(input) }
            }
            AudioInspection.Readable(facts)
        }
    } catch (_: InvalidAudio) {
        AudioInspection.Rejected(AudioExclusion.INVALID_FORMAT)
    } catch (_: IOException) {
        AudioInspection.Rejected(AudioExclusion.READ_FAILURE)
    } catch (_: SecurityException) {
        AudioInspection.Rejected(AudioExclusion.READ_FAILURE)
    }
}

/** Fixed-size cache keeps scanning compressed frames independent of the file's claimed length. */
internal class AudioData(file: File) : Closeable {
    private val source = RandomAccessFile(file, "r")
    val size = source.length()
    private val buffer = ByteArray(65_536)
    private var start = -1L
    private var count = 0
    fun range(at: Long, length: Long, end: Long = size) {
        audioRequire(at >= 0 && length >= 0 && end <= size && at <= end && length <= end - at)
    }
    fun byte(at: Long): Int {
        range(at, 1)
        if (at < start || at - start >= count) {
            source.seek(at); count = source.read(buffer); start = at
            audioRequire(count > 0)
        }
        return buffer[(at - start).toInt()].toInt() and 255
    }
    fun uint(at: Long, bytes: Int, little: Boolean = false): Long {
        range(at, bytes.toLong())
        var value = 0L
        for (i in 0 until bytes) {
            val next = byte(at + if (little) bytes - 1 - i else i)
            audioRequire(value <= (Long.MAX_VALUE - next) / 256)
            value = value * 256 + next
        }
        return value
    }
    fun matches(at: Long, text: String): Boolean = at >= 0 && at <= size && text.length <= size - at &&
        text.indices.all { byte(at + it) == text[it].code }
    override fun close() = source.close()
}

private fun inspectWave(d: AudioData): AudioFileFacts {
    audioRequire(d.matches(8, "WAVE"))
    val end = audioAdd(d.uint(4, 4, true), 8)
    d.range(0, end)
    // Bytes outside RIFF are not audio. Every chunk inside RIFF must actually exist.
    var at = 12L
    var rate = 0
    var channels = 0
    var align = 0
    var dataBytes = 0L
    var foundData = false
    var foundFormat = false
    while (at < end) {
        d.range(at, 8, end)
        val length = d.uint(at + 4, 4, true)
        val payload = at + 8
        d.range(payload, length, end)
        if (d.matches(at, "fmt ")) {
            audioRequire(!foundFormat && length >= 16)
            foundFormat = true
            var encoding = d.uint(payload, 2, true).toInt()
            channels = d.uint(payload + 2, 2, true).toInt()
            rate = d.uint(payload + 4, 4, true).toInt()
            align = d.uint(payload + 12, 2, true).toInt()
            val bits = d.uint(payload + 14, 2, true).toInt()
            if (encoding == 0xfffe) {
                audioRequire(length >= 40 && d.uint(payload + 16, 2, true) >= 22)
                encoding = d.uint(payload + 24, 2, true).toInt()
                // PCM/IEEE float subtype GUID; not an arbitrary compressed extensible format.
                audioRequire(d.uint(payload + 26, 2, true) == 0L && d.uint(payload + 28, 4, true) == 0x00100000L &&
                    d.uint(payload + 32, 4) == 0x800000aaL && d.uint(payload + 36, 4) == 0x00389b71L)
            }
            audioRequire(encoding == 1 || encoding == 3)
            audioRequire(channels > 0 && rate > 0 && bits in 8..64 && bits % 8 == 0)
            audioRequire(encoding != 3 || bits == 32 || bits == 64)
            audioRequire(align == channels * (bits / 8))
        } else if (d.matches(at, "data")) {
            foundData = true
            dataBytes = audioAdd(dataBytes, length)
        }
        val padded = audioAdd(length, length % 2)
        d.range(payload, padded, end)
        at = payload + padded
    }
    audioRequire(foundFormat && foundData && align > 0 && dataBytes % align == 0L)
    return AudioFileFacts(rate, channels, dataBytes / align, "wav")
}

private fun inspectMpegOrAdts(d: AudioData): AudioFileFacts {
    var at = 0L
    while (d.matches(at, "ID3")) {
        d.range(at, 10)
        audioRequire(d.byte(at + 3) in 2..4)
        var length = 0L
        for (i in 6..9) { val b = d.byte(at + i); audioRequire(b < 128); length = length * 128 + b }
        val footer = if (d.byte(at + 3) == 4 && d.byte(at + 5) and 16 != 0) 10 else 0
        val size = audioAdd(length, 10L + footer)
        d.range(at, size); at += size
    }
    if (d.size - at >= 2 && d.byte(at) == 255 && d.byte(at + 1) and 0xf6 == 0xf0) return inspectAdts(d, at)
    var frames = 0L
    var rate = 0
    var channels = 0
    while (at < d.size) {
        if (d.size - at == 128L && d.matches(at, "TAG")) { at += 128; break }
        val h = d.uint(at, 4)
        audioRequire(h ushr 21 == 0x7ffL)
        val version = (h ushr 19 and 3).toInt()
        val layer = (h ushr 17 and 3).toInt()
        val bitrateIndex = (h ushr 12 and 15).toInt()
        val rateIndex = (h ushr 10 and 3).toInt()
        audioRequire(version != 1 && layer != 0 && bitrateIndex in 1..14 && rateIndex < 3)
        val r = intArrayOf(44_100, 48_000, 32_000)[rateIndex] / when (version) { 3 -> 1; 2 -> 2; else -> 4 }
        val c = if (h ushr 6 and 3 == 3L) 1 else 2
        val rates = when {
            version == 3 && layer == 3 -> intArrayOf(32,64,96,128,160,192,224,256,288,320,352,384,416,448)
            version == 3 && layer == 2 -> intArrayOf(32,48,56,64,80,96,112,128,160,192,224,256,320,384)
            version == 3 -> intArrayOf(32,40,48,56,64,80,96,112,128,160,192,224,256,320)
            layer == 3 -> intArrayOf(32,48,56,64,80,96,112,128,144,160,176,192,224,256)
            else -> intArrayOf(8,16,24,32,40,48,56,64,80,96,112,128,144,160)
        }
        val bitrate = rates[bitrateIndex - 1]
        val padding = (h ushr 9 and 1).toInt()
        val samples = when { layer == 3 -> 384; layer == 1 && version != 3 -> 576; else -> 1152 }
        val bytes = if (layer == 3) (12_000 * bitrate / r + padding) * 4
            else (if (samples == 576) 72_000 else 144_000) * bitrate / r + padding
        d.range(at, bytes.toLong())
        audioRequire(bytes >= 4 && (rate == 0 || rate == r && channels == c))
        rate = r; channels = c; frames = audioAdd(frames, samples.toLong()); at += bytes
    }
    audioRequire(frames > 0 && at == d.size)
    return AudioFileFacts(rate, channels, frames, "mp3")
}

private fun inspectAdts(d: AudioData, start: Long): AudioFileFacts {
    var at = start
    var frames = 0L
    var rate = 0
    var channels = 0
    val rates = intArrayOf(96_000,88_200,64_000,48_000,44_100,32_000,24_000,22_050,16_000,12_000,11_025,8_000,7_350)
    while (at < d.size) {
        d.range(at, 7)
        audioRequire(d.byte(at) == 255 && d.byte(at + 1) and 0xf6 == 0xf0)
        val index = d.byte(at + 2) ushr 2 and 15
        audioRequire(index < rates.size)
        val r = rates[index]
        val configuration = ((d.byte(at + 2) and 1) shl 2) or (d.byte(at + 3) ushr 6)
        val c = if (configuration == 7) 8 else configuration
        audioRequire(c > 0 && (rate == 0 || rate == r && channels == c))
        val length = ((d.byte(at + 3) and 3) shl 11) or (d.byte(at + 4) shl 3) or (d.byte(at + 5) ushr 5)
        audioRequire(length >= if (d.byte(at + 1) and 1 == 1) 7 else 9)
        d.range(at, length.toLong())
        frames = audioAdd(frames, 1024L * ((d.byte(at + 6) and 3) + 1))
        rate = r; channels = c; at += length
    }
    audioRequire(frames > 0)
    return AudioFileFacts(rate, channels, frames, "aac")
}
