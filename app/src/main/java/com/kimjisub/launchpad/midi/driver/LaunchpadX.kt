package com.kimjisub.launchpad.midi.driver

class LaunchpadX : DriverRef() {
	companion object {
		internal val circleCode = arrayOf(
			intArrayOf(27, -80, 91),
			intArrayOf(27, -80, 92),
			intArrayOf(27, -80, 93),
			intArrayOf(27, -80, 94),
			intArrayOf(27, -80, 95),
			intArrayOf(27, -80, 96),
			intArrayOf(27, -80, 97),
			intArrayOf(27, -80, 98),
			intArrayOf(27, -80, 89),
			intArrayOf(27, -80, 79),
			intArrayOf(27, -80, 69),
			intArrayOf(27, -80, 59),
			intArrayOf(27, -80, 49),
			intArrayOf(27, -80, 39),
			intArrayOf(27, -80, 29),
			intArrayOf(27, -80, 19),
			intArrayOf(27, -80, 8),
			intArrayOf(27, -80, 7),
			intArrayOf(27, -80, 6),
			intArrayOf(27, -80, 5),
			intArrayOf(27, -80, 4),
			intArrayOf(27, -80, 3),
			intArrayOf(27, -80, 2),
			intArrayOf(27, -80, 1),
			intArrayOf(27, -80, 10),
			intArrayOf(27, -80, 20),
			intArrayOf(27, -80, 30),
			intArrayOf(27, -80, 40),
			intArrayOf(27, -80, 50),
			intArrayOf(27, -80, 60),
			intArrayOf(27, -80, 70),
			intArrayOf(27, -80, 80)
		)
	}

	override fun getSignal(cmd: Int, sig: Int, note: Int, velo: Int) {
		if (cmd == 25) {
			val x = 9 - note / 10
			val y = note % 10
			if (y in 1..8)
				onPadTouch(x - 1, y - 1, velo != 0, velo)
		} else if (cmd == 27 && sig == -80) {
			if (note in 91..98) {
				onFunctionkeyTouch(note - 91, velo != 0)
			}
			if (note in 19..89 && note % 10 == 9) {
				val c = 9 - note / 10 - 1
				onChainTouch(c, velo != 0)
				onFunctionkeyTouch(c + 8, velo != 0)
			}
			if (note in 1..8) {
				onChainTouch(8 - note + 16 - 8, velo != 0)
				onFunctionkeyTouch(8 - note + 16, velo != 0)
			}
			if (note in 10..80 && note % 10 == 0) {
				onChainTouch(note / 10 - 1 + 24 - 8, velo != 0)
				onFunctionkeyTouch(note / 10 - 1 + 24, velo != 0)
			}
		} else {
			onUnknownReceived(cmd, sig, note, velo)
		}
	}

	override fun sendPadLED(x: Int, y: Int, velo: Int) {
		sendSignal(25, -112, 10 * (8 - x) + y + 1, velo)
	}

	override fun sendChainLED(c: Int, velo: Int) {
		if (c in 0..7)
			sendFunctionkeyLED(c + 8, velo)
	}

	override fun sendFunctionkeyLED(f: Int, velo: Int) {
		if (f in 0..31)
			sendSignal(circleCode[f][0].toByte(), circleCode[f][1].toByte(), circleCode[f][2].toByte(), velo.toByte())
	}


	override fun sendClearLED() {
		for (i in 0..7)
			for (j in 0..7)
				sendPadLED(i, j, 0)
		for (i in 0..31)
			sendFunctionkeyLED(i, 0)
	}
}