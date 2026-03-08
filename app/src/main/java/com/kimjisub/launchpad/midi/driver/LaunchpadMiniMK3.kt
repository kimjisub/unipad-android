package com.kimjisub.launchpad.midi.driver

class LaunchpadMiniMK3 : LaunchpadX() {
	override fun getInitSysEx(): Pair<List<ByteArray>, Int> {
		// Launchpad Mini MK3 uses SysEx header 02 0D (vs X's 02 0C)
		// Same protocol as Launchpad X (cable 1, cmd 25/27)
		// Standalone mode + Programmer mode, sent via DAW port (port 1)
		return Pair(listOf(
			byteArrayOf(0xF0.toByte(), 0x00, 0x20, 0x29, 0x02, 0x0D, 0x10, 0x00, 0xF7.toByte()),
			byteArrayOf(0xF0.toByte(), 0x00, 0x20, 0x29, 0x02, 0x0D, 0x0E, 0x01, 0xF7.toByte()),
		), 1)
	}
}
