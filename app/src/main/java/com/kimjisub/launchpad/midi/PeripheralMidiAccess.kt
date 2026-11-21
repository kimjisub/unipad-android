package com.kimjisub.launchpad.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.ChecksSdkIntAtLeast
import java.util.concurrent.Executor

/** Port names describe the system device, so its input is our outgoing button stream. */
data class PeripheralDevice(val id: Int, val name: String, val inputs: List<Port>, val outputs: List<Port>) {
    data class Port(val number: Int, val name: String)
}

internal interface PeripheralMidiAccess {
    fun devices(): List<PeripheralDevice>
    fun watch(changed: () -> Unit, removed: (Int) -> Unit): AutoCloseable
    fun open(id: Int, completed: (Device?) -> Unit)
    interface Device : AutoCloseable {
        fun input(number: Int): Sender?
        fun output(number: Int, receive: (ByteArray, Int, Int) -> Unit): AutoCloseable?
    }
    interface Sender : AutoCloseable { fun send(bytes: ByteArray) }
}

internal class AndroidPeripheralMidiAccess(context: Context, private val sdk: Int = Build.VERSION.SDK_INT) : PeripheralMidiAccess {
    private val manager = try { context.getSystemService(Context.MIDI_SERVICE) as? MidiManager }
        catch (_: RuntimeException) { null }
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        fun isPeripheral(info: MidiDeviceInfo): Boolean =
            info.type == MidiDeviceInfo.TYPE_USB && !info.properties.containsKey(MidiDeviceInfo.PROPERTY_USB_DEVICE)
    }

    @ChecksSdkIntAtLeast(api = 33)
    private fun hasTransportApi(): Boolean = sdk >= 33

    @Suppress("DEPRECATION")
    internal fun infos(): List<MidiDeviceInfo> {
        val midi = manager ?: throw IllegalStateException("MIDI unavailable")
        return if (hasTransportApi())
            midi.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
        else midi.devices.toList()
    }

    override fun devices(): List<PeripheralDevice> = infos().filter(::isPeripheral).map { info ->
        fun ports(type: Int) = info.ports.filter { it.type == type }.map { PeripheralDevice.Port(it.portNumber, it.name) }
        PeripheralDevice(info.id, info.properties.getString(MidiDeviceInfo.PROPERTY_NAME).orEmpty(),
            ports(MidiDeviceInfo.PortInfo.TYPE_INPUT), ports(MidiDeviceInfo.PortInfo.TYPE_OUTPUT))
    }

    @Suppress("DEPRECATION")
    override fun watch(changed: () -> Unit, removed: (Int) -> Unit): AutoCloseable {
        val midi = manager ?: throw IllegalStateException("MIDI unavailable")
        val callback = object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(info: MidiDeviceInfo) { changed() }
            override fun onDeviceRemoved(info: MidiDeviceInfo) { removed(info.id); changed() }
        }
        if (hasTransportApi())
            midi.registerDeviceCallback(MidiManager.TRANSPORT_MIDI_BYTE_STREAM, Executor { handler.post(it) }, callback)
        else midi.registerDeviceCallback(callback, handler)
        return AutoCloseable { midi.unregisterDeviceCallback(callback) }
    }

    override fun open(id: Int, completed: (PeripheralMidiAccess.Device?) -> Unit) {
        val midi = manager ?: throw IllegalStateException("MIDI unavailable")
        val info = infos().firstOrNull { it.id == id && isPeripheral(it) }
            ?: throw IllegalStateException("Endpoint removed")
        midi.openDevice(info, { device -> completed(device?.let(::AndroidMidiPorts)) }, handler)
    }
}

/** Also used by device tests with a virtual peer; production discovery never accepts that peer. */
internal class AndroidMidiPorts(private val device: MidiDevice) : PeripheralMidiAccess.Device {
    override fun input(number: Int): PeripheralMidiAccess.Sender? {
        val port = device.openInputPort(number) ?: return null
        return object : PeripheralMidiAccess.Sender {
            override fun send(bytes: ByteArray) { port.send(bytes, 0, bytes.size) }
            override fun close() { port.close() }
        }
    }
    override fun output(number: Int, receive: (ByteArray, Int, Int) -> Unit): AutoCloseable? {
        val port = device.openOutputPort(number) ?: return null
        val receiver = object : MidiReceiver() {
            override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) {
                receive(data, offset, count)
            }
        }
        try { port.connect(receiver) } catch (e: Exception) { try { port.close() } catch (_: Exception) { }; throw e }
        return AutoCloseable { try { port.disconnect(receiver) } finally { port.close() } }
    }
    override fun close() { device.close() }
}
