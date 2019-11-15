package com.kimjisub.launchpad.midi.driver

class LaunchpadMK2 : DriverRef() {
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
			intArrayOf(9, -112, 89),
			intArrayOf(9, -112, 79),
			intArrayOf(9, -112, 69),
			intArrayOf(9, -112, 59),
			intArrayOf(9, -112, 49),
			intArrayOf(9, -112, 39),
			intArrayOf(9, -112, 29),
			intArrayOf(9, -112, 19)
		)
	}

	override fun getSignal(cmd: Int, sig: Int, note: Int, velo: Int) {
		if (cmd == 9) {
			val x = 9 - note / 10
			val y = note % 10
			if (y in 1..8)
				onPadTouch(x - 1, y - 1, velo != 0, velo)
			else if (y == 9) {
				onChainTouch(x - 1, velo != 0)
				onFunctionkeyTouch(x - 1 + 8, velo != 0)
			}
		} else if (cmd == 11) {
			if (note in 104..111)
				onFunctionkeyTouch(note - 104, velo != 0)
		}
	}

	override fun sendPadLED(x: Int, y: Int, velo: Int) {
		sendSignal(9, -112, 10 * (8 - x) + y + 1, velo)
	}

	override fun sendChainLED(c: Int, velo: Int) {
		if (c in 0..7)
			sendFunctionkeyLED(c + 8, velo)
	}

	override fun sendFunctionkeyLED(f: Int, velo: Int) {
		if (f in 0..15)
			sendSignal(circleCode[f][0], circleCode[f][1], circleCode[f][2], velo)
	}

	override fun sendClearLED() {
		for (i in 0..7)
			for (j in 0..7)
				sendPadLED(i, j, 0)
		for (i in 0..15) sendFunctionkeyLED(i, 0)
	}
}