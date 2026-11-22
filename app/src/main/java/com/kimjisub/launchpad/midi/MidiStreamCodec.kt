package com.kimjisub.launchpad.midi

/** A MIDI 1.0 byte stream has no USB cable/CIN header. Stores at most one incomplete message. */
internal class MidiStreamDecoder(private val event: (Int, Int, Int) -> Unit) {
	private var status = 0
	private var first = -1

	@Synchronized
	fun accept(bytes: ByteArray, offset: Int, count: Int) {
		if (offset < 0 || count < 0 || offset > bytes.size - count) return
		for (i in offset until offset + count) {
			val value = bytes[i].toInt() and 0xff
			if (value >= 0xf8) continue
			if (value >= 0x80) {
				status = if (value < 0xf0) value else 0
				first = -1
				continue
			}
			when (status shr 4) {
				8, 9, 10, 11, 14 -> {
					if (first < 0) first = value else {
						when (status shr 4) {
							8 -> event(status + 0x10, first, 0)
							9, 11 -> event(status, first, value)
						}
						first = -1
					}
				}
				// Program change/channel pressure consume one byte and keep running status.
				else -> first = -1
			}
		}
	}
}

/** Converts the existing drivers' USB-MIDI output at the transport boundary only. */
internal object MidiStreamEncoder {
	private val lengths = intArrayOf(0, 0, 2, 3, 3, 1, 2, 3, 3, 3, 3, 3, 2, 2, 3, 1)

	fun packet(cmd: Byte, status: Byte, note: Byte, value: Byte): ByteArray? =
		packets(byteArrayOf(cmd, status, note, value))

	fun packets(packets: ByteArray): ByteArray? {
		if (packets.size % 4 != 0 || packets.size > 4096) return null
		val output = ByteArray(packets.size)
		var size = 0
		for (i in packets.indices step 4) {
			val cin = packets[i].toInt() and 15
			val length = lengths[cin]
			if (length == 0) return null
			val status = packets[i + 1].toInt() and 255
			if (cin in 8..14 && (status shr 4 != cin || (2..length).any { packets[i + it] < 0 })) return null
			for (j in 1..length) output[size++] = packets[i + j]
		}
		return output.copyOf(size)
	}
}
