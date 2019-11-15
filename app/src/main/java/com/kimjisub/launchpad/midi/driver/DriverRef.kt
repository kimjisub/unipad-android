package com.kimjisub.launchpad.midi.driver

import com.kimjisub.manager.Log

abstract class DriverRef {

	// OnCycleListener /////////////////////////////////////////////////////////////////////////////////////////

	private var onCycleListener: OnCycleListener? = null
	fun setOnCycleListener(listener: OnCycleListener?): DriverRef {
		onCycleListener = listener
		return this
	}

	interface OnCycleListener {
		fun onConnected()
		fun onDisconnected()
	}

	////


	fun onConnected() = onCycleListener?.onConnected()

	fun onDisconnected() = onCycleListener?.onDisconnected()


	// OnGetSignalListener /////////////////////////////////////////////////////////////////////////////////////////

	private var onGetSignalListener: OnGetSignalListener? = null
	fun setOnGetSignalListener(listener: OnGetSignalListener?): DriverRef {
		onGetSignalListener = listener
		return this
	}

	interface OnGetSignalListener {
		fun onPadTouch(x: Int, y: Int, upDown: Boolean, velo: Int)
		fun onFunctionkeyTouch(f: Int, upDown: Boolean)
		fun onChainTouch(c: Int, upDown: Boolean)
		fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velo: Int)
	}

	////


	abstract fun getSignal(cmd: Int, sig: Int, note: Int, velo: Int)
	internal fun onPadTouch(x: Int, y: Int, upDown: Boolean, velo: Int) {
		Log.midiDetail("onPadTouch($x, $y, $upDown, $velo)")
		onGetSignalListener?.onPadTouch(x, y, upDown, velo)
	}

	internal fun onFunctionkeyTouch(f: Int, upDown: Boolean) {
		Log.midiDetail("onFunctionkeyTouch($f, $upDown)")
		onGetSignalListener?.onFunctionkeyTouch(f, upDown)
	}

	internal fun onChainTouch(c: Int, upDown: Boolean) {
		Log.midiDetail("onChainTouch($c, $upDown)")
		onGetSignalListener?.onChainTouch(c, upDown)
	}

	internal fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velo: Int) {
		Log.midiDetail("onUnknownEvent($cmd, $sig, $note, $velo)")
		onGetSignalListener?.onUnknownEvent(cmd, sig, note, velo)
	}

	// OnSendSignalListener /////////////////////////////////////////////////////////////////////////////////////////

	private var onSendSignalListener: OnSendSignalListener? = null
	fun setOnSendSignalListener(listener: OnSendSignalListener?): DriverRef {
		onSendSignalListener = listener
		return this
	}

	interface OnSendSignalListener {
		fun onSend(cmd: Byte, sig: Byte, note: Byte, velo: Byte)
	}

	////

	internal fun sendSignal(cmd: Byte, sig: Byte, note: Byte, velo: Byte) {
		onSendSignalListener?.onSend(cmd, sig, note, velo)
	}

	internal fun sendSignal(cmd: Int, sig: Int, note: Int, velo: Int) {
		sendSignal(cmd.toByte(), sig.toByte(), note.toByte(), velo.toByte())
	}

	abstract fun sendPadLED(x: Int, y: Int, velo: Int)
	abstract fun sendChainLED(c: Int, velo: Int)
	abstract fun sendFunctionkeyLED(f: Int, velo: Int)
	abstract fun sendClearLED()
}