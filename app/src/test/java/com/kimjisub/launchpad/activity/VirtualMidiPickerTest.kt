package com.kimjisub.launchpad.activity

import com.kimjisub.launchpad.activity.VirtualMidiPicker.Lights
import com.kimjisub.launchpad.midi.MidiTransportGate
import com.kimjisub.launchpad.midi.VirtualMidiBackend
import com.kimjisub.launchpad.midi.VirtualMidiConnection
import com.kimjisub.launchpad.midi.VirtualMidiConnection.Failure
import com.kimjisub.launchpad.midi.VirtualMidiConnection.State
import com.kimjisub.launchpad.midi.VirtualMidiDevice
import com.kimjisub.launchpad.midi.VirtualMidiPort
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.Matrix
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VirtualMidiPickerTest {
	private class Port(val fail: Boolean = false) : VirtualMidiBackend.Port {
		override fun send(bytes: ByteArray) { if (fail) error("Peer stopped reading") }
		override fun close() { }
	}

	private class Device(var lightPort: Port? = Port()) : VirtualMidiBackend.Device {
		val opened = mutableListOf<Int>()
		override fun openPerformancePort(number: Int, receiver: (ByteArray, Int, Int) -> Unit): VirtualMidiBackend.Port {
			opened += number
			return Port()
		}
		override fun openLightPort(number: Int): VirtualMidiBackend.Port? = lightPort
		override fun close() { }
	}

	private class Backend : VirtualMidiBackend {
		var list = listOf(mystrix)
		var listing: CompletableDeferred<Unit>? = null
		var reads = 0
		val opens = mutableListOf<Int>()
		var device: CompletableDeferred<VirtualMidiBackend.Device?> = CompletableDeferred(Device())
		var removed: ((Int) -> Unit)? = null
		override suspend fun devices(): List<VirtualMidiDevice> {
			reads++
			listing?.await()
			return list
		}
		override suspend fun open(id: Int): VirtualMidiBackend.Device? {
			opens += id
			return device.await()
		}
		override fun watchRemoval(callback: (Int) -> Unit): AutoCloseable {
			removed = callback
			return AutoCloseable { removed = null }
		}
	}

	private class Fixture(scope: TestScope, usb: Boolean = false, attach: (DriverRef.OnSendSignalListener) -> Unit = { }) {
		val backend = Backend()
		val gate = MidiTransportGate()
		var usbInUse = usb
		private val dispatcher = StandardTestDispatcher(scope.testScheduler)
		val connection = VirtualMidiConnection(backend, scope.backgroundScope, dispatcher, dispatcher, gate) { _, _, output ->
			attach(output)
			AutoCloseable { }
		}
		val picker = VirtualMidiPicker(connection, CoroutineScope(dispatcher + scope.backgroundScope.coroutineContext[kotlinx.coroutines.Job]!!),
			available = true, usbInUse = { usbInUse },
			recommend = { if (it.recommendsMatrix) MATRIX else null }, driverFor = { Matrix() })
		val ui get() = picker.ui.value
	}

	@Test fun openingReadsTheListOnceAndNeverConnects() = runTest {
		val f = Fixture(this)
		f.backend.listing = CompletableDeferred()
		f.picker.open()
		runCurrent()
		assertTrue(f.ui.scanning)
		f.picker.open()
		f.picker.search()
		runCurrent()
		assertEquals("A read already running is not repeated", 1, f.backend.reads)
		f.backend.listing!!.complete(Unit)
		runCurrent()
		advanceTimeBy(60_000)
		runCurrent()
		assertFalse(f.ui.scanning)
		assertEquals(listOf(mystrix), f.ui.devices)
		assertEquals("No background search", 1, f.backend.reads)
		assertTrue("Opening the tab must not open a device", f.backend.opens.isEmpty())
		assertNull(f.ui.selected)
		assertFalse(f.ui.canConnect)
	}

	@Test fun stoppingASearchKeepsTheEarlierList() = runTest {
		val f = Fixture(this)
		f.picker.open()
		runCurrent()
		f.backend.list = emptyList()
		f.backend.listing = CompletableDeferred()
		f.picker.search()
		runCurrent()
		assertTrue(f.ui.scanning)
		f.picker.cancelSearch()
		f.backend.listing!!.complete(Unit)
		runCurrent()
		assertFalse(f.ui.scanning)
		assertEquals(listOf(mystrix), f.ui.devices)
	}

	@Test fun aDeviceWithOnePortOfEachIsReadyWithTheRecommendedModel() = runTest {
		val f = Fixture(this)
		f.picker.open(); runCurrent()
		f.picker.select(mystrix.id)
		assertEquals(1, f.ui.performancePort)
		assertEquals(Lights.ON, f.ui.lights)
		assertEquals(0, f.ui.lightPort)
		assertEquals(MATRIX, f.ui.model)
		assertEquals(MATRIX, f.ui.recommended)
		assertTrue(f.ui.canConnect)
		f.picker.chooseModel(2)
		assertEquals("A recommendation is only a default", 2, f.ui.model)
	}

	@Test fun severalPortsMustBeChosenAndLightsCanBeTurnedOff() = runTest {
		val f = Fixture(this)
		f.backend.list = listOf(multi)
		f.picker.open(); runCurrent()
		f.picker.select(multi.id)
		assertNull(f.ui.performancePort)
		assertEquals(Lights.CHOOSE, f.ui.lights)
		assertFalse(f.ui.canConnect)
		f.picker.choosePerformancePort(9)
		assertNull("A port the device does not offer is ignored", f.ui.performancePort)
		f.picker.choosePerformancePort(3)
		assertFalse(f.ui.canConnect)
		f.picker.chooseLightPort(null)
		assertEquals(Lights.OFF, f.ui.lights)
		assertTrue(f.ui.canConnect)
		f.picker.connect()
		runCurrent()
		assertEquals(State.Connected(lights = false), f.ui.connection)
		f.picker.disconnect(); runCurrent()
	}

	@Test fun aDeviceWithoutLightPortsConnectsForInputOnly() = runTest {
		val f = Fixture(this)
		f.backend.list = listOf(inputOnly)
		f.picker.open(); runCurrent()
		f.picker.select(inputOnly.id)
		assertEquals(Lights.NONE, f.ui.lights)
		f.picker.chooseLightPort(0)
		assertEquals("No light port can be picked", Lights.NONE, f.ui.lights)
		assertTrue(f.ui.canConnect)
		f.picker.connect(); runCurrent()
		assertEquals(State.Connected(lights = false), f.ui.connection)
		f.picker.disconnect(); runCurrent()
	}

	@Test fun aDeviceThatSendsNothingCannotBeConnected() = runTest {
		val f = Fixture(this)
		f.backend.list = listOf(lightsOnly)
		f.picker.open(); runCurrent()
		f.picker.select(lightsOnly.id)
		f.picker.chooseModel(MATRIX)
		assertFalse(f.ui.canConnect)
		f.picker.connect(); runCurrent()
		assertTrue(f.backend.opens.isEmpty())
	}

	@Test fun anUnknownDeviceWaitsForAModel() = runTest {
		val f = Fixture(this)
		f.backend.list = listOf(unknown)
		f.picker.open(); runCurrent()
		f.picker.select(unknown.id)
		assertNull(f.ui.model)
		assertNull(f.ui.recommended)
		assertFalse(f.ui.canConnect)
		f.picker.chooseModel(MATRIX)
		assertTrue(f.ui.canConnect)
	}

	@Test fun connectingLocksEveryPickAndCancelReturnsToIdle() = runTest {
		val f = Fixture(this)
		f.backend.device = CompletableDeferred()
		f.backend.list = listOf(mystrix, unknown)
		f.picker.open(); runCurrent()
		f.picker.select(mystrix.id)
		f.picker.connect(); runCurrent()
		assertEquals(State.Connecting, f.ui.connection)
		f.picker.connect(); runCurrent()
		assertEquals("A second request is ignored", 1, f.backend.opens.size)
		f.picker.select(unknown.id)
		f.picker.chooseModel(0)
		f.picker.chooseLightPort(null)
		f.picker.search()
		assertEquals(mystrix, f.ui.selected)
		assertEquals(MATRIX, f.ui.model)
		assertEquals(Lights.ON, f.ui.lights)
		assertEquals("No search while connecting", 1, f.backend.reads)
		f.picker.cancelConnect(); runCurrent()
		assertEquals(State.Idle, f.ui.connection)
		assertTrue("The cancelled attempt returned the transport", f.gate.acquireUsb()?.also { it.close() } != null)
	}

	@Test fun noAnswerWithinTenSecondsFailsAndCanBeRetried() = runTest {
		val f = Fixture(this)
		f.backend.device = CompletableDeferred()
		f.picker.open(); runCurrent()
		f.picker.select(mystrix.id)
		f.picker.connect(); runCurrent()
		advanceTimeBy(10_001); runCurrent()
		assertEquals(State.Failed(Failure.TIMEOUT), f.ui.connection)
		assertFalse(f.ui.lost)
		assertTrue(f.ui.canConnect)
		f.picker.select(mystrix.id)
		assertEquals("A new pick no longer shows the earlier failure", State.Idle, f.ui.connection)
		f.backend.device = CompletableDeferred(Device())
		f.picker.connect(); runCurrent()
		assertTrue(f.ui.connected)
		f.picker.disconnect(); runCurrent()
	}

	@Test fun aBusyLightPortOffersConnectingWithoutLights() = runTest {
		val f = Fixture(this)
		f.backend.device = CompletableDeferred(Device(lightPort = null))
		f.picker.open(); runCurrent()
		f.picker.select(mystrix.id)
		f.picker.connect(); runCurrent()
		assertEquals(State.LightUnavailable, f.ui.connection)
		assertTrue(f.ui.locked)
		f.picker.continueWithoutLights(); runCurrent()
		assertEquals(State.Connected(lights = false), f.ui.connection)
		f.picker.disconnect(); runCurrent()
		assertEquals(State.Idle, f.ui.connection)
	}

	@Test fun aDeviceThatLeavesIsShownAsMissingUntilFoundAndPickedAgain() = runTest {
		val f = Fixture(this)
		f.backend.list = listOf(mystrix, unknown)
		f.picker.open(); runCurrent()
		f.picker.select(mystrix.id)
		f.picker.connect(); runCurrent()
		assertTrue(f.ui.connected)
		f.backend.removed!!(mystrix.id); runCurrent()
		assertEquals(State.Failed(Failure.DISCONNECTED), f.ui.connection)
		assertTrue(f.ui.lost)
		assertEquals(mystrix, f.ui.missing)
		assertEquals(listOf(unknown), f.ui.devices)
		assertNull("A lost device is picked again after a search", f.ui.selected)
		f.picker.select(mystrix.id)
		assertNull(f.ui.selected)
		f.backend.list = listOf(unknown)
		f.picker.search(); runCurrent()
		assertEquals("Still gone: stays dimmed", mystrix, f.ui.missing)
		assertFalse("The search answered the loss; the main button connects again", f.ui.lost)
		assertEquals(State.Idle, f.ui.connection)
		f.backend.list = listOf(mystrix, unknown)
		f.picker.search(); runCurrent()
		advanceTimeBy(60_000); runCurrent()
		assertNull(f.ui.missing)
		assertEquals(listOf(mystrix, unknown), f.ui.devices)
		assertEquals("Found again, but never connected by itself", 1, f.backend.opens.size)
		assertFalse(f.ui.connected)
	}

	@Test fun aLightWriteFailureIsShownLikeADisconnection() = runTest {
		var output: DriverRef.OnSendSignalListener? = null
		val f = Fixture(this) { output = it }
		f.backend.device = CompletableDeferred(Device(lightPort = Port(fail = true)))
		f.picker.open(); runCurrent()
		f.picker.select(mystrix.id)
		f.picker.connect(); runCurrent()
		output!!.onSend(9, 0x90.toByte(), 36, 5); runCurrent()
		assertEquals(State.Failed(Failure.IO), f.ui.connection)
		assertTrue("The other app stopping is shown as a disconnection", f.ui.lost)
		assertEquals(mystrix, f.ui.missing)
	}

	@Test fun aConnectedUsbDeviceBlocksConnecting() = runTest {
		val f = Fixture(this, usb = true)
		f.picker.open(); runCurrent()
		f.picker.select(mystrix.id)
		assertTrue(f.ui.usbInUse)
		assertFalse(f.ui.canConnect)
		f.picker.connect(); runCurrent()
		assertTrue(f.backend.opens.isEmpty())
		f.usbInUse = false
		f.picker.refreshUsb()
		assertTrue(f.ui.canConnect)
	}

	@Test fun theSameImmediateFailureIsShownAgainAfterPickingAgain() = runTest {
		val f = Fixture(this)
		// USB holds the transport before the screen knows (a device waiting for permission).
		val usb = f.gate.acquireUsb()!!
		f.picker.open(); runCurrent()
		f.picker.select(mystrix.id)
		f.picker.connect(); runCurrent()
		assertEquals(State.Failed(Failure.USB_BUSY), f.ui.connection)
		f.picker.select(mystrix.id)
		assertEquals(State.Idle, f.ui.connection)
		f.picker.connect(); runCurrent()
		assertEquals("Connect must answer again, not look unresponsive", State.Failed(Failure.USB_BUSY), f.ui.connection)
		usb.close()
	}

	@Test fun identicalDevicesAreNumbered() {
		val twin = mystrix.copy(id = 8)
		assertEquals(mapOf(mystrix.id to 1, twin.id to 2), deviceOrdinals(listOf(mystrix, unknown, twin)))
		assertEquals(emptyMap<Int, Int>(), deviceOrdinals(listOf(mystrix, mystrix.copy(id = 9, provider = "other.app"))))
	}

	private companion object {
		const val MATRIX = 8
		val mystrix = VirtualMidiDevice(7, "Mystrix", "Mystrix emulator", "test.provider",
			listOf(VirtualMidiPort(1, "Pads")), listOf(VirtualMidiPort(0, "Lights")))
		val multi = VirtualMidiDevice(10, "Matrix twin", "", "test.provider",
			listOf(VirtualMidiPort(2, "A"), VirtualMidiPort(3, "B")), listOf(VirtualMidiPort(0, "A"), VirtualMidiPort(1, "B")))
		val inputOnly = VirtualMidiDevice(11, "Matrix input", "", "test.provider", listOf(VirtualMidiPort(0, "Pads")), emptyList())
		val lightsOnly = VirtualMidiDevice(12, "Matrix lights", "", "test.provider", emptyList(), listOf(VirtualMidiPort(0, "Lights")))
		val unknown = VirtualMidiDevice(13, "Synth", "Keys", "other.app", listOf(VirtualMidiPort(1, "Out")), listOf(VirtualMidiPort(0, "In")))
	}
}
