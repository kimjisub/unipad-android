package com.kimjisub.launchpad.midi

/** Splits a SysEx message into 4-byte USB-MIDI event packets (USB MIDI 1.0, CIN 4h to 7h). */
internal object UsbMidiSysEx {

	fun encode(sysex: ByteArray, cableNumber: Int = 0): ByteArray {
		val cablePrefix = (cableNumber shl 4).toByte()
		val packets = mutableListOf<Byte>()
		var i = 0
		while (i < sysex.size) {
			val remaining = sysex.size - i
			if (remaining >= 3 && sysex[i + 2] != 0xF7.toByte()) {
				// SysEx start or continue: CIN = 0x04
				packets.add((cablePrefix + 0x04).toByte())
				packets.add(sysex[i])
				packets.add(sysex[i + 1])
				packets.add(sysex[i + 2])
				i += 3
			} else if (remaining == 1) {
				// SysEx end with 1 byte: CIN = 0x05
				packets.add((cablePrefix + 0x05).toByte())
				packets.add(sysex[i])
				packets.add(0x00)
				packets.add(0x00)
				i += 1
			} else if (remaining == 2) {
				// SysEx end with 2 bytes: CIN = 0x06
				packets.add((cablePrefix + 0x06).toByte())
				packets.add(sysex[i])
				packets.add(sysex[i + 1])
				packets.add(0x00)
				i += 2
			} else {
				// SysEx end with 3 bytes: CIN = 0x07
				packets.add((cablePrefix + 0x07).toByte())
				packets.add(sysex[i])
				packets.add(sysex[i + 1])
				packets.add(sysex[i + 2])
				i += 3
			}
		}
		return packets.toByteArray()
	}
}
