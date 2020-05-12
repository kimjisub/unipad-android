package com.kimjisub.launchpad.midi.driver

class Noting : DriverRef() {
	override fun getSignal(cmd: Int, sig: Int, note: Int, velo: Int) {}
	override fun sendPadLed(x: Int, y: Int, velo: Int) {}
	override fun sendChainLed(c: Int, velo: Int) {}
	override fun sendFunctionkeyLed(f: Int, velo: Int) {}
	override fun sendClearLed() {}
}