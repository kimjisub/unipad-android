package com.kimjisub.launchpad.midi

import android.content.Context

/** No pack, audio, USB host driver or screen dependencies. Call close when its screen leaves. */
class UsbPeripheralConnection internal constructor(access: PeripheralMidiAccess) : AutoCloseable {
    enum class Failure { UNAVAILABLE, DISCOVERY, BUSY, OPEN, INPUT_PORT, SEND, OVERFLOW, REMOVED, TIMEOUT }
    sealed interface State {
        data object Idle : State
        data object Connecting : State
        data class Open(val lightsAvailable: Boolean, val generation: Long) : State
        data object Closing : State
        data class Failed(val reason: Failure) : State
    }
    companion object {
        fun create(context: Context): UsbPeripheralConnection = UsbPeripheralConnection(AndroidPeripheralMidiAccess(context))
    }

    val lights = PeripheralLights()
    private val transport = PeripheralMidiTransport(access) { PeripheralPadProtocol(lights) }
    val state get() = transport.state
    val devices get() = transport.devices
    val discoveryFailure get() = transport.discoveryFailure
    fun refresh() = transport.refresh()
    fun connect(device: PeripheralDevice, inputPort: Int, outputPort: Int?) = transport.connect(device,inputPort,outputPort)
    /** Keep the generation captured on pointer down, including for its eventual release. */
    fun send(change: PadChange, generation: Long): Boolean =
        Mk2Layout.message(change.index,change.pressed)?.let { transport.send(it, generation) } ?: false
    fun disconnect() = transport.disconnect()
    override fun close() = transport.close()
}
