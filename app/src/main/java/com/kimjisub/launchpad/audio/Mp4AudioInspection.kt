package com.kimjisub.launchpad.audio

private data class AudioBox(val type: String, val data: Long, val end: Long)
private fun AudioData.boxes(from: Long, end: Long): Sequence<AudioBox> = sequence {
    var at = from
    while (at < end) {
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
        var rate = 0
        var channels = 0
        var aacPacketFrames: Int? = null
        for (entry in entries) {
            audioRequire(entry.type in setOf("mp4a", "alac", "fLaC", "Opus", "sowt", "twos", "lpcm"))
            d.range(entry.data, 28, entry.end)
            val soundVersion = d.uint(entry.data + 8, 2).toInt()
            audioRequire(soundVersion in 0..1)
            var c = d.uint(entry.data + 16, 2).toInt()
            var r = (d.uint(entry.data + 24, 4) ushr 16).toInt()
            // AAC's AudioSpecificConfig is authoritative when the 16.16 field cannot hold its rate.
            if (entry.type == "mp4a") {
                val children = d.boxes(entry.data + if (soundVersion == 1) 44 else 28, entry.end).toList()
                val esds = children.singleOrNull { it.type == "esds" }
                    ?: children.singleOrNull { it.type == "wave" }?.let { wave -> d.child(wave, "esds") }
                    ?: throw InvalidAudio()
                val config = aacConfig(d, esds)
                r = config.rate
                if (config.channels != 0) c = config.channels
                audioRequire(aacPacketFrames == null || aacPacketFrames == config.packetFrames)
                aacPacketFrames = config.packetFrames
            }
            audioRequire(r > 0 && c > 0 && (rate == 0 || rate == r && channels == c))
            rate = r; channels = c
        }
        val stts = d.child(stbl, "stts")
        val timeCount = tableCount(d, stts, 8)
        var samples = 0L
        var ticks = 0L
        var verifiedFrames = 0L
        for (i in 0 until timeCount) {
            val at = stts.data + 8 + i * 8
            val count = d.uint(at, 4); val delta = d.uint(at + 4, 4)
            audioRequire(count > 0 && delta > 0)
            samples = audioAdd(samples, count); ticks = audioAdd(ticks, audioMultiply(count, delta))
            aacPacketFrames?.let { packet ->
                val actual = audioMultiply(delta, rate.toLong())
                val full = audioMultiply(packet.toLong(), clock)
                val partialLast = i == timeCount - 1 && count == 1L && actual < full
                // Only a final packet may be shortened. Other packets each contain a full AAC block.
                audioRequire(partialLast || (if (actual >= full) actual - full else full - actual) < rate)
                val frames = if (partialLast) actual / clock + if (actual % clock == 0L) 0 else 1 else packet.toLong()
                verifiedFrames = audioAdd(verifiedFrames, audioMultiply(count, frames))
            }
        }
        val stsz = d.child(stbl, "stsz")
        d.range(stsz.data, 12, stsz.end)
        val constantSize = d.uint(stsz.data + 4, 4)
        val sizeCount = d.uint(stsz.data + 8, 4)
        audioRequire(sizeCount == samples && samples > 0)
        if (constantSize == 0L) d.range(stsz.data + 12, audioMultiply(sizeCount, 4), stsz.end)
        val offsets = d.boxes(stbl.data, stbl.end).filter { it.type == "stco" || it.type == "co64" }.toList()
        audioRequire(offsets.size == 1)
        val offsetBox = offsets.single()
        val width = if (offsetBox.type == "co64") 8 else 4
        val chunks = tableCount(d, offsetBox, width)
        val stsc = d.child(stbl, "stsc")
        val mappings = tableCount(d, stsc, 12)
        audioRequire(chunks > 0 && mappings > 0)
        var mapping = 0L
        var used = 0L
        val extents = mutableListOf<Pair<Long, Long>>()
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
            audioRequire(media.any { offset >= it.data && offset <= it.end && bytes <= it.end - offset })
            extents.add(offset to offset + bytes)
            used += count
        }
        audioRequire(used == samples && mapping + 1 == mappings)
        val sorted = extents.sortedBy { it.first }
        for (i in 1 until sorted.size) audioRequire(sorted[i].first >= sorted[i - 1].second)
        val numerator = audioMultiply(ticks, rate.toLong())
        val frames = if (aacPacketFrames != null) verifiedFrames else {
            audioRequire(numerator % clock == 0L); numerator / clock
        }
        return AudioFileFacts(rate, channels, frames, "m4a")
    }
    throw InvalidAudio()
}

private fun tableCount(d: AudioData, box: AudioBox, stride: Int): Long {
    d.range(box.data, 8, box.end)
    val count = d.uint(box.data + 4, 4)
    d.range(box.data + 8, audioMultiply(count, stride.toLong()), box.end)
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
        val rates = intArrayOf(96_000,88_200,64_000,48_000,44_100,32_000,24_000,22_050,16_000,12_000,11_025,8_000,7_350)
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
