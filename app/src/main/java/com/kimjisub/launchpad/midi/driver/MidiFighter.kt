package com.kimjisub.launchpad.midi.driver

class MidiFighter : DriverRef() {
	override fun getSignal(cmd: Int, sig: Int, note: Int, velocity: Int) {
		val x: Int
		val y: Int
		if (cmd == 9) {
			if (note in 36..67) {
				x = (67 - note) / 4 + 1
				y = 4 - (67 - note) % 4
				onPadTouch(x - 1, y - 1, true, velocity)
			} else if (note in 68..99) {
				x = (99 - note) / 4 + 1
				y = 8 - (99 - note) % 4
				onPadTouch(x - 1, y - 1, true, velocity)
			}
		} else if (cmd == 8) {
			if (note in 36..67) {
				x = (67 - note) / 4 + 1
				y = 4 - (67 - note) % 4
				onPadTouch(x - 1, y - 1, false, velocity)
			} else if (note in 68..99) {
				x = (99 - note) / 4 + 1
				y = 8 - (99 - note) % 4
				onPadTouch(x - 1, y - 1, false, velocity)
			}
		}
	}

	override fun sendPadLed(x: Int, y: Int, velocity: Int) {
		val padX = x + 1
		val padY = y + 1
		if (padY in 1..4) {
			sendSignal(9, -110, -4 * padX + padY + 67, velocity)
		} else if (padY in 5..8) {
			sendSignal(9, -110, -4 * padX + padY + 95, velocity)
		}
	}

	override fun sendChainLed(c: Int, velocity: Int) {}
	override fun sendFunctionKeyLed(f: Int, velocity: Int) {}
	override fun sendClearLed() {
		for (i in 0..7)
			for (j in 0..7)
				sendPadLed(i, j, 0)
	}
}