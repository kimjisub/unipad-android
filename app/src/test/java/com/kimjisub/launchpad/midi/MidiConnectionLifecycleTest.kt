package com.kimjisub.launchpad.midi

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import com.kimjisub.launchpad.midi.controller.MidiController
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Lifecycle races around MidiConnection's USB/MIDI API init and its receive loop - the two
 * paths behind the Play Vitals NullPointerException clusters (ReceiveTask.doInBackground on
 * builds 89-103, initMidiApiDevice on build 109). Every scenario must end without an
 * exception escaping to a thread's uncaught handler, which is what crashes the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MidiConnectionLifecycleTest {

	private lateinit var mainDispatcher: ExecutorCoroutineDispatcher
	private val uncaught: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())
	private var previousUncaughtHandler: Thread.UncaughtExceptionHandler? = null

	private lateinit var usbManager: UsbManager
	private lateinit var midiManager: MidiManager
	private lateinit var context: Context

	@Before
	fun setUp() {
		mainDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
		Dispatchers.setMain(mainDispatcher)
		previousUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
		Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught.add(e) }

		usbManager = mockk(relaxed = true)
		every { usbManager.hasPermission(any<UsbDevice>()) } returns true
		every { usbManager.deviceList } returns HashMap()
		midiManager = mockk(relaxed = true)
		context = mockk(relaxed = true)
		every { context.applicationContext } returns context
		every { context.getSystemService(Context.MIDI_SERVICE) } returns midiManager

		// A launch-path call with nothing attached wires the cycle/signal listeners up front,
		// so each test's attach does not race initConnection() finishing that wiring.
		MidiConnection.initConnection(intent(action = null, device = null), usbManager, context)
	}

	@After
	fun tearDown() {
		MidiConnection.controller = null
		MidiConnection.connectionObserver = null
		Thread.setDefaultUncaughtExceptionHandler(previousUncaughtHandler)
		Dispatchers.resetMain()
		mainDispatcher.close()
	}

	@Test
	fun init_midiServiceEnumerationNpe_fallsBackToUsbClaim() {
		every { midiManager.devices } throws NullPointerException("MidiManager.getDevices")
		val (device, connection) = launchpad(deviceId = 1)

		attach(device)

		verify(timeout = TIMEOUT_MS) { connection.claimInterface(any(), true) }
		assertNoCrash()
	}

	@Test
	fun init_midiServiceOpenDeviceNpe_fallsBackToUsbClaim() {
		every { midiManager.devices } returns arrayOf(midiDeviceInfo())
		every { midiManager.openDevice(any(), any(), any()) } throws NullPointerException("MidiManager.openDevice")
		val (device, connection) = launchpad(deviceId = 1)

		attach(device)

		verify(timeout = TIMEOUT_MS) { connection.claimInterface(any(), true) }
		assertNoCrash()
	}

	@Test
	fun init_openDeviceCallbackAfterDeviceReplaced_releasesMidiDeviceWithoutOpeningPorts() {
		val info = midiDeviceInfo()
		every { midiManager.devices } returns arrayOf(info)
		val callback = slot<MidiManager.OnDeviceOpenedListener>()
		every { midiManager.openDevice(info, capture(callback), any()) } returns Unit
		val (first, firstConnection) = launchpad(deviceId = 1)
		val (second, _) = launchpad(deviceId = 2)

		attach(first)
		val lateCallback = callback.captured
		// Single-pad mode: the second attach tears the first session down before the MIDI
		// service has answered the first openDevice().
		attach(second)
		verify(timeout = TIMEOUT_MS) { firstConnection.close() }

		val staleMidiDevice = mockk<MidiDevice>(relaxed = true)
		lateCallback.onDeviceOpened(staleMidiDevice)

		verify(timeout = TIMEOUT_MS) { staleMidiDevice.close() }
		verify(exactly = 0) { staleMidiDevice.openInputPort(any()) }
		verify(exactly = 0) { firstConnection.claimInterface(any(), any()) }
		assertNoCrash()
	}

	@Test
	fun receive_controllerThrowsOnAttach_tearsDownWithoutCrash() {
		every { midiManager.devices } returns emptyArray()
		val controller = mockk<MidiController>(relaxed = true)
		every { controller.onAttach() } throws IllegalStateException("controller not ready")
		MidiConnection.controller = controller
		val (device, connection) = launchpad(deviceId = 1)

		attach(device)

		verify(timeout = TIMEOUT_MS) { connection.close() }
		assertNoCrash()
	}

	@Test
	fun receive_connectionReleasedMidTransfer_tearsDownWithoutCrash() {
		every { midiManager.devices } returns emptyArray()
		val observer = mockk<MidiConnection.ConnectionObserver>(relaxed = true)
		MidiConnection.connectionObserver = observer
		val (device, connection) = launchpad(deviceId = 1)
		every { connection.bulkTransfer(any(), any(), any<Int>(), any<Int>()) } throws
			NullPointerException("UsbDeviceConnection released")

		attach(device)

		verify(timeout = TIMEOUT_MS) { connection.close() }
		verify(timeout = TIMEOUT_MS) { observer.onDisconnected() }
		assertNoCrash()
	}

	private fun attach(device: UsbDevice) {
		MidiConnection.initConnection(intent(UsbManager.ACTION_USB_DEVICE_ATTACHED, device), usbManager, context)
	}

	private fun intent(action: String?, device: UsbDevice?): Intent {
		val intent = mockk<Intent>(relaxed = true)
		every { intent.action } returns action
		@Suppress("DEPRECATION")
		every { intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) } returns device
		return intent
	}

	// Launchpad X (PID 0x0103) with a single MIDI streaming interface. The receive loop's
	// bulkTransfer fails fast by default, so the loop ends and the session tears itself down.
	private fun launchpad(deviceId: Int): Pair<UsbDevice, UsbDeviceConnection> {
		val endpointIn = mockk<UsbEndpoint>(relaxed = true)
		every { endpointIn.direction } returns UsbConstants.USB_DIR_IN
		every { endpointIn.maxPacketSize } returns 64
		val endpointOut = mockk<UsbEndpoint>(relaxed = true)
		every { endpointOut.direction } returns UsbConstants.USB_DIR_OUT

		val usbInterface = mockk<UsbInterface>(relaxed = true)
		every { usbInterface.interfaceClass } returns UsbConstants.USB_CLASS_AUDIO
		every { usbInterface.interfaceSubclass } returns 3
		every { usbInterface.endpointCount } returns 2
		every { usbInterface.getEndpoint(0) } returns endpointIn
		every { usbInterface.getEndpoint(1) } returns endpointOut

		val device = mockk<UsbDevice>(relaxed = true)
		every { device.deviceId } returns deviceId
		every { device.productId } returns 0x0103
		every { device.productName } returns null
		every { device.interfaceCount } returns 1
		every { device.getInterface(0) } returns usbInterface

		val connection = mockk<UsbDeviceConnection>(relaxed = true)
		every { connection.claimInterface(any(), any()) } returns true
		every { connection.bulkTransfer(any(), any(), any<Int>(), any<Int>()) } returns -1
		every { usbManager.openDevice(device) } returns connection
		return device to connection
	}

	private fun midiDeviceInfo(): MidiDeviceInfo {
		val port = mockk<MidiDeviceInfo.PortInfo>(relaxed = true)
		every { port.type } returns MidiDeviceInfo.PortInfo.TYPE_INPUT
		every { port.portNumber } returns 0
		val info = mockk<MidiDeviceInfo>(relaxed = true)
		every { info.inputPortCount } returns 1
		every { info.ports } returns arrayOf(port)
		return info
	}

	private fun assertNoCrash() {
		// Give the IO/Main coroutines a moment to surface anything they would throw.
		Thread.sleep(300)
		assertTrue("uncaught: $uncaught", uncaught.isEmpty())
	}

	private companion object {
		const val TIMEOUT_MS = 3_000L
	}
}
