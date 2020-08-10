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

	override fun getSignal(cmd: Int, sig: Int, note: Int, velocity: Int) {
		if (cmd == 25) {
			val x = 9 - note / 10
			val y = note % 10
			if (y in 1..8)
				onPadTouch(x - 1, y - 1, velocity != 0, velocity)
		} else if (cmd == 27 && sig == -80) {
			if (note in 91..98) {
				onFunctionKeyTouch(note - 91, velocity != 0)
			}
			if (note in 19..89 && note % 10 == 9) {
				val c = 9 - note / 10 - 1
				onChainTouch(c, velocity != 0)
				onFunctionKeyTouch(c + 8, velocity != 0)
			}
			if (note in 1..8) {
				onChainTouch(8 - note + 16 - 8, velocity != 0)
				onFunctionKeyTouch(8 - note + 16, velocity != 0)
			}
			if (note in 10..80 && note % 10 == 0) {
				onChainTouch(note / 10 - 1 + 24 - 8, velocity != 0)
				onFunctionKeyTouch(note / 10 - 1 + 24, velocity != 0)
			}
		} else {
			onUnknownReceived(cmd, sig, note, velocity)
		}
	}

	override fun sendPadLed(x: Int, y: Int, velocity: Int) {
		sendSignal(25, -112, 10 * (8 - x) + y + 1, velocity)
	}

	override fun sendChainLed(c: Int, velocity: Int) {
		if (c in 0..7)
			sendFunctionkeyLed(c + 8, velocity)
	}

	override fun sendFunctionkeyLed(f: Int, velocity: Int) {
		if (f in 0..31)
			sendSignal(circleCode[f][0].toByte(), circleCode[f][1].toByte(), circleCode[f][2].toByte(), velocity.toByte())
	}


	override fun sendClearLed() {
		for (i in 0..7)
			for (j in 0..7)
				sendPadLed(i, j, 0)
		for (i in 0..31)
			sendFunctionkeyLed(i, 0)
	}
}