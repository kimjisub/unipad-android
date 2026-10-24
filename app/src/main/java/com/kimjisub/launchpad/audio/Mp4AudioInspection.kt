package com.kimjisub.launchpad.audio

// Resource ceilings are read failures, separate from corrupt structure and admission limits.
private const val MAX_AUDIO_BOXES = 4096
private const val MAX_AUDIO_TABLE_ROWS = 262_144L
private const val MAX_AUDIO_SAMPLE_SIZES = 1_048_576L
internal class AudioInspectionLimit : Exception()
private fun inspectionCapacity(count: Long, maximum: Long) {
    if (count > maximum) throw AudioInspectionLimit()
}

private data class AudioBox(val type: String, val data: Long, val end: Long)
private fun AudioData.boxes(from: Long, end: Long): Sequence<AudioBox> = sequence {
    var at = from
    var count = 0L
    while (at < end) {
        inspectionCapacity(++count, MAX_AUDIO_BOXES.toLong())
        range(at, 8, end)
        val small = uint(at, 4)
        val header = if (small == 1L) 16 else 8
        range(at, header.toLong(), end)
        val size = when (small) { 0L -> end - at; 1L -> uint(at + 8, 8); else -> small }
        audioRequire(size >= header)
        range(at, size, end)
        val type = (0..3).map { byte(at + 4 + it).toChar() }.joinToString("")
        yield(AudioBox(type, at + header, at + size)); at += size
    }
}
private fun AudioData.child(parent: AudioBox, type: String): AudioBox {
    val matches = boxes(parent.data, parent.end).filter { it.type == type }.iterator()
    audioRequire(matches.hasNext())
    val found = matches.next(); audioRequire(!matches.hasNext()); return found
}

/** Checks sample counts, timing and every referenced sample's physical mdat extent. */
internal fun inspectMp4(d: AudioData): AudioFileFacts {
    val top = d.boxes(0, d.size).toList()
    val media = top.filter { it.type == "mdat" }
    audioRequire(media.isNotEmpty())
    val moov = top.singleOrNull { it.type == "moov" } ?: throw InvalidAudio()
    for (track in d.boxes(moov.data, moov.end).filter { it.type == "trak" }) {
        val mdia = d.child(track, "mdia")
        val handler = d.child(mdia, "hdlr")
        d.range(handler.data, 12, handler.end)
        if (!d.matches(handler.data + 8, "soun")) continue
        val mdhd = d.child(mdia, "mdhd")
        val version = d.byte(mdhd.data)
        audioRequire(version in 0..1)
        val clockOffset = if (version == 0) 12 else 20
        d.range(mdhd.data, clockOffset + 4L, mdhd.end)
        val clock = d.uint(mdhd.data + clockOffset, 4)
        audioRequire(clock > 0)
        val stbl = d.child(d.child(mdia, "minf"), "stbl")
        val stsd = d.child(stbl, "stsd")
        d.range(stsd.data, 8, stsd.end)
        val entries = d.boxes(stsd.data + 8, stsd.end).toList()
        audioRequire(entries.size.toLong() == d.uint(stsd.data + 4, 4) && entries.isNotEmpty())
        val codecs = entries.map { mp4Codec(d, it) }
        val codec = codecs.first()
        // A description switch must never reinterpret packets with a cheaper codec/configuration.
        audioRequire(codecs.all { it == codec })
        val rate = codec.rate
        val channels = codec.channels
        val aacPacketFrames = codec.aacFrames
        val stts = d.child(stbl, "stts")
        val timeCount = tableCount(d, stts, 8)
        var samples = 0L
        var ticks = 0L
        var verifiedFrames = 0L
        for (i in 0 until timeCount) {
            val at = stts.data + 8 + i * 8
            val count = d.uint(at, 4); val delta = d.uint(at + 4, 4)
            audioRequire(count > 0)
            // Android encoders can timestamp the first AAC packet with zero delta.
            // It still contains one full codec block; reserve it once, never infer zero PCM.
            val zeroFirst = i == 0L && count == 1L && delta == 0L && aacPacketFrames != null
            audioRequire(delta > 0 || zeroFirst)
            samples = audioAdd(samples, count); ticks = audioAdd(ticks, audioMultiply(count, delta))
            aacPacketFrames?.let { packet ->
                val actual = audioMultiply(delta, rate.toLong())
                val full = audioMultiply(packet.toLong(), clock)
                val partialLast = !zeroFirst && i == timeCount - 1 && count == 1L && actual < full
                // Only a final packet may be shortened. Other packets each contain a full AAC block.
                audioRequire(zeroFirst || partialLast || (if (actual >= full) actual - full else full - actual) < rate)
                val frames = if (partialLast) actual / clock + if (actual % clock == 0L) 0 else 1 else packet.toLong()
                verifiedFrames = audioAdd(verifiedFrames, audioMultiply(count, frames))
            }
        }
        val stsz = d.child(stbl, "stsz")
        d.range(stsz.data, 12, stsz.end)
        val constantSize = d.uint(stsz.data + 4, 4)
        val sizeCount = d.uint(stsz.data + 8, 4)
        audioRequire(sizeCount == samples && samples > 0)
        if (constantSize == 0L) {
            inspectionCapacity(sizeCount, MAX_AUDIO_SAMPLE_SIZES)
            d.range(stsz.data + 12, audioMultiply(sizeCount, 4), stsz.end)
        }
        val offsets = d.boxes(stbl.data, stbl.end).filter { it.type == "stco" || it.type == "co64" }.toList()
        audioRequire(offsets.size == 1)
        val offsetBox = offsets.single()
        val width = if (offsetBox.type == "co64") 8 else 4
        val chunks = tableCount(d, offsetBox, width)
        val stsc = d.child(stbl, "stsc")
        val mappings = tableCount(d, stsc, 12)
        audioRequire(chunks > 0 && mappings > 0)
        if (codec.pcmAlign == 0 && aacPacketFrames == null) inspectionCapacity(samples, MAX_AUDIO_SAMPLE_SIZES)
        var physicalFrames = 0L
        var mapping = 0L
        var used = 0L
        // Primitive arrays bound allocation to 4 MiB at the row ceiling; no boxed Pair per chunk.
        val starts = LongArray(chunks.toInt())
        val ends = LongArray(chunks.toInt())
        var nextFirst = if (mappings > 1) d.uint(stsc.data + 20, 4) else chunks + 1
        audioRequire(d.uint(stsc.data + 8, 4) == 1L)
        for (chunk in 1..chunks) {
            if (chunk == nextFirst) {
                mapping++
                nextFirst = if (mapping + 1 < mappings) d.uint(stsc.data + 8 + (mapping + 1) * 12, 4) else chunks + 1
            }
            audioRequire(nextFirst > chunk && nextFirst <= chunks + 1)
            val row = stsc.data + 8 + mapping * 12
            val count = d.uint(row + 4, 4)
            val description = d.uint(row + 8, 4)
            audioRequire(count > 0 && count <= samples - used && description in 1..entries.size.toLong())
            var bytes = 0L
            if (constantSize != 0L) bytes = audioMultiply(count, constantSize)
            else for (sample in used until used + count) {
                val size = d.uint(stsz.data + 12 + sample * 4, 4)
                audioRequire(size > 0); bytes = audioAdd(bytes, size)
            }
            val offset = d.uint(offsetBox.data + 8 + (chunk - 1) * width, width)
            // Top-level boxes occur in physical order. Find mdat with a binary search rather
            // than scanning every mdat for every chunk in a file-controlled cross product.
            var low = 0
            var high = media.lastIndex
            while (low <= high) {
                val middle = (low + high) ushr 1
                if (media[middle].data <= offset) low = middle + 1 else high = middle - 1
            }
            audioRequire(high >= 0)
            val extent = media[high]
            audioRequire(offset <= extent.end && bytes <= extent.end - offset)
            if (codec.pcmAlign != 0) {
                audioRequire(bytes % codec.pcmAlign == 0L)
                physicalFrames = audioAdd(physicalFrames, bytes / codec.pcmAlign)
            } else if (aacPacketFrames == null) {
                var packetAt = offset
                for (sample in used until used + count) {
                    val size = if (constantSize != 0L) constantSize else d.uint(stsz.data + 12 + sample * 4, 4)
                    val frames = when (codec.type) {
                        "alac" -> alacPacketFrames(d, packetAt, size, codec.alacFrames)
                        "fLaC" -> inspectFlacFrames(d, packetAt, packetAt + size, rate, channels, codec.precision)
                        "Opus" -> opusPacketFrames(size, codec.opusStreams) { index ->
                            audioRequire(index in 0 until size); d.byte(packetAt + index)
                        }.toLong()
                        else -> throw InvalidAudio()
                    }
                    physicalFrames = audioAdd(physicalFrames, frames)
                    packetAt += size
                }
            }
            starts[(chunk - 1).toInt()] = offset
            ends[(chunk - 1).toInt()] = offset + bytes
            used += count
        }
        audioRequire(used == samples && mapping + 1 == mappings)
        // Disjoint positive intervals have the same start and end ordering. Sorting both
        // arrays independently detects overlaps, including nested intervals, without objects.
        starts.sort(); ends.sort()
        for (i in 1 until starts.size) audioRequire(starts[i] >= ends[i - 1])
        val numerator = audioMultiply(ticks, rate.toLong())
        val frames = if (aacPacketFrames != null) verifiedFrames else {
            // Container timing may be coarser than one output frame. It cannot lower
            // the independently counted physical reservation, even for the final packet.
            val timeline = numerator / clock + if (numerator % clock == 0L) 0 else 1
            maxOf(physicalFrames, timeline)
        }
        return AudioFileFacts(rate, channels, frames, "m4a")
    }
    throw InvalidAudio()
}

private data class Mp4Codec(val type: String, val rate: Int, val channels: Int,
    val aacFrames: Int? = null, val alacFrames: Long = 0, val precision: Int = 0,
    val opusStreams: Int = 0, val pcmAlign: Int = 0)

private fun mp4Codec(d: AudioData, entry: AudioBox): Mp4Codec {
    audioRequire(entry.type in setOf("mp4a", "alac", "fLaC", "Opus", "sowt", "twos", "lpcm"))
    d.range(entry.data, 28, entry.end)
    val version = d.uint(entry.data + 8, 2).toInt()
    audioRequire(version in 0..1)
    val headerSize = if (version == 1) 44L else 28L
    d.range(entry.data, headerSize, entry.end)
    val children = AudioBox("entry", entry.data + headerSize, entry.end)
    var channels = d.uint(entry.data + 16, 2).toInt()
    var rate = (d.uint(entry.data + 24, 4) ushr 16).toInt()
    val codec = when (entry.type) {
        "mp4a" -> {
            val boxes = d.boxes(children.data, children.end).toList()
            val esds = boxes.singleOrNull { it.type == "esds" }
                ?: boxes.singleOrNull { it.type == "wave" }?.let { d.child(it, "esds") }
                ?: throw InvalidAudio()
            val config = aacConfig(d, esds)
            rate = config.rate
            if (config.channels != 0) channels = config.channels
            Mp4Codec(entry.type, rate, channels, aacFrames = config.packetFrames)
        }
        "alac" -> {
            val config = d.child(children, "alac")
            // FullBox followed by ALACSpecificConfig; frameLength bounds every packet.
            d.range(config.data, 28, config.end)
            val frames = d.uint(config.data + 4, 4)
            audioRequire(frames > 0)
            Mp4Codec(entry.type, d.uint(config.data + 24, 4).toInt(),
                d.byte(config.data + 13), alacFrames = frames)
        }
        "fLaC" -> {
            val config = d.child(children, "dfLa")
            d.range(config.data, 42, config.end)
            audioRequire(d.uint(config.data, 4) == 0L && d.byte(config.data + 4) and 127 == 0 &&
                d.uint(config.data + 5, 3) == 34L)
            val info = config.data + 8
            rate = (d.uint(info + 10, 3) ushr 4).toInt()
            channels = ((d.byte(info + 12) ushr 1) and 7) + 1
            val bits = (((d.byte(info + 12) and 1) shl 4) or (d.byte(info + 13) ushr 4)) + 1
            Mp4Codec(entry.type, rate, channels, precision = bits)
        }
        "Opus" -> {
            val config = d.child(children, "dOps")
            d.range(config.data, 11, config.end)
            audioRequire(d.byte(config.data) == 0)
            channels = d.byte(config.data + 1)
            val family = d.byte(config.data + 10)
            var streams = 1
            if (family == 0) audioRequire(channels in 1..2)
            else {
                d.range(config.data, 13L + channels, config.end)
                streams = d.byte(config.data + 11)
                val coupled = d.byte(config.data + 12)
                audioRequire(streams > 0 && coupled <= streams && streams + coupled <= 255)
                repeat(channels) {
                    val mapping = d.byte(config.data + 13 + it)
                    audioRequire(mapping == 255 || mapping < streams + coupled)
                }
            }
            Mp4Codec(entry.type, 48_000, channels, opusStreams = streams)
        }
        else -> {
            val bits = d.uint(entry.data + 18, 2).toInt()
            audioRequire(bits in 8..64 && bits % 8 == 0 && channels in 1..8)
            val align = channels * (bits / 8)
            if (version == 1) {
                audioRequire(d.uint(entry.data + 32, 4) == (bits / 8).toLong() &&
                    d.uint(entry.data + 36, 4) == align.toLong())
            }
            Mp4Codec(entry.type, rate, channels, pcmAlign = align)
        }
    }
    audioRequire(codec.rate > 0 && codec.channels > 0)
    return codec
}

private fun alacPacketFrames(d: AudioData, at: Long, size: Long, maximum: Long): Long {
    val bits = AudioBits(audioMultiply(size, 8), false) { index -> d.byte(at + index) }
    // First channel element: instance tag, unused bits, has_size, extra_bits, compressed flag.
    // Every channel element in a decodable packet must declare the same output count.
    audioRequire(bits.read(3) in setOf(0, 1, 3))
    bits.skip(16)
    val explicit = bits.read(1) != 0
    bits.skip(3)
    val frames = if (explicit) bits.read(32).toLong() and 0xffffffffL else maximum
    audioRequire(frames in 1..maximum)
    return frames
}

private fun tableCount(d: AudioData, box: AudioBox, stride: Int): Long {
    d.range(box.data, 8, box.end)
    val count = d.uint(box.data + 4, 4)
    d.range(box.data + 8, audioMultiply(count, stride.toLong()), box.end)
    inspectionCapacity(count, MAX_AUDIO_TABLE_ROWS)
    return count
}

private data class AacConfig(val rate: Int, val channels: Int, val packetFrames: Int)
private fun aacConfig(d: AudioData, box: AudioBox): AacConfig {
    fun descriptor(at: Long, end: Long): Triple<Int, Long, Long> {
        d.range(at, 2, end)
        val tag = d.byte(at)
        var pos = at + 1
        var length = 0L
        var done = false
        repeat(4) {
            if (!done) {
                d.range(pos, 1, end); val b = d.byte(pos++)
                length = length * 128 + (b and 127)
                if (b and 128 == 0) done = true
            }
        }
        audioRequire(done); d.range(pos, length, end)
        return Triple(tag, pos, pos + length)
    }
    val es = descriptor(box.data + 4, box.end)
    audioRequire(es.first == 3); d.range(es.second, 3, es.third)
    val flags = d.byte(es.second + 2)
    var pos = es.second + 3
    if (flags and 128 != 0) pos += 2
    if (flags and 64 != 0) { d.range(pos, 1, es.third); pos += 1 + d.byte(pos) }
    if (flags and 32 != 0) pos += 2
    val decoder = descriptor(pos, es.third)
    audioRequire(decoder.first == 4); d.range(decoder.second, 13, decoder.third)
    val config = descriptor(decoder.second + 13, decoder.third)
    audioRequire(config.first == 5)
    val bits = AudioBits((config.third - config.second) * 8, false) { d.byte(config.second + it) }
    fun objectType(): Int = bits.read(5).let { if (it == 31) 32 + bits.read(6) else it }
    fun frequency(): Int {
        val index = bits.read(4)
        val rates = AAC_SAMPLE_RATES
        return if (index == 15) bits.read(24) else { audioRequire(index < rates.size); rates[index] }
    }
    var type = objectType()
    var rate = frequency()
    val coreRate = rate
    val configuration = bits.read(4)
    audioRequire(configuration <= 7)
    var channels = if (configuration == 7) 8 else configuration
    if (type == 5 || type == 29) {
        val parametricStereo = type == 29
        rate = frequency(); type = objectType()
        if (parametricStereo && channels == 1) channels = 2
    }
    val shortFrame = bits.read(1) != 0
    val baseFrames = if (type == 23 || type == 39) {
        if (shortFrame) 480 else 512
    } else if (shortFrame) 960 else 1024
    // LC streams can signal SBR/PS in the sync extension rather than the initial object type.
    if (type in 1..4 && configuration != 0) {
        if (bits.read(1) != 0) bits.skip(14)
        val extension = bits.read(1)
        if (extension == 0 && bits.position + 16 <= (config.third - config.second) * 8) {
            if (bits.read(11) == 0x2b7 && objectType() == 5 && bits.read(1) != 0) {
                rate = frequency()
                if (bits.position + 12 <= (config.third - config.second) * 8 && bits.read(11) == 0x548 && bits.read(1) != 0 && channels == 1) channels = 2
            }
        }
    }
    audioRequire(coreRate > 0 && rate % coreRate == 0 && rate / coreRate in 1..2)
    return AacConfig(rate, channels, baseFrames * (rate / coreRate))
}

/** A bounded bit reader, used for structural codec headers, never decoded PCM. */
internal class AudioBits(private val size: Long, private val little: Boolean, private val byte: (Long) -> Int) {
    var position = 0L
        private set
    fun read(count: Int): Int {
        audioRequire(count in 0..32 && position <= size && count <= size - position)
        var result = 0
        repeat(count) { index ->
            val value = byte(position / 8) ushr (if (little) (position % 8).toInt() else 7 - (position % 8).toInt()) and 1
            if (little) result = result or (value shl index) else result = (result shl 1) or value
            position++
        }
        return result
    }
    fun skip(count: Long) { audioRequire(count >= 0 && position <= size && count <= size - position); position += count }
}
