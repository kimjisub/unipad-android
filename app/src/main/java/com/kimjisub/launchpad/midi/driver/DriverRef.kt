package com.kimjisub.launchpad.midi.driver

import com.kimjisub.launchpad.midi.UsbMidiSysEx
import com.kimjisub.launchpad.tool.Log

abstract class DriverRef {
	companion object {
		/** Round buttons are function keys 0 until this: top, right, bottom and left rows of 8. */
		const val ROUND_BUTTON_COUNT = 32

		/** The logo LED (keyLED `l`), just past the round buttons as on iOS. Drivers without one ignore it. */
		const val LOGO_FUNCTION_KEY = ROUND_BUTTON_COUNT
	}

	// OnCycleListener

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

	// OnReceiveSignalListener

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
		fun onFunctionKeyTouch(f: Int, upDown: Boolean)
	}

	////


	open fun getSignal(cmd: Int, sig: Int, note: Int, velocity: Int) {
		onReceiveSignalListener?.onReceived(cmd, sig, note, velocity)
	}

	fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) {
		onReceiveSignalListener?.onPadTouch(x, y, upDown, velocity)
	}

	fun onChainTouch(c: Int, upDown: Boolean) {
		onReceiveSignalListener?.onChainTouch(c, upDown)
	}

	fun onFunctionKeyTouch(f: Int, upDown: Boolean) {
		onReceiveSignalListener?.onFunctionKeyTouch(f, upDown)
	}

	fun onUnknownReceived(cmd: Int, sig: Int, note: Int, velocity: Int) {
		onReceiveSignalListener?.onUnknownReceived(cmd, sig, note, velocity)
	}

	// OnSendSignalListener

	private var onSendSignalListener: OnSendSignalListener? = null
	fun setOnSendSignalListener(listener: OnSendSignalListener?): DriverRef {
		onSendSignalListener = listener
		return this
	}

	interface OnSendSignalListener {
		fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte)
		fun onSendRaw(messages: List<ByteArray>, cableNumber: Int)

		/** 4-byte USB-MIDI packets that go out in order with [onSend] and are never split apart. */
		fun onSendPackets(packets: ByteArray) {
			for (i in packets.indices step 4)
				onSend(packets[i], packets[i + 1], packets[i + 2], packets[i + 3])
		}
	}

	////

	internal fun sendSignal(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
		onSendSignalListener?.onSend(cmd, sig, note, velocity)
	}

	internal fun sendSignal(cmd: Int, sig: Int, note: Int, velocity: Int) {
		// note/velocity are MIDI data bytes: a pack LED code of 200 went out as 0xC8, a status byte
		// in a data position (web clamps the same way).
		sendSignal(cmd.toByte(), sig.toByte(), note.coerceIn(0, 127).toByte(), velocity.coerceIn(0, 127).toByte())
	}

	internal fun sendRawSignal(bytes: ByteArray, cableNumber: Int = 0) {
		onSendSignalListener?.onSendRaw(listOf(bytes), cableNumber)
	}

	internal fun sendRawSignals(messages: List<ByteArray>, cableNumber: Int = 0) {
		onSendSignalListener?.onSendRaw(messages, cableNumber)
	}

	/**
	 * Sends a SysEx through the same ordered queue as [sendSignal], so it keeps its place among LED
	 * messages. [sendRawSignal] goes out on its own and suits only the init messages.
	 */
	internal fun sendSysExInOrder(bytes: ByteArray, cableNumber: Int = 0) {
		onSendSignalListener?.onSendPackets(UsbMidiSysEx.encode(bytes, cableNumber))
	}

	open fun initialize() {}

	/** Returns SysEx messages and cable number for initialization, or null if none needed. */
	open fun getInitSysEx(): Pair<List<ByteArray>, Int>? = null

	abstract fun sendPadLed(x: Int, y: Int, velocity: Int)
	abstract fun sendChainLed(c: Int, velocity: Int)
	abstract fun sendFunctionKeyLed(f: Int, velocity: Int)
	abstract fun sendClearLed()
}