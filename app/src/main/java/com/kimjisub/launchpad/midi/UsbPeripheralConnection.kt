package com.kimjisub.launchpad.midi

import android.content.Context

/** No pack, audio, USB host driver or screen dependencies. Call close when its screen leaves. */
class UsbPeripheralConnection internal constructor(
    access: PeripheralMidiAccess,
    gate: MidiTransportGate,
) : AutoCloseable {
    enum class Failure { UNAVAILABLE, DISCOVERY, BUSY, OPEN, INPUT_PORT, OUTPUT_PORT, SEND, RELEASE, CLOSE, OVERFLOW, REMOVED, TIMEOUT }
    sealed interface State {
        data object Idle : State
        data object Connecting : State
        data class Open(val lightsAvailable: Boolean, val generation: Long) : State
        data object Closing : State
        /** [attempt] differs between failures, so the same reason twice in a row is still a new state. */
        data class Failed(val reason: Failure, val attempt: Long) : State
    }
    companion object {
        fun create(context: Context): UsbPeripheralConnection = UsbPeripheralConnection(AndroidPeripheralMidiAccess(context), MidiConnection.transportGate)
    }

    val lights = PeripheralLights()
    private val transport = PeripheralMidiTransport(access, gate) { PeripheralPadProtocol(lights) }
    val state get() = transport.state
    val devices get() = transport.devices
    val discoveryFailure get() = transport.discoveryFailure
    val portsClosing get() = transport.portsClosing
    fun refresh() = transport.refresh()
    fun connect(device: PeripheralDevice, inputPort: Int, outputPort: Int?) = transport.connect(device,inputPort,outputPort)
    /** Keep the generation captured on pointer down, including for its eventual release. */
    fun send(change: PadChange, generation: Long): Boolean =
        Mk2Layout.message(change.index,change.pressed)?.let { transport.send(it, generation) } ?: false
    fun disconnect() = transport.disconnect()
    override fun close() = transport.close()
}
