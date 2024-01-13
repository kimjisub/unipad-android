package com.kimjisub.launchpad.midi.driver

class Matrix : DriverRef() {
	companion object {
		internal val circleCode = arrayOf(
			intArrayOf(9, -111, 28),
			intArrayOf(9, -111, 29),
			intArrayOf(9, -111, 30),
			intArrayOf(9, -111, 31),
			intArrayOf(9, -111, 32),
			intArrayOf(9, -111, 33),
			intArrayOf(9, -111, 34),
			intArrayOf(9, -111, 35),
			intArrayOf(9, -111, 100),
			intArrayOf(9, -111, 101),
			intArrayOf(9, -111, 102),
			intArrayOf(9, -111, 103),
			intArrayOf(9, -111, 104),
			intArrayOf(9, -111, 105),
			intArrayOf(9, -111, 106),
			intArrayOf(9, -111, 107),
			intArrayOf(9, -111, 123),
			intArrayOf(9, -111, 122),
			intArrayOf(9, -111, 121),
			intArrayOf(9, -111, 120),
			intArrayOf(9, -111, 119),
			intArrayOf(9, -111, 118),
			intArrayOf(9, -111, 117),
			intArrayOf(9, -111, 116),
			intArrayOf(9, -111, 115),
			intArrayOf(9, -111, 114),
			intArrayOf(9, -111, 113),
			intArrayOf(9, -111, 112),
			intArrayOf(9, -111, 111),
			intArrayOf(9, -111, 110),
			intArrayOf(9, -111, 109),
			intArrayOf(9, -111, 108)
		)
	}

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
			} else if (note in 100..107) {
				val c = note - 100
				onChainTouch(c, velocity != 0)
				onFunctionKeyTouch(c + 8, velocity != 0)
			} else if (note in 108..115) {
				val c = 8 - (note - 108) + 8
				onFunctionKeyTouch(c + 8, velocity != 0)
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
		var x = x
		var y = y
		x += 1
		y += 1
		if (y in 1..4)
			sendSignal(9, -111, -4 * x + y + 67, velocity) else if (y in 5..8) sendSignal(
			9,
			-111,
			-4 * x + y + 95,
			velocity
		)
	}

	override fun sendChainLed(c: Int, velocity: Int) {
		if (c in 0..7)
			sendFunctionkeyLed(c + 8, velocity)
	}

	override fun sendFunctionkeyLed(f: Int, velocity: Int) {
		if (f in 0..31)
			sendSignal(
				circleCode[f][0].toByte(),
				circleCode[f][1].toByte(),
				circleCode[f][2].toByte(),
				velocity.toByte()
			)
	}

	override fun sendClearLed() {
		for (i in 0..7)
			for (j in 0..7)
				sendPadLed(i, j, 0)
	}
}