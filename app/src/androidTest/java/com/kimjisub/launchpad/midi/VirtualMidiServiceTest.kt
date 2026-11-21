package com.kimjisub.launchpad.midi

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.usb.UsbManager
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.midi.driver.Matrix
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.io.File

/** Android service registration, openDevice and real ports; never substitutes an in-process driver. */
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class VirtualMidiServiceTest : com.kimjisub.launchpad.basefeatures.PlaybackScreenTest() {
	private val context = InstrumentationRegistry.getInstrumentation().targetContext
	private val manager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
	private val uri = Uri.parse("content://com.kimjisub.launchpad.dev.test.midi")
	private fun call(method: String, extras: Bundle = Bundle()): Bundle = context.contentResolver.call(uri, method, "selected", extras)!!
	private fun send(vararg bytes: Int) = send(bytes.map { it.toByte() }.toByteArray())
	private fun send(bytes: ByteArray, port: Int = 1) { call("send", Bundle().apply { putByteArray("bytes", bytes); putInt("port",port) }) }
	private fun <T> main(block: () -> T): T = runBlocking { withContext(Dispatchers.Main) { block() } }
	private fun await(message: String, condition: () -> Boolean) = runBlocking {
		try { withTimeout(10_000) { while (!condition()) delay(10) } }
		catch (error: TimeoutCancellationException) { throw AssertionError(message, error) }
		assertTrue(message, condition())
	}
	private fun info(serviceName: String): MidiDeviceInfo {
		var found: MidiDeviceInfo? = null
		await("Test MIDI service was not registered") {
			found = manager.devices.firstOrNull { it.properties.getParcelable<ServiceInfo>("service_info")?.name?.endsWith(serviceName) == true }
			found != null
		}
		return found!!
	}
	private fun connection(): VirtualMidiConnection {
		main { MidiConnection.initConnection(Intent(), context.getSystemService(Context.USB_SERVICE) as UsbManager, context) }
		return MidiConnection.virtualConnection(context)
	}
	private fun selection(connection: VirtualMidiConnection, lights: Int? = 0): VirtualMidiSelection {
		val id = info(".MidiFixtureService").id
		val descriptor = runBlocking { connection.devices().getOrThrow().single { it.id == id } }
		assertTrue(descriptor.recommendsMatrix)
		assertEquals(2, descriptor.performancePorts.size)
		assertEquals(2, descriptor.lightPorts.size)
		return VirtualMidiSelection(descriptor, 1, lights)
	}
	private fun close(connection: VirtualMidiConnection) {
		main { connection.disconnect(); connection.disconnect() }
		await("Virtual resources still own the USB gate") { MidiConnection.transportGate.acquireUsb()?.let { it.close(); true } == true }
		assertNull(MidiConnection.connectedDevice)
	}

	@Test fun secondCableLayoutsUseRealPortsAndIgnoreDriverChangesUntilDisconnected() {
		val connection = connection()
		val selected = selection(connection)
		val pads = Collections.synchronizedList(mutableListOf<Boolean>())
		main {
			MidiConnection.controller = object : MidiController() {
				override fun onAttach() { }
				override fun onDetach() { }
				override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) {
					assertEquals(0, x); assertEquals(0, y); pads.add(upDown)
				}
				override fun onChainTouch(c: Int, upDown: Boolean) { }
				override fun onFunctionKeyTouch(f: Int, upDown: Boolean) { }
				override fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velocity: Int) { }
			}
		}
		try {
			for ((driver, note) in listOf(
				com.kimjisub.launchpad.midi.driver.LaunchpadX() to 81,
				com.kimjisub.launchpad.midi.driver.LaunchpadMiniMK3() to 81,
				com.kimjisub.launchpad.midi.driver.LaunchpadCoreFW() to 64,
			)) {
				pads.clear()
				main { assertTrue(connection.connect(selected, driver)) }
				try {
					await("Layout did not connect: ${driver.javaClass.simpleName}") { main { connection.state is VirtualMidiConnection.State.Connected } }
					main { MidiConnection.driver = Matrix(); assertSame(driver, MidiConnection.driver) }
					send(0x90, note, 100, 0x80, note, 64, 0x90, note, 0)
					await("Layout press or releases missing: ${driver.javaClass.simpleName}") { pads.size == 3 }
					assertEquals(listOf(true, false, false), pads.toList())
					// Wait for the initialization queue before clearing the peer's recording.
					main { driver.sendPadLed(0, 0, 99) }
					val marker = byteArrayOf(0x90.toByte(), note.toByte(), 99)
					await("Light marker missing") { call("read").getByteArray("lights0")!!.takeLast(3) == marker.toList() }
					call("clear")
					main { driver.sendPadLed(0, 0, 100); driver.sendPadLed(0, 0, 0) }
					await("Lights missing after driver pick") { call("read").getByteArray("lights0")!!.size >= 6 }
					assertArrayEquals(byteArrayOf(0x90.toByte(), note.toByte(), 100, 0x90.toByte(), note.toByte(), 0), call("read").getByteArray("lights0"))
				} finally { close(connection) }
			}
		} finally { main { MidiConnection.controller = null } }
	}

	@Test fun actualServiceInputRequestsPackSoundAndReceivesThePacksLightBytes() {
		screen.openPlay()
		val connection = connection()
		val selected = selection(connection)
		main { assertTrue(connection.connect(selected, Matrix())) }
		try {
			await("Service did not attach to the play controller") { main { connection.state is VirtualMidiConnection.State.Connected } }
			call("clear")
			// Matrix note 64 is the top-left pad of the same pack used by the screen tests.
			send(0x91,64,127)
			screen.await("Real service input did not request the pack sound") { audio.plays.size == 1 }
			assertEquals(audio.loaded.getValue("silence.wav"),audio.plays.single())
			fun lightValues() = call("read").getByteArray("lights0")!!.toList().chunked(3)
			val on = listOf(0x91.toByte(),64.toByte(),5.toByte())
			val off = listOf(0x91.toByte(),64.toByte(),0.toByte())
			screen.await("Pack light did not arrive at the peer service") { lightValues().contains(on) }
			val onIndex = lightValues().indexOf(on)
			send(0x81,64,64)
			screen.await("Pack light-off did not follow light-on at the peer") { lightValues().drop(onIndex + 1).contains(off) }
			assertEquals("One press/release must request the sound exactly once", 1, audio.plays.size)
			screen.capture("actual-midi-service-pack-roundtrip")
		} finally { close(connection) }
	}

	@Test fun selectedServiceReceivesLightsAndOnlySelectedOutputPlaysEveryMatrixPad() {
		val connection = connection()
		val selection = selection(connection)
		val pads = Collections.synchronizedList(mutableListOf<List<Int>>())
		val functions = Collections.synchronizedList(mutableListOf<Pair<Int,Boolean>>())
		var detached = 0
		main {
			MidiConnection.controller = object : MidiController() {
				override fun onAttach() { }
				override fun onDetach() { detached++ }
				override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) { pads.add(listOf(x,y,if(upDown) 1 else 0,velocity)) }
				override fun onChainTouch(c: Int, upDown: Boolean) { }
				override fun onFunctionKeyTouch(f: Int, upDown: Boolean) { functions.add(f to upDown) }
				override fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velocity: Int) { }
			}
			assertTrue(connection.connect(selection, Matrix()))
		}
		try {
			await("Device did not connect") { main { connection.state is VirtualMidiConnection.State.Connected } }
			await("Selected output port was not subscribed") { call("read").getInt("subscribers1") == 1 }
			assertEquals(0, call("read").getInt("subscribers0"))
			assertTrue(call("read").getBoolean("open0"))
			assertFalse(call("read").getBoolean("open1"))
			assertNotEquals(Process.myPid(), call("read").getInt("pid"))
			// A second registered service is never opened by discovery or by selecting the first.
			assertNotEquals(selection.device.id, info(".UnselectedMidiFixtureService").id)
			val otherUri = Uri.parse("content://com.kimjisub.launchpad.dev.test.othermidi")
			assertTrue(context.contentResolver.call(otherUri,"read","unselected",Bundle())!!.getBoolean("absent"))
			send(byteArrayOf(0x91.toByte(),36,100), port = 0)
			Thread.sleep(100)
			assertTrue(pads.isEmpty())
			for (note in 36..99) { send(0x91,note,100, 0x81,note,64, 0x91,note,0) }
			await("Matrix pad events missing") { pads.size == 64 * 3 }
			for (note in 36..99) {
				val index = (note - 36) * 3
				val base = if (note < 68) 67 else 99
				val x = (base-note)/4
				val y = 3-(base-note)%4 + if (note < 68) 0 else 4
				assertEquals(listOf(x,y,1,100), pads[index])
				assertEquals(listOf(x,y,0,0), pads[index+1])
				assertEquals(listOf(x,y,0,0), pads[index+2])
			}
			for (note in (28..35) + (100..123)) send(0x91,note,127,0x81,note,64)
			await("Matrix ring events missing") { functions.size == 64 }
			assertEquals((0..31).toSet(), functions.filter { it.second }.map { it.first }.toSet())
			assertEquals((0..31).toSet(), functions.filter { !it.second }.map { it.first }.toSet())
			call("clear")
			main { MidiConnection.driver.sendPadLed(7,0,100); MidiConnection.driver.sendPadLed(7,0,0) }
			await("Light bytes did not reach the other service") { call("read").getByteArray("lights0")!!.size >= 6 }
			assertArrayEquals(byteArrayOf(0x91.toByte(),36,100,0x91.toByte(),36,0), call("read").getByteArray("lights0"))
			assertTrue(call("read").getByteArray("lights1")!!.isEmpty())
		} finally { close(connection); main { MidiConnection.controller = null } }
		assertTrue(detached >= 1)
		val lights = call("read").getByteArray("lights0")!!
		assertTrue("Disconnect must clear pads and ring at the peer", lights.size >= 6 + 96 * 3)
		assertTrue(lights.takeLast(96*3).chunked(3).all { it[2] == 0.toByte() })
	}

	@Test fun fragmentedBundledAndMalformedMessagesUseTheRealServiceStream() {
		val connection = connection()
		val events = Collections.synchronizedList(mutableListOf<Boolean>())
		val driver = Matrix().apply {
			setOnGetSignalListener(null)
		}
		val selected = selection(connection, null)
		main {
			MidiConnection.controller = object : MidiController() {
				override fun onAttach() { }
				override fun onDetach() { }
				override fun onPadTouch(x: Int,y: Int,upDown: Boolean,velocity: Int) { events.add(upDown) }
				override fun onChainTouch(c: Int,upDown: Boolean) { }
				override fun onFunctionKeyTouch(f: Int,upDown: Boolean) { }
				override fun onUnknownEvent(cmd: Int,sig: Int,note: Int,velocity: Int) { }
			}
			assertTrue(connection.connect(selected,driver))
		}
		try {
			await("Device did not connect") { main { connection.state is VirtualMidiConnection.State.Connected } }
			send(0x91,36); send(127,36,0,0x81,36,64)
			send(0xf0); repeat(128) { send(ByteArray(1024) { 1 }) }; send(0xf7,1,2,0x91,36,0xf8,127,36,0)
			await("Fragmented stream was not decoded exactly once") { events.size == 5 }
			assertEquals(listOf(true,false,false,true,false), events.toList())
			main { driver.sendPadLed(0,0,100) }
			assertFalse(call("read").getBoolean("open0"))
		} finally { close(connection); main { MidiConnection.controller = null } }
	}

	@Test fun peerProcessExitReleasesInputOnlyConnectionWithoutRediscoveryOrReconnect() {
		val keySound = File(screen.pack(), "keySound")
		keySound.writeText(keySound.readLines().joinToString("\n") {
			if (it.startsWith("1 1 1 ")) "1 1 1 silence.wav 0" else it
		} + "\n")
		screen.openPlay()
		screen.onMain { screen.vm().scbFeedbackLight.setChecked(true) }
		val connection = connection()
		val selected = selection(connection, null)
		main { assertTrue(connection.connect(selected, Matrix())) }
		try {
			await("Input-only device did not connect") { main { connection.state == VirtualMidiConnection.State.Connected(false) } }
			await("Peer port status did not settle") { call("read").getInt("subscribers1") == 1 }
			send(0x91,64,127)
			screen.await("Peer input did not start the looping pad") { audio.ringingLoops().size == 1 }
			screen.capturePads("actual-midi-service-peer-held", setOf(0))
			call("exit")
			await("Peer death did not disconnect input-only connection") {
				main { connection.state == VirtualMidiConnection.State.Failed(VirtualMidiConnection.Failure.DISCONNECTED) && MidiConnection.connectedDevice == null }
			}
			assertNull(MidiConnection.connectedDevice)
			screen.await("Peer exit left the pad sound looping") { audio.ringingLoops().isEmpty() }
			screen.capturePads("actual-midi-service-peer-disconnected", emptySet())
			assertEquals("Peer exit must not replay the held input", 1, audio.plays.size)
		} finally { close(connection) }
	}

	@Test fun deviceWithoutALightPortAndRepeatedDisconnectKeepUsbAvailable() {
		val connection = connection()
		val id = info(".InputOnlyMidiFixtureService").id
		val device = runBlocking { connection.devices().getOrThrow().single { it.id == id } }
		assertTrue(device.lightPorts.isEmpty())
		val selected = VirtualMidiSelection(device, 0, null)
		repeat(3) {
			main { assertTrue(connection.connect(selected, Matrix())) }
			try {
				await("Input-only service did not connect") { main { connection.state == VirtualMidiConnection.State.Connected(false) } }
				main { MidiConnection.driver.sendPadLed(7,0,100) }
			} finally { close(connection) }
		}
	}

	@Test fun occupiedLightPortRequiresExplicitInputOnlyChoice() {
		val connection = connection()
		val selected = selection(connection)
		val blocker = runBlocking { withTimeout(10_000) { AndroidVirtualMidiBackend(context).open(selected.device.id) } }!!
		val occupied = blocker.openLightPort(0)!!
		try {
			main { assertTrue(connection.connect(selected,Matrix())) }
			await("Occupied light port must not report connected") { main { connection.state == VirtualMidiConnection.State.LightUnavailable } }
			assertNull(MidiConnection.connectedDevice)
			main { connection.continueWithoutLights() }
			assertEquals(VirtualMidiConnection.State.Connected(false), main { connection.state })
		} finally { close(connection); occupied.close(); blocker.close() }
	}
}
