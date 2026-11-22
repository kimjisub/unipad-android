package com.kimjisub.launchpad.midi.driver

import com.kimjisub.launchpad.midi.Mk2Layout

class LaunchpadMK2 : DriverRef() {

	override fun getSignal(cmd: Int, sig: Int, note: Int, velocity: Int) {
		if (cmd == 9) {
			val x = 9 - note / 10
			val y = note % 10
			// Notes 91..98 (x = 0) are the top row on some firmwares and would map to pad row -1
			// (Crashlytics f2a35391: ArrayIndexOutOfBounds index=-1 in PlayActivity.setLedUI).
			if (x in 1..8 && y in 1..8)
				onPadTouch(x - 1, y - 1, velocity != 0, velocity)
			else if (y == 9 && x in 1..8) {
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
		sendSignal(9, -112, Mk2Layout.padNote(x, y)!!, velocity)
	}

	override fun sendChainLed(c: Int, velocity: Int) {
		if (c in 0..7)
			sendFunctionKeyLed(c + 8, velocity)
	}

	override fun sendFunctionKeyLed(f: Int, velocity: Int) {
		if (f !in 0..15) return
		val address = Mk2Layout.address(64 + f)!!
		sendSignal(address.status shr 4, address.status.toByte().toInt(), address.number, velocity)
	}

	override fun sendClearLed() {
		for (i in 0..7)
			for (j in 0..7)
				sendPadLed(i, j, 0)
		for (i in 0..15) sendFunctionKeyLed(i, 0)
	}
}