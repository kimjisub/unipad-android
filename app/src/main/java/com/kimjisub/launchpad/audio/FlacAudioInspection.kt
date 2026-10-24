package com.kimjisub.launchpad.audio

private data class FlacFrame(val end: Long, val samples: Int, val number: Long, val variable: Boolean, val assignment: Int)
private fun crcStep(value: Int, byte: Int, width: Int, polynomial: Int): Int {
    var crc = value xor (byte shl (width - 8))
    repeat(8) { crc = (crc shl 1) xor if (crc and (1 shl (width - 1)) != 0) polynomial else 0 }
    return crc and ((1 shl width) - 1)
}

/** STREAMINFO's total-sample field is a hint; count CRC-checked physical frame blocks instead. */
internal fun inspectFlac(d: AudioData): AudioFileFacts {
    var at = 4L
    var last = false
    var rate = 0
    var channels = 0
    var bits = 0
    while (!last) {
        d.range(at, 4)
        val type = d.byte(at)
        val length = d.uint(at + 1, 3)
        val payload = at + 4
        d.range(payload, length)
        if (at == 4L) {
            audioRequire(type and 127 == 0 && length == 34L)
            rate = ((d.uint(payload + 10, 3) ushr 4)).toInt()
            channels = ((d.byte(payload + 12) ushr 1) and 7) + 1
            bits = (((d.byte(payload + 12) and 1) shl 4) or (d.byte(payload + 13) ushr 4)) + 1
            audioRequire(rate > 0)
        } else audioRequire(type and 127 != 0 && type and 127 < 127)
        last = type and 128 != 0; at = payload + length
    }
    val total = inspectFlacFrames(d, at, d.size, rate, channels, bits, true)
    return AudioFileFacts(rate, channels, total, "flac")
}

/** Also used by MP4: each sample is bounded independently of its timestamp table. */
internal fun inspectFlacFrames(d: AudioData, from: Long, limit: Long, rate: Int,
    channels: Int, bits: Int, requireZeroOrigin: Boolean = false): Long {
    fun header(start: Long): FlacFrame {
        d.range(start, 6, limit)
        audioRequire(d.byte(start) == 255 && d.byte(start + 1) and 0xfe == 0xf8)
        val variable = d.byte(start + 1) and 1 != 0
        val blockCode = d.byte(start + 2) ushr 4
        val rateCode = d.byte(start + 2) and 15
        val assignment = d.byte(start + 3) ushr 4
        val precision = (d.byte(start + 3) ushr 1) and 7
        audioRequire(d.byte(start + 3) and 1 == 0 && assignment <= 10)
        audioRequire((if (assignment < 8) assignment + 1 else 2) == channels)
        audioRequire(precision != 3)
        val frameBits = intArrayOf(bits, 8, 12, 0, 16, 20, 24, 32)[precision]
        audioRequire(frameBits == bits)
        var pos = start + 4
        val first = d.byte(pos++)
        var number: Long
        if (first < 128) number = first.toLong()
        else {
            val count = Integer.numberOfLeadingZeros(first.inv() and 255) - 24
            audioRequire(count in 2..7)
            number = (first and (0x7f ushr count)).toLong()
            repeat(count - 1) {
                val next = d.byte(pos++); audioRequire(next and 0xc0 == 0x80)
                number = audioAdd(audioMultiply(number, 64), (next and 63).toLong())
            }
        }
        val samples = when (blockCode) {
            1 -> 192
            in 2..5 -> 576 shl (blockCode - 2)
            6 -> d.byte(pos++).plus(1)
            7 -> d.uint(pos, 2).toInt().plus(1).also { pos += 2 }
            in 8..15 -> 256 shl (blockCode - 8)
            else -> throw InvalidAudio()
        }
        val frameRate = when (rateCode) {
            0 -> rate
            in 1..11 -> intArrayOf(88_200,176_400,192_000,8_000,16_000,22_050,24_000,32_000,44_100,48_000,96_000)[rateCode - 1]
            12 -> d.byte(pos++) * 1000
            13 -> d.uint(pos, 2).toInt().also { pos += 2 }
            14 -> (d.uint(pos, 2).toInt() * 10).also { pos += 2 }
            else -> throw InvalidAudio()
        }
        audioRequire(frameRate == rate)
        d.range(start, pos - start + 1, limit)
        var crc = 0
        for (index in start until pos) crc = crcStep(crc, d.byte(index), 8, 7)
        audioRequire(crc == d.byte(pos++))
        return FlacFrame(pos, samples, number, variable, assignment)
    }
    var at = from
    audioRequire(at < limit)
    var frame = header(at)
    val origin = if (requireZeroOrigin) 0 else frame.number
    var total = 0L
    var ordinal = 0L
    while (true) {
        audioRequire(frame.number == audioAdd(origin, if (frame.variable) total else ordinal))
        val body = AudioBits(audioMultiply(limit - frame.end, 8), false) { d.byte(frame.end + it) }
        fun residual(order: Int) {
            val method = body.read(2); audioRequire(method in 0..1)
            val parameterBits = if (method == 0) 4 else 5
            val partitions = 1 shl body.read(4)
            audioRequire(frame.samples % partitions == 0)
            repeat(partitions) { partition ->
                val count = frame.samples / partitions - if (partition == 0) order else 0
                audioRequire(count >= 0)
                val parameter = body.read(parameterBits)
                if (parameter == (1 shl parameterBits) - 1) body.skip(audioMultiply(count.toLong(), body.read(5).toLong()))
                else repeat(count) { while (body.read(1) == 0) { /* bounded by the physical file */ }; body.skip(parameter.toLong()) }
            }
        }
        repeat(channels) { channel ->
            val extra = when (frame.assignment) { 8, 10 -> channel == 1; 9 -> channel == 0; else -> false }
            var precision = bits + if (extra) 1 else 0
            audioRequire(body.read(1) == 0)
            val type = body.read(6)
            if (body.read(1) != 0) {
                var wasted = 1
                while (body.read(1) == 0) { wasted++; audioRequire(wasted < precision) }
                precision -= wasted
            }
            audioRequire(precision > 0)
            when {
                type == 0 -> body.skip(precision.toLong())
                type == 1 -> body.skip(audioMultiply(frame.samples.toLong(), precision.toLong()))
                type in 8..12 -> {
                    val order = type - 8; audioRequire(order <= frame.samples)
                    body.skip(audioMultiply(order.toLong(), precision.toLong())); residual(order)
                }
                type in 32..63 -> {
                    val order = (type and 31) + 1; audioRequire(order <= frame.samples)
                    body.skip(audioMultiply(order.toLong(), precision.toLong()))
                    val coefficientBits = body.read(4); audioRequire(coefficientBits < 15)
                    body.skip(5); body.skip(order * (coefficientBits + 1L)); residual(order)
                }
                else -> throw InvalidAudio()
            }
        }
        val padding = ((8 - body.position % 8) % 8).toInt()
        audioRequire(body.read(padding) == 0)
        val end = audioAdd(frame.end, body.position / 8)
        d.range(end, 2, limit)
        var crc = 0
        for (pos in at until end + 2) crc = crcStep(crc, d.byte(pos), 16, 0x8005)
        audioRequire(crc == 0)
        total = audioAdd(total, frame.samples.toLong()); ordinal++
        at = end + 2
        if (at == limit) break
        val next = header(at); audioRequire(next.variable == frame.variable); frame = next
    }
    return total
}
