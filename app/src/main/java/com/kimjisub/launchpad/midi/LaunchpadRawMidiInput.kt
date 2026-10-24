package com.kimjisub.launchpad.midi

/** The original Launchpad sends a MIDI byte stream rather than USB-MIDI event packets. */
internal class LaunchpadRawMidiInput {
	private var status = -1
	private var firstData = -1

	/** Returns the same command/status/note/value tuples as the USB-MIDI receive path. */
	fun decode(bytes: ByteArray, length: Int): IntArray {
		val events = ArrayList<Int>()
		for (i in 0 until length) {
			val value = bytes[i].toInt() and 0xff
			if (value >= 0xf8) continue // Real-time messages do not interrupt another message.
			if (value >= 0x80) {
				status = value
				firstData = -1
				continue
			}
			val command = status shr 4
			if (command != 8 && command != 9 && command != 11) continue
			if (firstData == -1) {
				firstData = value
			} else {
				// LaunchpadS handles releases as note-on with zero velocity, including a
				// note-off message whose release velocity is nonzero.
				events.add(if (command == 8) 9 else command)
				events.add((if (command == 8) status + 0x10 else status).toByte().toInt())
				events.add(firstData)
				events.add(if (command == 8) 0 else value)
				firstData = -1
			}
		}
		return events.toIntArray()
	}
}
