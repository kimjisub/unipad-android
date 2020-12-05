package com.kimjisub.launchpad.midi.driver

class MasterKeyboard : DriverRef() {
	companion object;

	override fun getSignal(cmd: Int, sig: Int, note: Int, velocity: Int) {
		val x: Int
		val y: Int
		if (cmd == 9) {
			if (note in 36..67) {
				x = (67 - note) / 4 + 1
				y = 4 - (67 - note) % 4
				onPadTouch(x - 1, y - 1, velocity != 0, velocity)
			} else if (note in 68..99) {
				x = (99 - note) / 4 + 1
				y = 8 - (99 - note) % 4
				onPadTouch(x - 1, y - 1, velocity != 0, velocity)
			}
		} else if (velocity == 0) {
			if (note in 36..67) {
				x = (67 - note) / 4 + 1
				y = 4 - (67 - note) % 4
				onPadTouch(x - 1, y - 1, false, 0)
			} else if (note in 68..99) {
				x = (99 - note) / 4 + 1
				y = 8 - (99 - note) % 4
				onPadTouch(x - 1, y - 1, false, 0)
			}
		}
	}

	override fun sendPadLed(x: Int, y: Int, velocity: Int) {}
	override fun sendChainLed(c: Int, velocity: Int) {}
	override fun sendFunctionkeyLed(f: Int, velocity: Int) {}
	override fun sendClearLed() {}
}