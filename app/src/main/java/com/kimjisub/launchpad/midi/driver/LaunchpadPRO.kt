package com.kimjisub.launchpad.midi.driver

import com.kimjisub.manager.Log

class LaunchpadPRO : DriverRef() {
	companion object {
		internal val circleCode = arrayOf(
			intArrayOf(11, -80, 91),
			intArrayOf(11, -80, 92),
			intArrayOf(11, -80, 93),
			intArrayOf(11, -80, 94),
			intArrayOf(11, -80, 95),
			intArrayOf(11, -80, 96),
			intArrayOf(11, -80, 97),
			intArrayOf(11, -80, 98),
			intArrayOf(11, -80, 89),
			intArrayOf(11, -80, 79),
			intArrayOf(11, -80, 69),
			intArrayOf(11, -80, 59),
			intArrayOf(11, -80, 49),
			intArrayOf(11, -80, 39),
			intArrayOf(11, -80, 29),
			intArrayOf(11, -80, 19),
			intArrayOf(11, -80, 8),
			intArrayOf(11, -80, 7),
			intArrayOf(11, -80, 6),
			intArrayOf(11, -80, 5),
			intArrayOf(11, -80, 4),
			intArrayOf(11, -80, 3),
			intArrayOf(11, -80, 2),
			intArrayOf(11, -80, 1),
			intArrayOf(11, -80, 10),
			intArrayOf(11, -80, 20),
			intArrayOf(11, -80, 30),
			intArrayOf(11, -80, 40),
			intArrayOf(11, -80, 50),
			intArrayOf(11, -80, 60),
			intArrayOf(11, -80, 70),
			intArrayOf(11, -80, 80)
		)
	}

	override fun getSignal(cmd: Int, sig: Int, note: Int, velo: Int) {
		if (cmd == 9) {
			val x = 9 - note / 10
			val y = note % 10
			if (y in 1..8)
				onPadTouch(x - 1, y - 1, velo != 0, velo)
		}
		if (cmd == 11 && sig == -80) {
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
			if (cmd == 7 && sig == 46 && note == 0 && velo == -9)
				Log.midiDetail("PRO > Live Mode")
			else if (cmd == 23 && sig == 47 && note == 0 && velo == -9)
				Log.midiDetail("PRO > Note Mode")
			else if (cmd == 23 && sig == 47 && note == 1 && velo == -9)
				Log.midiDetail("PRO > Drum Mode")
			else if (cmd == 23 && sig == 47 && note == 2 && velo == -9)
				Log.midiDetail("PRO > Fade Mode")
			else if (cmd == 23 && sig == 47 && note == 3 && velo == -9)
				Log.midiDetail("PRO > Programmer Mode")
		}
	}

	override fun sendPadLED(x: Int, y: Int, velo: Int) {
		sendSignal(9, -112, 10 * (8 - x) + y + 1, velo)
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