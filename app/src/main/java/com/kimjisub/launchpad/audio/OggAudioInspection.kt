package com.kimjisub.launchpad.audio

private data class PacketSpan(val at: Long, val length: Long, val index: Long)
private class AudioPacket(private val d: AudioData, private val spans: List<PacketSpan>, val size: Long) {
    fun byte(index: Long): Int {
        audioRequire(index in 0 until size)
        var low = 0
        var high = spans.lastIndex
        while (low < high) {
            val middle = (low + high + 1) / 2
            if (spans[middle].index <= index) low = middle else high = middle - 1
        }
        val span = spans[low]
        return d.byte(span.at + index - span.index)
    }
    fun matches(index: Long, value: String): Boolean = index >= 0 && index <= size && value.length <= size - index &&
        value.indices.all { byte(index + it) == value[it].code }
    fun uint(index: Long, count: Int): Long {
        var value = 0L
        for (i in count - 1 downTo 0) value = audioAdd(audioMultiply(value, 256), byte(index + i).toLong())
        return value
    }
    fun bits() = AudioBits(audioMultiply(size, 8), true, ::byte)
}

/** CRC, serial, sequence, lacing and codec packet lengths are checked before trusting a granule. */
internal fun inspectOgg(d: AudioData): AudioFileFacts {
    var at = 0L
    var serial = -1L
    var sequence = 0L
    var packetCount = 0
    var packetSize = 0L
    val spans = mutableListOf<PacketSpan>()
    var rate = 0
    var channels = 0
    var opus = false
    var opusStreams = 1
    var preSkip = 0L
    var shortBlock = 0
    var longBlock = 0
    var modes = booleanArrayOf()
    var previousBlock = 0
    var calculated = 0L
    var lastPacketFrames = 0L
    var granule = 0L
    var granuleOrigin: Long? = null
    var ended = false
    while (at < d.size) {
        audioRequire(!ended && d.matches(at, "OggS"))
        d.range(at, 27)
        audioRequire(d.byte(at + 4) == 0)
        val flags = d.byte(at + 5)
        audioRequire(flags and 0xf8 == 0)
        val stream = d.uint(at + 14, 4, true)
        val pageSequence = d.uint(at + 18, 4, true)
        if (serial == -1L) {
            audioRequire(flags and 2 != 0 && flags and 1 == 0 && pageSequence == 0L)
            serial = stream
        } else audioRequire(flags and 2 == 0)
        audioRequire(stream == serial && pageSequence == sequence && (flags and 1 != 0) == (packetSize > 0))
        sequence = (sequence + 1) and 0xffffffffL
        val segments = d.byte(at + 26)
        d.range(at + 27, segments.toLong())
        var payloadBytes = 0L
        repeat(segments) { payloadBytes += d.byte(at + 27 + it) }
        val pageSize = 27L + segments + payloadBytes
        d.range(at, pageSize)
        var crc = 0
        for (i in 0 until pageSize) {
            val b = if (i in 22L..25L) 0 else d.byte(at + i)
            crc = crc xor (b shl 24)
            repeat(8) { crc = (crc shl 1) xor if (crc < 0) 0x04c11db7 else 0 }
        }
        audioRequire(crc.toLong() and 0xffffffffL == d.uint(at + 22, 4, true))
        var payload = at + 27 + segments
        var completed = 0
        repeat(segments) { index ->
            val size = d.byte(at + 27 + index).toLong()
            if (size > 0) {
                val last = spans.lastOrNull()
                if (last != null && last.at + last.length == payload) spans[spans.lastIndex] = last.copy(length = last.length + size)
                else spans.add(PacketSpan(payload, size, packetSize))
                packetSize = audioAdd(packetSize, size)
            }
            payload += size
            if (size < 255) {
                val packet = AudioPacket(d, spans.toList(), packetSize)
                if (packetCount == 0) {
                    opus = packet.matches(0, "OpusHead")
                    if (opus) {
                        audioRequire(packet.size >= 19 && packet.byte(8) in 1..15)
                        channels = packet.byte(9); rate = 48_000; preSkip = packet.uint(10, 2)
                        val family = packet.byte(18)
                        audioRequire(family != 0 || channels in 1..2)
                        if (family != 0) {
                            audioRequire(packet.size >= 21L + channels)
                            opusStreams = packet.byte(19)
                            val coupled = packet.byte(20)
                            audioRequire(opusStreams > 0 && coupled <= opusStreams && opusStreams + coupled <= 255)
                            repeat(channels) { audioRequire(packet.byte(21L + it) == 255 || packet.byte(21L + it) < opusStreams + coupled) }
                        }
                    } else {
                        audioRequire(packet.size == 30L && packet.byte(0) == 1 && packet.matches(1, "vorbis") && packet.uint(7, 4) == 0L)
                        channels = packet.byte(11); rate = packet.uint(12, 4).toInt()
                        val sizes = packet.byte(28)
                        val small = sizes and 15; val large = sizes ushr 4
                        audioRequire(small in 6..13 && large in small..13 && packet.byte(29) and 1 != 0)
                        shortBlock = 1 shl small; longBlock = 1 shl large
                    }
                    audioRequire(rate > 0 && channels > 0)
                } else if (packetCount == 1) {
                    audioRequire(if (opus) packet.matches(0, "OpusTags") else packet.byte(0) == 3 && packet.matches(1, "vorbis"))
                } else if (!opus && packetCount == 2) {
                    modes = vorbisModes(packet, channels)
                } else {
                    if (opus) {
                        audioRequire(packet.size > 0)
                        lastPacketFrames = opusPacketFrames(packet.size, opusStreams, packet::byte).toLong()
                        calculated = audioAdd(calculated, lastPacketFrames)
                    } else {
                        val bits = packet.bits()
                        audioRequire(bits.read(1) == 0 && modes.isNotEmpty())
                        val mode = bits.read(ilog(modes.size - 1))
                        audioRequire(mode < modes.size)
                        val block = if (modes[mode]) longBlock else shortBlock
                        if (modes[mode]) bits.skip(2)
                        lastPacketFrames = block.toLong()
                        if (previousBlock != 0) calculated = audioAdd(calculated, (previousBlock + block) / 4L)
                        previousBlock = block
                    }
                }
                packetCount++; completed++; spans.clear(); packetSize = 0
            }
        }
        val unset = (0..7).all { d.byte(at + 6 + it) == 255 }
        if (!unset) {
            val raw = d.uint(at + 6, 8, true)
            // Cropped streams may retain a positive source position. Reserve physical blocks,
            // rather than treating that timestamp as additional audio or rejecting the sound.
            if (calculated > 0 && granuleOrigin == null) granuleOrigin = maxOf(raw - calculated, 0)
            val origin = granuleOrigin ?: 0
            audioRequire(raw >= origin)
            val current = raw - origin
            audioRequire(completed > 0 && current >= granule && current <= calculated)
            granule = current
        } else audioRequire(completed == 0)
        if (flags and 4 != 0) {
            audioRequire(!unset && packetSize == 0L && calculated > 0 && granule >= preSkip)
            // Only the final packet may be trimmed, not an arbitrarily shortened timeline.
            audioRequire(calculated - granule <= lastPacketFrames)
            ended = true
        }
        at += pageSize
    }
    audioRequire(ended)
    return AudioFileFacts(rate, channels, granule - preSkip, if (opus) "opus" else "ogg")
}

private fun ilog(value: Int): Int = if (value == 0) 0 else 32 - Integer.numberOfLeadingZeros(value)

/** Walk the setup header to the mode table; packet mode bits determine actual block lengths. */
private fun vorbisModes(packet: AudioPacket, channels: Int): BooleanArray {
    audioRequire(packet.byte(0) == 5 && packet.matches(1, "vorbis"))
    val b = packet.bits(); b.skip(56)
    val codebooks = b.read(8) + 1
    repeat(codebooks) {
        audioRequire(b.read(24) == 0x564342)
        val dimensions = b.read(16); val entries = b.read(24)
        audioRequire(dimensions > 0 && entries > 0)
        if (b.read(1) == 1) {
            var current = 0; var length = b.read(5) + 1
            while (current < entries) {
                audioRequire(length <= 32)
                val count = b.read(ilog(entries - current)); audioRequire(count <= entries - current)
                current += count; length++
            }
        } else {
            val sparse = b.read(1) == 1
            if (sparse) repeat(entries) { if (b.read(1) != 0) b.skip(5) } else b.skip(entries * 5L)
        }
        val lookup = b.read(4); audioRequire(lookup in 0..2)
        if (lookup > 0) {
            b.skip(64)
            val bits = b.read(4) + 1; b.skip(1)
            val values = if (lookup == 2) audioMultiply(entries.toLong(), dimensions.toLong()) else {
                // Integer root of entries. Products are bounded before multiplying.
                fun fits(base: Long): Boolean {
                    var product = 1L
                    repeat(dimensions) { if (base > entries / product) return false; product *= base }
                    return true
                }
                var low = 1L; var high = entries.toLong()
                while (low < high) { val mid = (low + high + 1) / 2; if (fits(mid)) low = mid else high = mid - 1 }
                low
            }
            b.skip(audioMultiply(values, bits.toLong()))
        }
    }
    repeat(b.read(6) + 1) { audioRequire(b.read(16) == 0) }
    val floors = b.read(6) + 1
    repeat(floors) {
        when (b.read(16)) {
            0 -> { b.skip(8 + 16 + 16 + 6 + 8L); repeat(b.read(4) + 1) { audioRequire(b.read(8) < codebooks) } }
            1 -> {
                val partitions = IntArray(b.read(5)) { b.read(4) }
                val classes = IntArray((partitions.maxOrNull() ?: -1) + 1)
                for (i in classes.indices) {
                    classes[i] = b.read(3) + 1
                    val sub = b.read(2)
                    if (sub > 0) audioRequire(b.read(8) < codebooks)
                    repeat(1 shl sub) { audioRequire(b.read(8) <= codebooks) }
                }
                b.skip(2); val rangeBits = b.read(4)
                for (partition in partitions) b.skip(classes[partition] * rangeBits.toLong())
            }
            else -> throw InvalidAudio()
        }
    }
    val residues = b.read(6) + 1
    repeat(residues) {
        audioRequire(b.read(16) in 0..2); b.skip(24 + 24 + 24L)
        val classifications = b.read(6) + 1; audioRequire(b.read(8) < codebooks)
        val cascades = IntArray(classifications) { val low = b.read(3); low or ((if (b.read(1) == 1) b.read(5) else 0) shl 3) }
        for (cascade in cascades) repeat(8) { if (cascade and (1 shl it) != 0) audioRequire(b.read(8) < codebooks) }
    }
    val mappings = b.read(6) + 1
    repeat(mappings) {
        audioRequire(b.read(16) == 0)
        val submaps = if (b.read(1) == 1) b.read(4) + 1 else 1
        if (b.read(1) == 1) repeat(b.read(8) + 1) {
            val magnitude = b.read(ilog(channels - 1)); val angle = b.read(ilog(channels - 1))
            audioRequire(magnitude != angle && magnitude < channels && angle < channels)
        }
        audioRequire(b.read(2) == 0)
        if (submaps > 1) repeat(channels) { audioRequire(b.read(4) < submaps) }
        repeat(submaps) { b.skip(8); audioRequire(b.read(8) < floors && b.read(8) < residues) }
    }
    val modes = BooleanArray(b.read(6) + 1) {
        val block = b.read(1) != 0
        audioRequire(b.read(16) == 0 && b.read(16) == 0 && b.read(8) < mappings)
        block
    }
    audioRequire(b.read(1) == 1)
    return modes
}

/** Validate each stream's Opus framing. All but the final stream are self-delimited. */
internal fun opusPacketFrames(size: Long, streams: Int, byte: (Long) -> Int): Int {
    var pos = 0L
    var duration = 0
    repeat(streams) { stream ->
        val toc = byte(pos++)
        val configuration = toc ushr 3
        val frameSamples = when {
            configuration >= 16 -> 120 shl (configuration and 3)
            configuration >= 12 -> 480 shl (configuration and 1)
            configuration and 3 == 3 -> 2880
            else -> 480 shl (configuration and 3)
        }
        val code = toc and 3
        var count = if (code == 0) 1 else 2
        var vbr = code == 2
        var padding = 0L
        if (code == 3) {
            val control = byte(pos++)
            count = control and 63; vbr = control and 128 != 0
            audioRequire(count in 1..48)
            if (control and 64 != 0) {
                var next: Int
                do {
                    next = byte(pos++)
                    padding = audioAdd(padding, if (next == 255) 254 else next.toLong())
                    audioRequire(padding <= size - pos)
                } while (next == 255)
            }
        }
        audioRequire(frameSamples * count <= 5760)
        val samples = frameSamples * count
        audioRequire(duration == 0 || duration == samples); duration = samples
        fun length(): Int {
            val first = byte(pos++)
            return if (first < 252) first else first + 4 * byte(pos++)
        }
        var known = 0L
        if (vbr) repeat(count - 1) { known = audioAdd(known, length().toLong()) }
        val selfDelimited = stream < streams - 1
        val payload = if (selfDelimited) {
            val last = length().toLong()
            if (vbr) audioAdd(known, last) else audioMultiply(count.toLong(), last)
        } else {
            audioRequire(pos <= size && padding <= size - pos)
            val available = size - pos - padding
            if (vbr) audioRequire(known <= available && available - known <= 1275)
            else audioRequire(available % count == 0L && available / count <= 1275)
            available
        }
        audioRequire(pos <= size && padding <= size - pos && payload <= size - pos - padding)
        pos += payload + padding
    }
    audioRequire(pos == size)
    return duration
}
