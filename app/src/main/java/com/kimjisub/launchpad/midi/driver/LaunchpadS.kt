package com.kimjisub.launchpad.midi.driver

import com.kimjisub.launchpad.manager.LaunchpadColor

class LaunchpadS : DriverRef() {
	companion object {
		internal val circleCode = arrayOf(
			intArrayOf(11, -80, 104),
			intArrayOf(11, -80, 105),
			intArrayOf(11, -80, 106),
			intArrayOf(11, -80, 107),
			intArrayOf(11, -80, 108),
			intArrayOf(11, -80, 109),
			intArrayOf(11, -80, 110),
			intArrayOf(11, -80, 111),
			intArrayOf(9, -112, 8),
			intArrayOf(9, -112, 24),
			intArrayOf(9, -112, 40),
			intArrayOf(9, -112, 56),
			intArrayOf(9, -112, 72),
			intArrayOf(9, -112, 88),
			intArrayOf(9, -112, 104),
			intArrayOf(9, -112, 120)
		)
	}

	override fun getSignal(cmd: Int, sig: Int, note: Int, velocity: Int) {
		if (cmd == 9) {
			val x = note / 16 + 1
			val y = note % 16 + 1
			if (y in 1..8)
				onPadTouch(x - 1, y - 1, velocity != 0, velocity)
			else if (y == 9) {
				onChainTouch(x - 1, velocity != 0)
				onFunctionKeyTouch(x - 1 + 8, velocity != 0)
			}
		} else if (cmd == 11) {
			if (note in 104..111)
				onFunctionKeyTouch(note - 104, velocity != 0)
		}
	}

	override fun sendPadLed(x: Int, y: Int, velocity: Int) {
		// x/y come from the pack's info file (buttonX/buttonY) and are not clamped upstream; an
		// out-of-grid pad would address the ring, or on a 16-note layout put a status byte in a
		// data position.
		if (x !in 0..7 || y !in 0..7) return
		sendSignal(9, -112, x * 16 + y, sCode(velocity))
	}

	// velocity is a LED code read out of a pack; SCode has 128 entries.
	private fun sCode(velocity: Int): Int = LaunchpadColor.SCode[velocity.coerceIn(0, LaunchpadColor.SCode.lastIndex)]

	override fun sendChainLed(c: Int, velocity: Int) {
		if (c in 0..7)
			sendFunctionKeyLed(c + 8, velocity)
	}

	override fun sendFunctionKeyLed(f: Int, velocity: Int) {
		if (f in 0..15)
			sendSignal(
				circleCode[f][0].toByte(),
				circleCode[f][1].toByte(),
				circleCode[f][2].toByte(),
				sCode(velocity).toByte()
			)
	}

	override fun sendClearLed() {
		for (i in 0..7)
			for (j in 0..7)
				sendPadLed(i, j, 0)
		for (i in 0..15)
			sendFunctionKeyLed(i, 0)
	}
}