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

	// OnReceiveSignalListener /////////////////////////////////////////////////////////////////////////////////////////

	private var onReceiveSignalListener: OnReceiveSignalListener? = null
	fun setOnGetSignalListener(listener: OnReceiveSignalListener?): DriverRef {
		onReceiveSignalListener = listener
		return this
	}

	interface OnReceiveSignalListener {
		fun onReceived(cmd: Int, sig: Int, note: Int, velo: Int)
		fun onUnknownReceived(cmd: Int, sig: Int, note: Int, velo: Int)

		fun onPadTouch(x: Int, y: Int, upDown: Boolean, velo: Int)
		fun onChainTouch(c: Int, upDown: Boolean)
		fun onFunctionkeyTouch(f: Int, upDown: Boolean)
	}

	////


	open fun getSignal(cmd: Int, sig: Int, note: Int, velo: Int) {
		Log.midiDetail("onReceived($cmd, $sig, $note, $velo)")
		onReceiveSignalListener?.onReceived(cmd, sig, note, velo)
	}

	fun onPadTouch(x: Int, y: Int, upDown: Boolean, velo: Int) {
		Log.midiDetail("onPadTouch($x, $y, $upDown, $velo)")
		onReceiveSignalListener?.onPadTouch(x, y, upDown, velo)
	}

	fun onChainTouch(c: Int, upDown: Boolean) {
		Log.midiDetail("onChainTouch($c, $upDown)")
		onReceiveSignalListener?.onChainTouch(c, upDown)
	}

	fun onFunctionkeyTouch(f: Int, upDown: Boolean) {
		Log.midiDetail("onFunctionkeyTouch($f, $upDown)")
		onReceiveSignalListener?.onFunctionkeyTouch(f, upDown)
	}

	fun onUnknownReceived(cmd: Int, sig: Int, note: Int, velo: Int) {
		Log.midiDetail("onUnknownReceived($cmd, $sig, $note, $velo)")
		onReceiveSignalListener?.onUnknownReceived(cmd, sig, note, velo)
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