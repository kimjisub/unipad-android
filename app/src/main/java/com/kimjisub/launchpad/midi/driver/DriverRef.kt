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
		fun onReceived(cmd: Int, sig: Int, note: Int, velocity: Int)
		fun onUnknownReceived(cmd: Int, sig: Int, note: Int, velocity: Int)

		fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int)
		fun onChainTouch(c: Int, upDown: Boolean)
		fun onFunctionkeyTouch(f: Int, upDown: Boolean)
	}

	////


	open fun getSignal(cmd: Int, sig: Int, note: Int, velocity: Int) {
		Log.midiDetail("onReceived($cmd, $sig, $note, $velocity)")
		onReceiveSignalListener?.onReceived(cmd, sig, note, velocity)
	}

	fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) {
		Log.midiDetail("onPadTouch($x, $y, $upDown, $velocity)")
		onReceiveSignalListener?.onPadTouch(x, y, upDown, velocity)
	}

	fun onChainTouch(c: Int, upDown: Boolean) {
		Log.midiDetail("onChainTouch($c, $upDown)")
		onReceiveSignalListener?.onChainTouch(c, upDown)
	}

	fun onFunctionKeyTouch(f: Int, upDown: Boolean) {
		Log.midiDetail("onFunctionKeyTouch($f, $upDown)")
		onReceiveSignalListener?.onFunctionkeyTouch(f, upDown)
	}

	fun onUnknownReceived(cmd: Int, sig: Int, note: Int, velocity: Int) {
		Log.midiDetail("onUnknownReceived($cmd, $sig, $note, $velocity)")
		onReceiveSignalListener?.onUnknownReceived(cmd, sig, note, velocity)
	}

	// OnSendSignalListener /////////////////////////////////////////////////////////////////////////////////////////

	private var onSendSignalListener: OnSendSignalListener? = null
	fun setOnSendSignalListener(listener: OnSendSignalListener?): DriverRef {
		onSendSignalListener = listener
		return this
	}

	interface OnSendSignalListener {
		fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte)
	}

	////

	internal fun sendSignal(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
		onSendSignalListener?.onSend(cmd, sig, note, velocity)
	}

	internal fun sendSignal(cmd: Int, sig: Int, note: Int, velocity: Int) {
		sendSignal(cmd.toByte(), sig.toByte(), note.toByte(), velocity.toByte())
	}

	abstract fun sendPadLed(x: Int, y: Int, velocity: Int)
	abstract fun sendChainLed(c: Int, velocity: Int)
	abstract fun sendFunctionkeyLed(f: Int, velocity: Int)
	abstract fun sendClearLed()
}