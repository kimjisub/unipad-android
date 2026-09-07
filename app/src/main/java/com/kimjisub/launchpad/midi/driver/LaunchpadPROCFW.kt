package com.kimjisub.launchpad.midi.driver

class LaunchpadPROCFW : DriverRef() {

	companion object {


		// MIDI Channels for CFW
		private const val CHANNEL_LED = 15 // Channel 16
		private const val STATUS_NOTE_ON = (0x90 or CHANNEL_LED)

		// CIN (USB MIDI Code Index Number)
		private const val CIN_NOTE_ON = 0x09
	}

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

	override fun getSignal(cmd: Int, sig: Int, note: Int, velocity: Int) {
		val cin = cmd and 0x0F
		if (cin != 8 && cin != 9 && cin != 11) {
			// PlayActivity's Live Mode resync hook listens here, as it does for PRO/MK3.
			onUnknownReceived(cmd, sig, note, velocity)
			return
		}

		val isDown = cin != 8 && velocity > 0

		when (note) {
			in 36..99 -> {
				// Pads (8x8)
				val index = note - 36
				val row = 7 - (index % 32) / 4
				val col = if (index < 32) index % 4 else (index % 4) + 4
				onPadTouch(row, col, isDown, velocity)
			}
			in 28..35 -> {
				// Top row (f 0..7) - Left to Right
				onFunctionKeyTouch(note - 28, isDown)
			}
			in 100..107 -> {
				// Right column (c 0..7 / f 8..15) - Top to Bottom
				val c = note - 100
				onChainTouch(c, isDown)
				onFunctionKeyTouch(c + 8, isDown)
			}
			in 116..123 -> {
				// Bottom row (c 8..15 / f 16..23) - Right to Left
				val c = 15 - (note - 116)
				onChainTouch(c, isDown)
				onFunctionKeyTouch(c + 8, isDown)
			}
			in 108..115 -> {
				// Left column (c 16..23 / f 24..31) - Bottom to Top
				val c = 23 - (note - 108)
				onChainTouch(c, isDown)
				onFunctionKeyTouch(c + 8, isDown)
			}
			27 -> {
				// Top-right corner (Setup)
				onFunctionKeyTouch(32, isDown)
			}
			else -> {
				onUnknownReceived(cmd, sig, note, velocity)
			}
		}
	}

	override fun sendPadLed(x: Int, y: Int, velocity: Int) {
		// x/y come from the pack's info file (buttonX/buttonY) and are not clamped upstream; an
		// out-of-grid pad would address the ring, or on a 16-note layout put a status byte in a
		// data position.
		if (x !in 0..7 || y !in 0..7) return
		// UniPad passes x as row, y as col
		val rowInverted = 7 - x
		val note = if (y < 4) {
			36 + rowInverted * 4 + y
		} else {
			68 + rowInverted * 4 + (y - 4)
		}
		sendSignal(CIN_NOTE_ON, STATUS_NOTE_ON, note, velocity)
	}

	override fun sendChainLed(c: Int, velocity: Int) {
		if (c in 0..23) {
			sendFunctionKeyLed(c + 8, velocity)
		}
	}

	override fun sendFunctionKeyLed(f: Int, velocity: Int) {
		val note = when (f) {
			in 0..7 -> 28 + f           // Top (L-R: 28-35)
			in 8..15 -> 100 + (f - 8)   // Right (T-B: 100-107)
			in 16..23 -> 123 - (f - 16) // Bottom (R-L: 123-116)
			in 24..31 -> 115 - (f - 24) // Left (B-T: 115-108)
			32 -> 27                    // Top-right corner
			else -> return
		}
		sendSignal(CIN_NOTE_ON, STATUS_NOTE_ON, note, velocity)
	}

	override fun sendClearLed() {
		for (i in 0..7) {
			for (j in 0..7) {
				sendPadLed(i, j, 0)
			}
		}
		for (i in 0..32) {
			sendFunctionKeyLed(i, 0)
		}
	}
}
