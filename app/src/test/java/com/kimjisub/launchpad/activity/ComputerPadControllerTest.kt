package com.kimjisub.launchpad.activity

import com.kimjisub.launchpad.midi.MidiTransportGate
import com.kimjisub.launchpad.midi.PeripheralDevice
import com.kimjisub.launchpad.midi.PeripheralMidiAccess
import com.kimjisub.launchpad.midi.UsbPeripheralConnection
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ComputerPadControllerTest {
	private val access = object : PeripheralMidiAccess {
		override fun devices(): List<PeripheralDevice> = emptyList()
		override fun watch(changed: () -> Unit, removed: (Int) -> Unit) = AutoCloseable { }
		override fun open(id: Int, completed: (PeripheralMidiAccess.Device?) -> Unit) = Unit
	}
	private val connection = UsbPeripheralConnection(access, MidiTransportGate())
	private val controller = ComputerPadController(connection, midiSupported = true)
	private val port = PeripheralDevice.Port(0, "MIDI 1")

	@After fun tearDown() = connection.close()

	@Test fun aDeviceWithoutAPressPortCannotBeConnectedAndConnectDoesNothing() {
		val lightsOnly = PeripheralDevice(3, "Lights only", inputs = emptyList(), outputs = listOf(port))
		assertFalse(controller.canConnect(listOf(lightsOnly)))
		controller.connect(listOf(lightsOnly)) { it.name }
		assertNull(controller.connected)
		assertEquals(UsbPeripheralConnection.State.Idle, connection.state.value)

		val usable = lightsOnly.copy(id = 4, inputs = listOf(port))
		assertTrue(controller.canConnect(listOf(usable)))
		controller.deviceId = 3
		assertFalse("The chosen device decides, not the first usable one", controller.canConnect(listOf(lightsOnly, usable)))
		controller.deviceId = 4
		assertTrue(controller.canConnect(listOf(lightsOnly, usable)))
	}

	@Test fun reconnectingToADeviceWithoutAPressPortSearchesAgainInsteadOfDoingNothing() {
		val lightsOnly = PeripheralDevice(3, "Lights only", inputs = emptyList(), outputs = listOf(port))
		val reads = AtomicInteger()
		val listing = object : PeripheralMidiAccess by access {
			override fun devices(): List<PeripheralDevice> { reads.incrementAndGet(); return listOf(lightsOnly) }
		}
		UsbPeripheralConnection(listing, MidiTransportGate()).use { found ->
			val screen = ComputerPadController(found, midiSupported = true)
			awaitTrue { found.devices.value == listOf(lightsOnly) }
			screen.deviceId = 3
			val before = reads.get()
			screen.reconnect { it.name }
			assertEquals(UsbPeripheralConnection.State.Idle, found.state.value)
			awaitTrue { reads.get() > before }
		}
	}

	private fun awaitTrue(check: () -> Boolean) {
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
		while (!check() && System.nanoTime() < deadline) Thread.sleep(5)
		assertTrue("Condition did not complete", check())
	}

	@Test fun aDeviceWithoutANameIsShownWithTheSameNameWhileConnected() {
		val unnamed = PeripheralDevice(5, "", inputs = listOf(port), outputs = emptyList())
		assertEquals("USB MIDI", ComputerPadRules.deviceName(unnamed))
		controller.receiveLights = false
		controller.connect(listOf(unnamed)) { it.name }
		assertEquals(UsbPeripheralConnection.State.Connecting, connection.state.value)
		assertEquals(ComputerPadController.Connected("USB MIDI", "MIDI 1", null), controller.connected)
	}
}
