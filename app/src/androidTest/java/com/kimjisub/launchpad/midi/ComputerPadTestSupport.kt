package com.kimjisub.launchpad.midi

import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import com.kimjisub.launchpad.ui.compose.PadGridGeometry

/** The centre of a computer pad cell, inside the pad grid being touched. */
internal fun TouchInjectionScope.cell(index: Int): Offset {
	val (row, column) = PadGridGeometry.position(index)
	val pitch = width.toFloat() / PadGridGeometry.CELLS
	return Offset((column + 0.5f) * pitch, (row + 0.5f) * pitch)
}

/** The test peer offered as if the system had listed it as a USB endpoint. [sender] may wrap the press port. */
internal class PeerAccess(
	private val manager: MidiManager,
	private val info: MidiDeviceInfo,
	private var defer: ((() -> Unit) -> Unit)? = null,
	private val onClosed: () -> Unit = {},
	private val sender: (PeripheralMidiAccess.Sender) -> PeripheralMidiAccess.Sender = { it },
) : PeripheralMidiAccess {
	override fun devices() = listOf(PeripheralDevice(info.id, "test peer", listOf(PeripheralDevice.Port(0, "press")), listOf(PeripheralDevice.Port(0, "lights"))))
	@Suppress("DEPRECATION")
	override fun watch(changed: () -> Unit, removed: (Int) -> Unit): AutoCloseable {
		val callback = object : MidiManager.DeviceCallback() {
			override fun onDeviceRemoved(device: MidiDeviceInfo) { removed(device.id) }
		}
		manager.registerDeviceCallback(callback, Handler(Looper.getMainLooper()))
		return AutoCloseable { manager.unregisterDeviceCallback(callback) }
	}
	override fun open(id: Int, completed: (PeripheralMidiAccess.Device?) -> Unit) {
		manager.openDevice(info, { device ->
			val ports = device?.let(::AndroidMidiPorts)?.let { ports ->
				object : PeripheralMidiAccess.Device by ports {
					override fun input(number: Int) = ports.input(number)?.let(sender)
					override fun close() { try { ports.close() } finally { onClosed() } }
				}
			}
			val delay = defer
			defer = null
			if (delay == null) completed(ports) else delay { completed(ports) }
		}, Handler(Looper.getMainLooper()))
	}
}
