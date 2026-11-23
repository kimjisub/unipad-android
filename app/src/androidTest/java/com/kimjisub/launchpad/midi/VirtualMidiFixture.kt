package com.kimjisub.launchpad.midi

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * The test-only MIDI service, running in its own process and reached through Android's real MIDI
 * service: discovery, openDevice and its ports. Its content provider reads what reached it and
 * makes it send, as the other app on the phone would.
 */
internal class VirtualMidiFixture(private val context: Context) {
	private val manager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
	private val uri = Uri.parse("content://com.kimjisub.launchpad.dev.test.midi")

	fun call(method: String, extras: Bundle = Bundle()): Bundle = context.contentResolver.call(uri, method, "selected", extras)!!
	fun send(vararg bytes: Int) = send(bytes.map { it.toByte() }.toByteArray())
	fun send(bytes: ByteArray, port: Int = 1) { call("send", Bundle().apply { putByteArray("bytes", bytes); putInt("port", port) }) }
	/** Three-byte messages received on the selected light port, oldest first. */
	fun lights(): List<List<Byte>> = call("read").getByteArray("lights0")!!.toList().chunked(3)

	fun <T> main(block: () -> T): T = runBlocking { withContext(Dispatchers.Main) { block() } }
	fun await(message: String, condition: () -> Boolean) = runBlocking {
		try { withTimeout(10_000) { while (!condition()) delay(10) } }
		catch (error: TimeoutCancellationException) { throw AssertionError(message, error) }
		assertTrue(message, condition())
	}

	@Suppress("DEPRECATION")
	fun info(serviceName: String): MidiDeviceInfo {
		var found: MidiDeviceInfo? = null
		await("Test MIDI service was not registered") {
			found = manager.devices.firstOrNull { it.properties.getParcelable<ServiceInfo>("service_info")?.name?.endsWith(serviceName) == true }
			found != null
		}
		return found!!
	}

	fun connection(): VirtualMidiConnection {
		main { MidiConnection.initConnection(Intent(), context.getSystemService(Context.USB_SERVICE) as UsbManager, context) }
		return MidiConnection.virtualConnection(context)
	}

	/** The selected fixture device: performance port 1, light port [lights]. */
	fun selection(connection: VirtualMidiConnection, lights: Int? = 0): VirtualMidiSelection {
		val id = info(".MidiFixtureService").id
		val descriptor = runBlocking { connection.devices().getOrThrow().single { it.id == id } }
		assertTrue(descriptor.recommendsMatrix)
		assertEquals(2, descriptor.performancePorts.size)
		assertEquals(2, descriptor.lightPorts.size)
		return VirtualMidiSelection(descriptor, 1, lights)
	}

	fun close(connection: VirtualMidiConnection) {
		main { connection.disconnect(); connection.disconnect() }
		await("Virtual resources still own the USB gate") { MidiConnection.transportGate.acquireUsb()?.let { it.close(); true } == true }
		assertNull(MidiConnection.connectedDevice)
	}
}
