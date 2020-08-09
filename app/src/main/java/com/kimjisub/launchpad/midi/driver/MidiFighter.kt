package com.kimjisub.launchpad.midi.driver

class MidiFighter : DriverRef() {
	companion object {}
	override fun getSignal(cmd: Int, sig: Int, note: Int, velo: Int) {
		val x: Int
		val y: Int
		if (cmd == 9) {
			if (note in 36..67) {
				x = (67 - note) / 4 + 1
				y = 4 - (67 - note) % 4
				onPadTouch(x - 1, y - 1, true, velo)
			} else if (note in 68..99) {
				x = (99 - note) / 4 + 1
				y = 8 - (99 - note) % 4
				onPadTouch(x - 1, y - 1, true, velo)
			}
		} else if (cmd == 8) {
			if (note in 36..67) {
				x = (67 - note) / 4 + 1
				y = 4 - (67 - note) % 4
				onPadTouch(x - 1, y - 1, false, velo)
			} else if (note in 68..99) {
				x = (99 - note) / 4 + 1
				y = 8 - (99 - note) % 4
				onPadTouch(x - 1, y - 1, false, velo)
			}
		}
	}

	override fun sendPadLed(x: Int, y: Int, velo: Int) {
		var x = x
		var y = y
		x += 1
		y += 1
		if (y in 1..4)
			sendSignal(9, -110, -4 * x + y + 67, velo) else if (y in 5..8) sendSignal(9, -110, -4 * x + y + 95, velo)
	}

	override fun sendChainLed(c: Int, velo: Int) {}
	override fun sendFunctionkeyLed(f: Int, velo: Int) {}
	override fun sendClearLed() {
		for (i in 0..7)
			for (j in 0..7)
				sendPadLed(i, j, 0)
	}
}