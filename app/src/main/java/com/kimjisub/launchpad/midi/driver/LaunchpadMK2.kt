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

	override fun getSignal(cmd: Int, sig: Int, note: Int, velocity: Int) {
		if (cmd == 9) {
			val x = 9 - note / 10
			val y = note % 10
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
		sendSignal(9, -112, 10 * (8 - x) + y + 1, velocity)
	}

	override fun sendChainLed(c: Int, velocity: Int) {
		if (c in 0..7)
			sendFunctionkeyLed(c + 8, velocity)
	}

	override fun sendFunctionkeyLed(f: Int, velocity: Int) {
		if (f in 0..15)
			sendSignal(circleCode[f][0], circleCode[f][1], circleCode[f][2], velocity)
	}

	override fun sendClearLed() {
		for (i in 0..7)
			for (j in 0..7)
				sendPadLed(i, j, 0)
		for (i in 0..15) sendFunctionkeyLed(i, 0)
	}
}