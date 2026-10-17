package com.kimjisub.launchpad.midi.driver

/**
 * The drum-rack layout custom Launchpad firmwares play in. The 8x8 grid is two 4x4 blocks, each
 * numbered from the bottom row up: the left half on notes 36..67, the right half on 68..99. The
 * round buttons are top 28..35 (left to right), right 100..107 (top down), left 108..115 (top down)
 * and bottom 116..123 (left to right); 27 is the top-right corner.
 *
 * Every LED goes out as a note-on on [cable] and [channel] (0-based).
 */
abstract class DrumRackDriver(cable: Int, channel: Int) : DriverRef() {
	private val ledCmd = (cable shl 4) or CIN_NOTE_ON
	private val ledStatus = STATUS_NOTE_ON or channel

	/** Whether a USB-MIDI packet with this first byte carries a button press or release. */
	protected abstract fun isButtonPacket(cmd: Int): Boolean

	/** The velocity this firmware shows as the colour of pack LED code [velocity]. */
	protected open fun ledVelocity(velocity: Int): Int = velocity

	override fun getSignal(cmd: Int, sig: Int, note: Int, velocity: Int) {
		if (!isButtonPacket(cmd)) {
			// PlayActivity's Live Mode resync hook listens here, as it does for PRO/MK3.
			onUnknownReceived(cmd, sig, note, velocity)
			return
		}

		val isDown = cmd and 0x0F != CIN_NOTE_OFF && velocity > 0

		when (note) {
			in 36..99 -> {
				val index = note - 36
				val row = 7 - (index % 32) / 4
				val col = if (index < 32) index % 4 else (index % 4) + 4
				onPadTouch(row, col, isDown, velocity)
			}
			in 28..35 -> onFunctionKeyTouch(note - 28, isDown)
			in 100..107 -> onRingChainTouch(note - 100, isDown)
			in 116..123 -> onRingChainTouch(15 - (note - 116), isDown)
			in 108..115 -> onRingChainTouch(23 - (note - 108), isDown)
			27 -> onFunctionKeyTouch(LOGO_FUNCTION_KEY, isDown)
			else -> onUnknownReceived(cmd, sig, note, velocity)
		}
	}

	private fun onRingChainTouch(c: Int, isDown: Boolean) {
		onChainTouch(c, isDown)
		onFunctionKeyTouch(c + 8, isDown)
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
		sendLed(note, velocity)
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
			LOGO_FUNCTION_KEY -> 27     // Top-right corner
			else -> return
		}
		sendLed(note, velocity)
	}

	private fun sendLed(note: Int, velocity: Int) {
		sendSignal(ledCmd, ledStatus, note, ledVelocity(velocity))
	}

	override fun sendClearLed() {
		for (i in 0..7) {
			for (j in 0..7) {
				sendPadLed(i, j, 0)
			}
		}
		for (i in 0..LOGO_FUNCTION_KEY) {
			sendFunctionKeyLed(i, 0)
		}
	}

	private companion object {
		// USB-MIDI Code Index Numbers
		const val CIN_NOTE_OFF = 0x08
		const val CIN_NOTE_ON = 0x09

		const val STATUS_NOTE_ON = 0x90
	}
}
