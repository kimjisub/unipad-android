package com.kimjisub.launchpad.midi.driver

class MasterKeyboard : DriverRef() {
	override fun getSignal(cmd: Int, sig: Int, note: Int, velo: Int) {
		val x: Int
		val y: Int
		if (cmd == 9) {
			if (note in 36..67) {
				x = (67 - note) / 4 + 1
				y = 4 - (67 - note) % 4
				onPadTouch(x - 1, y - 1, velo != 0, velo)
			} else if (note in 68..99) {
				x = (99 - note) / 4 + 1
				y = 8 - (99 - note) % 4
				onPadTouch(x - 1, y - 1, velo != 0, velo)
			}
		} else if (velo == 0) {
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

	override fun sendPadLED(x: Int, y: Int, velo: Int) {}
	override fun sendChainLED(c: Int, velo: Int) {}
	override fun sendFunctionkeyLED(f: Int, velo: Int) {}
	override fun sendClearLED() {}
}