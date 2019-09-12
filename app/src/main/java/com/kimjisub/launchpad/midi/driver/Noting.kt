package com.kimjisub.launchpad.midi.driver

class Noting : DriverRef() {
	override fun getSignal(cmd: Int, sig: Int, note: Int, velo: Int) {}
	override fun sendPadLED(x: Int, y: Int, velo: Int) {}
	override fun sendChainLED(c: Int, velo: Int) {}
	override fun sendFunctionkeyLED(f: Int, velo: Int) {}
	override fun sendClearLED() {}
}