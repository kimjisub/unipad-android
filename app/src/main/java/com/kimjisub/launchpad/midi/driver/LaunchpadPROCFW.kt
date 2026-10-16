package com.kimjisub.launchpad.midi.driver

/** Launchpad Pro running the "Launchpad Open" custom firmware: LEDs on channel 16 of the first cable. */
class LaunchpadPROCFW : DrumRackDriver(cable = 0, channel = 15) {

	override fun getInitSysEx(): Pair<List<ByteArray>, Int> {
		return Pair(listOf(
			byteArrayOf(0xF0.toByte(), 0x00, 0x20, 0x29, 0x02, 0x10, 0x21, 0x01, 0xF7.toByte()), // Enter Performance Mode
			byteArrayOf(0xF0.toByte(), 0x00, 0x20, 0x29, 0x02, 0x10, 0x0E, 0x00, 0xF7.toByte()), // Clear canvas
		), 0)
	}

	override fun initialize() {
		val (messages, cable) = getInitSysEx()
		sendRawSignals(messages, cableNumber = cable)
	}

	// Note off, note on or control change, on any cable.
	override fun isButtonPacket(cmd: Int): Boolean = when (cmd and 0x0F) {
		0x08, 0x09, 0x0B -> true
		else -> false
	}
}
