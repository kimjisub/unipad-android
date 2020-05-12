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

	override fun getSignal(cmd: Int, sig: Int, note: Int, velo: Int) {
		if (cmd == 9) {
			val x = note / 16 + 1
			val y = note % 16 + 1
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

	override fun sendPadLed(x: Int, y: Int, velo: Int) {
		sendSignal(9, -112, x * 16 + y, LaunchpadColor.SCode[velo])
	}

	override fun sendChainLed(c: Int, velo: Int) {
		if (c in 0..7)
			sendFunctionkeyLed(c + 8, velo)
	}

	override fun sendFunctionkeyLed(f: Int, velo: Int) {
		if (f in 0..15)
			sendSignal(circleCode[f][0].toByte(), circleCode[f][1].toByte(), circleCode[f][2].toByte(), LaunchpadColor.SCode[velo].toByte())
	}

	override fun sendClearLed() {
		for (i in 0..7)
			for (j in 0..7)
				sendPadLed(i, j, 0)
		for (i in 0..15)
			sendFunctionkeyLed(i, 0)
	}
}