package com.kimjisub.launchpad.midi.driver

class Noting : DriverRef() {
	override fun getSignal(cmd: Int, sig: Int, note: Int, velocity: Int) {}
	override fun sendPadLed(x: Int, y: Int, velocity: Int) {}
	override fun sendChainLed(c: Int, velocity: Int) {}
	override fun sendFunctionkeyLed(f: Int, velocity: Int) {}
	override fun sendClearLed() {}
}