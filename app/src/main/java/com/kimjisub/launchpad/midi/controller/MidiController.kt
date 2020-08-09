package com.kimjisub.launchpad.midi.controller

abstract class MidiController {

	abstract fun onAttach()

	abstract fun onDetach()

	abstract fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int)

	abstract fun onFunctionkeyTouch(f: Int, upDown: Boolean)

	abstract fun onChainTouch(c: Int, upDown: Boolean)

	abstract fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velocity: Int)
}
