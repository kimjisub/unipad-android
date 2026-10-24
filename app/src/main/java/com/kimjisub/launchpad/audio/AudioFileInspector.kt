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
    } catch (_: AudioInspectionLimit) {
        AudioInspection.Rejected(AudioExclusion.READ_FAILURE)
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
    // RIFF sizes in existing encoder output can be stale. Chunk payloads are bounded by EOF.
    val end = d.size
    var at = 12L
    var rate = 0
    var channels = 0
    var align = 0
    var encoding = 0
    var compressedFrames = 0L
    var dataBytes = 0L
    var foundData = false
    var foundFormat = false
    while (at < end) {
        if (end - at < 8 && foundFormat && foundData) break
        d.range(at, 8, end)
        val length = d.uint(at + 4, 4, true)
        val payload = at + 8
        val available = minOf(length, end - payload)
        // Ignore an incomplete trailing non-audio chunk once the sound is accounted for.
        if (available < length && !d.matches(at, "data") && foundFormat && foundData) break
        if (!d.matches(at, "data")) d.range(payload, length, end)
        if (d.matches(at, "fmt ")) {
            audioRequire(!foundFormat && length >= 16)
            foundFormat = true
            encoding = d.uint(payload, 2, true).toInt()
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
            audioRequire(encoding == 1 || encoding == 3 || encoding == 85)
            audioRequire(channels > 0 && rate > 0 && align > 0)
            if (encoding != 85) {
                audioRequire(bits in 8..64 && bits % 8 == 0)
                audioRequire(encoding != 3 || bits == 32 || bits == 64)
                audioRequire(align == channels * (bits / 8))
            }
        } else if (d.matches(at, "data")) {
            foundData = true
            audioRequire(align > 0)
            if (encoding == 85) {
                // WAVE_FORMAT_MPEGLAYER3 stores variable-size compressed frames, not PCM blocks.
                val facts = inspectMpegOrAdts(d, payload, payload + available)
                audioRequire(facts.format == "mp3" && facts.sampleRate == rate && facts.channels == channels)
                compressedFrames = audioAdd(compressedFrames, facts.frames)
            } else dataBytes = audioAdd(dataBytes, available - available % align)
        }
        val padded = audioAdd(length, length % 2)
        if (padded > end - payload) break // Truncated audio or a missing final pad byte.
        at = payload + padded
    }
    audioRequire(foundFormat && foundData && align > 0 && dataBytes % align == 0L)
    return AudioFileFacts(rate, channels, if (encoding == 85) compressedFrames else dataBytes / align, "wav")
}

/** Excludes physically validated trailing tags, not just a footer's advertised byte count. */
private fun mpegAudioEnd(d: AudioData, start: Long, end: Long): Long {
    var tail = end
    while (true) {
        if (tail - start >= 128 && d.matches(tail - 128, "TAG")) { tail -= 128; continue }
        if (tail - start < 32 || !d.matches(tail - 32, "APETAGEX")) return tail
        val before = tail
        tail = try {
            val footer = tail - 32
            val version = d.uint(footer + 8, 4, true)
            val length = d.uint(footer + 12, 4, true)
            val count = d.uint(footer + 16, 4, true)
            val flags = d.uint(footer + 20, 4, true)
            audioRequire(version == 1000L || version == 2000L)
            audioRequire(length >= 32 && length <= tail - start && flags and 0x20000000L == 0L)
            audioRequire(d.uint(footer + 24, 8) == 0L)
            val body = tail - length
            var first = body
            if (flags and 0x80000000L != 0L) {
                audioRequire(body - start >= 32)
                first -= 32
                audioRequire(d.matches(first, "APETAGEX") && d.uint(first + 8, 4, true) == version &&
                    d.uint(first + 12, 4, true) == length && d.uint(first + 16, 4, true) == count &&
                    d.uint(first + 20, 4, true) and 0x20000000L != 0L)
            }
            // Each item has eight fixed bytes, at least one key byte and a terminator.
            audioRequire(count <= (footer - body) / 10)
            var at = body
            repeat(count.toInt()) {
                d.range(at, 8, footer)
                val size = d.uint(at, 4, true)
                val itemFlags = d.uint(at + 4, 4, true)
                audioRequire(itemFlags <= 5) // Read-only bit plus text, binary or external value type.
                at += 8
                var keyLength = 0
                while (true) {
                    d.range(at, 1, footer)
                    val value = d.byte(at++)
                    if (value == 0) break
                    audioRequire(value in 32..126 && value != 61 && ++keyLength <= 255)
                }
                audioRequire(keyLength > 0)
                d.range(at, size, footer); at += size
            }
            audioRequire(at == footer)
            first
        } catch (_: InvalidAudio) { before }
        if (tail == before) return tail
    }
}

private data class MpegFrame(val rate: Int, val channels: Int, val samples: Int,
    val bytes: Int, val version: Int, val layer: Int) {
    fun sameStream(other: MpegFrame) = rate == other.rate && channels == other.channels &&
        version == other.version && layer == other.layer
}

private fun mpegFrame(d: AudioData, at: Long, end: Long): MpegFrame? {
    if (at < 0 || at > end || end - at < 4) return null
    val h = d.uint(at, 4)
    if (h ushr 21 != 0x7ffL) return null
    val version = (h ushr 19 and 3).toInt()
    val layer = (h ushr 17 and 3).toInt()
    val bitrateIndex = (h ushr 12 and 15).toInt()
    val rateIndex = (h ushr 10 and 3).toInt()
    if (version == 1 || layer == 0 || bitrateIndex !in 1..14 || rateIndex >= 3) return null
    val rate = intArrayOf(44_100, 48_000, 32_000)[rateIndex] / when (version) { 3 -> 1; 2 -> 2; else -> 4 }
    val channels = if (h ushr 6 and 3 == 3L) 1 else 2
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
    val bytes = if (layer == 3) (12_000 * bitrate / rate + padding) * 4
        else (if (samples == 576) 72_000 else 144_000) * bitrate / rate + padding
    return MpegFrame(rate, channels, samples, bytes, version, layer)
}

private fun inspectMpegOrAdts(d: AudioData, start: Long = 0, payloadEnd: Long = d.size): AudioFileFacts {
    val end = mpegAudioEnd(d, start, payloadEnd)
    var at = start
    // Padding and tags may be interleaved before the first frame.
    while (at < end) {
        if (d.byte(at) == 0) { at++; continue }
        if (end - at < 3 || !d.matches(at, "ID3")) break
        d.range(at, 10, end)
        audioRequire(d.byte(at + 3) in 2..4)
        var length = 0L
        for (i in 6..9) { val b = d.byte(at + i); audioRequire(b < 128); length = length * 128 + b }
        val footer = if (d.byte(at + 3) == 4 && d.byte(at + 5) and 16 != 0) 10 else 0
        val size = audioAdd(length, 10L + footer)
        d.range(at, size, end); at += size
    }
    if (end - at >= 2 && d.byte(at) == 255 && d.byte(at + 1) and 0xf6 == 0xf0) return inspectAdts(d, at, end)
    val physicalBytes = end - start
    var probes = minOf(1_048_576L, maxOf(4096L, if (physicalBytes > Long.MAX_VALUE / 4) Long.MAX_VALUE else physicalBytes * 4))
    fun frameAt(position: Long): MpegFrame? {
        if (probes-- <= 0) throw AudioInspectionLimit()
        return mpegFrame(d, position, end)
    }
    val first = frameAt(at) ?: throw InvalidAudio()
    audioRequire(first.bytes <= end - at)
    // Encoder debris can resemble a header with the wrong rate/channels. Establish the
    // layout from a consecutive pair (or one frame ending at EOF), not that first candidate.
    var anchorAt = at
    var anchor: MpegFrame? = null
    while (anchorAt < end) {
        val candidate = frameAt(anchorAt)
        if (candidate != null && candidate.bytes <= end - anchorAt) {
            val nextAt = anchorAt + candidate.bytes
            val next = frameAt(nextAt)
            if (nextAt == end || next != null && candidate.sameStream(next)) { anchor = candidate; break }
        }
        anchorAt++
    }
    if (anchor == null) { anchor = first; anchorAt = at } // One complete frame followed by non-audio bytes.
    val layout = anchor
    var frames = 0L
    var stream: MpegFrame? = layout
    while (at < end) {
        val frame = frameAt(at)
        if (at < anchorAt) {
            // Once a matching prefix frame has been counted, an incompatible header
            // enclosing the confirmed anchor is ambiguous rather than harmless padding.
            if (frame != null && frames > 0 && !layout.sameStream(frame) &&
                frame.bytes <= end - at && frame.bytes > anchorAt - at) throw InvalidAudio()
            // Preserve complete, matching isolated frames before a gap; skip misleading
            // prefix headers without letting their claimed length swallow the anchor.
            if (frame != null && layout.sameStream(frame) && frame.bytes <= anchorAt - at) {
                frames = audioAdd(frames, frame.samples.toLong()); at += frame.bytes
            } else at++
            continue
        }
        if (frame == null || frame.bytes > end - at) {
            audioRequire(stream != null); at++; continue
        }
        val nextAt = at + frame.bytes
        val next = frameAt(nextAt)
        val continuous = nextAt == end || next != null && frame.sameStream(next)
        if (!continuous) {
            // A forged length must not jump over a consistent sequence inside its payload.
            // Reject ambiguous framing instead of choosing the cheaper interpretation.
            // All interior and resynchronization probes share the bounded per-payload budget.
            var inside = at + 4
            while (inside < nextAt) {
                val candidate = frameAt(inside)
                if (candidate != null && (stream == null || stream.sameStream(candidate)) && candidate.bytes <= end - inside) {
                    val followingAt = inside + candidate.bytes
                    val following = frameAt(followingAt)
                    audioRequire(followingAt != end && (following == null || !candidate.sameStream(following)))
                }
                inside++
            }
        }
        val known = stream
        if (known != null && !known.sameStream(frame)) {
            // An isolated header-shaped value is junk; a consistent second stream is unsupported.
            audioRequire(!continuous)
            at++; continue
        }
        frames = audioAdd(frames, frame.samples.toLong()); stream = frame; at = nextAt
    }
    audioRequire(frames > 0)
    val known = stream ?: throw InvalidAudio()
    return AudioFileFacts(known.rate, known.channels, frames, "mp3")
}

private fun inspectAdts(d: AudioData, start: Long, end: Long): AudioFileFacts {
    var at = start
    var frames = 0L
    var rate = 0
    var channels = 0
    val rates = intArrayOf(96_000,88_200,64_000,48_000,44_100,32_000,24_000,22_050,16_000,12_000,11_025,8_000,7_350)
    while (at < end) {
        d.range(at, 7, end)
        audioRequire(d.byte(at) == 255 && d.byte(at + 1) and 0xf6 == 0xf0)
        val index = d.byte(at + 2) ushr 2 and 15
        audioRequire(index < rates.size)
        val r = rates[index]
        val configuration = ((d.byte(at + 2) and 1) shl 2) or (d.byte(at + 3) ushr 6)
        val c = if (configuration == 7) 8 else configuration
        audioRequire(c > 0 && (rate == 0 || rate == r && channels == c))
        val length = ((d.byte(at + 3) and 3) shl 11) or (d.byte(at + 4) shl 3) or (d.byte(at + 5) ushr 5)
        audioRequire(length >= if (d.byte(at + 1) and 1 == 1) 7 else 9)
        d.range(at, length.toLong(), end)
        frames = audioAdd(frames, 1024L * ((d.byte(at + 6) and 3) + 1))
        rate = r; channels = c; at += length
    }
    audioRequire(frames > 0)
    return AudioFileFacts(rate, channels, frames, "aac")
}
