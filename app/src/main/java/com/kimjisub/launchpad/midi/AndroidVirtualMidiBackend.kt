package com.kimjisub.launchpad.midi

import android.content.Context
import android.content.pm.ServiceInfo
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceStatus
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean

/** Only virtual MIDI 1.0 devices; USB continues to use the existing USB host implementation. */
@Suppress("DEPRECATION")
internal class AndroidVirtualMidiBackend(context: Context) : VirtualMidiBackend {
	private val manager = context.applicationContext.getSystemService(Context.MIDI_SERVICE) as? MidiManager
	private val handler = Handler(Looper.getMainLooper())

	override suspend fun devices(): List<VirtualMidiDevice> = requireNotNull(manager) { "MIDI service unavailable" }
		.devices.filter { it.type == MidiDeviceInfo.TYPE_VIRTUAL }.map { info ->
			val properties = info.properties
			val provider = properties.getParcelable<ServiceInfo>("service_info")?.packageName.orEmpty()
			fun ports(type: Int) = info.ports.filter { it.type == type }.map { VirtualMidiPort(it.portNumber, it.name.orEmpty()) }
			VirtualMidiDevice(info.id, properties.getString(MidiDeviceInfo.PROPERTY_NAME).orEmpty(),
				properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT).orEmpty(), provider,
				ports(MidiDeviceInfo.PortInfo.TYPE_OUTPUT), ports(MidiDeviceInfo.PortInfo.TYPE_INPUT))
		}

	override suspend fun open(id: Int): VirtualMidiBackend.Device? {
		val service = manager ?: return null
		val info = service.devices.firstOrNull { it.id == id && it.type == MidiDeviceInfo.TYPE_VIRTUAL } ?: return null
		return suspendCancellableCoroutine { continuation ->
			service.openDevice(info, { device ->
				val opened = device?.let { Device(it, service, handler) }
				// Cancellation owns a late device, including cancellation during dispatch of resume.
				continuation.resume(opened) { _, value, _ -> try { value?.close() } catch (_: Exception) { } }
			}, handler)
		}
	}

	override fun watchRemoval(callback: (Int) -> Unit): AutoCloseable {
		val service = requireNotNull(manager) { "MIDI service unavailable" }
		val observer = object : MidiManager.DeviceCallback() {
			override fun onDeviceRemoved(device: MidiDeviceInfo) = callback(device.id)
		}
		service.registerDeviceCallback(observer, handler)
		return AutoCloseable { service.unregisterDeviceCallback(observer) }
	}

	private class Device(private val device: MidiDevice, private val manager: MidiManager, private val handler: Handler) : VirtualMidiBackend.Device {
		private var performancePort = -1

		// A static virtual device stays in the device list after its service dies. The public
		// callback registration supplies its current port status; refresh only this connection's
		// status, without discovering devices or reopening anything.
		override suspend fun isAlive(): Boolean = suspendCancellableCoroutine { continuation ->
			val completed = AtomicBoolean(false)
			val observer = object : MidiManager.DeviceCallback() {
				override fun onDeviceStatusChanged(status: MidiDeviceStatus) {
					if (status.deviceInfo.id == device.info.id && completed.compareAndSet(false, true)) {
						try { manager.unregisterDeviceCallback(this) } catch (_: RuntimeException) { }
						continuation.resume(status.getOutputPortOpenCount(performancePort) > 0) { _, _, _ -> }
					}
				}
			}
			manager.registerDeviceCallback(observer, handler)
			continuation.invokeOnCancellation {
				if (completed.compareAndSet(false, true)) {
					try { manager.unregisterDeviceCallback(observer) } catch (_: RuntimeException) { }
				}
			}
		}

		override fun openPerformancePort(number: Int, receiver: (ByteArray, Int, Int) -> Unit): VirtualMidiBackend.Port? {
			val port = device.openOutputPort(number) ?: return null
			val input = object : MidiReceiver() {
				override fun onSend(data: ByteArray, offset: Int, count: Int, timestamp: Long) = receiver(data, offset, count)
			}
			try { port.connect(input); performancePort = number } catch (e: Exception) { port.close(); throw e }
			return object : VirtualMidiBackend.Port {
				override fun send(bytes: ByteArray) = error("Performance port is receive-only")
				override fun close() { try { port.disconnect(input) } finally { port.close() } }
			}
		}
		override fun openLightPort(number: Int): VirtualMidiBackend.Port? {
			val port = device.openInputPort(number) ?: return null
			return object : VirtualMidiBackend.Port {
				override fun send(bytes: ByteArray) = port.send(bytes, 0, bytes.size)
				override fun close() = port.close()
			}
		}
		override fun close() = device.close()
	}
}
