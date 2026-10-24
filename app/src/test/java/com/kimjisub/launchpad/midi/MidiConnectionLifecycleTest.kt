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
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.LaunchpadCoreFW
import com.kimjisub.launchpad.midi.driver.LaunchpadX
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
		MidiConnection.listener = null
		Thread.setDefaultUncaughtExceptionHandler(previousUncaughtHandler)
		Dispatchers.resetMain()
		mainDispatcher.close()
	}

	@Test
	fun init_midiServiceEnumerationNpe_fallsBackToUsbClaim() {
		midiManager.stubDeviceList() throws NullPointerException("MidiManager.getDevices")
		val (device, connection) = launchpad(deviceId = 1)

		attach(device)

		verify(timeout = TIMEOUT_MS) { connection.claimInterface(any(), true) }
		assertNoCrash()
	}

	@Test
	fun init_midiServiceOpenDeviceNpe_fallsBackToUsbClaim() {
		midiManager.stubDeviceList() returns arrayOf(midiDeviceInfo())
		every { midiManager.openDevice(any(), any(), any()) } throws NullPointerException("MidiManager.openDevice")
		val (device, connection) = launchpad(deviceId = 1)

		attach(device)

		verify(timeout = TIMEOUT_MS) { connection.claimInterface(any(), true) }
		assertNoCrash()
	}

	@Test
	fun init_openDeviceCallbackAfterDeviceReplaced_releasesMidiDeviceWithoutOpeningPorts() {
		val info = midiDeviceInfo()
		midiManager.stubDeviceList() returns arrayOf(info)
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
		midiManager.stubDeviceList() returns emptyArray()
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
		midiManager.stubDeviceList() returns emptyArray()
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

	/** Mini MK2 input through the actual USB receive loop, rather than calling a driver directly. */
	@Test
	fun receive_miniChord_keepsEveryPressAndReleaseAcrossBatchedPackets() {
		midiManager.stubDeviceList() returns emptyArray()
		val events = Collections.synchronizedList(mutableListOf<String>())
		val controller = mockk<MidiController>(relaxed = true)
		every { controller.onPadTouch(any(), any(), any(), any()) } answers {
			events.add("${firstArg<Int>()},${secondArg<Int>()},${thirdArg<Boolean>()}")
		}
		MidiConnection.controller = controller
		val (device, connection) = launchpad(deviceId = 1)
		every { device.productId } returns 0x0036
		val packets = listOf(
			byteArrayOf(9, 0x90.toByte(), 0, 127, 9, 0x90.toByte(), 17, 127, 9, 0x90.toByte(), 119, 127),
			// A clock packet between release events must not drop either neighbouring event.
			byteArrayOf(9, 0x90.toByte(), 17, 0, 15, 0xF8.toByte(), 0, 0, 9, 0x90.toByte(), 0, 0, 9, 0x90.toByte(), 119, 0),
		)
		var nextPacket = 0
		every { connection.bulkTransfer(match { it.direction == UsbConstants.USB_DIR_IN }, any(), any<Int>(), any<Int>()) } answers {
			if (nextPacket < packets.size) {
				val packet = packets[nextPacket++]
				packet.copyInto(secondArg<ByteArray>())
				packet.size
			} else -1
		}

		attach(device)

		verify(timeout = TIMEOUT_MS) { controller.onPadTouch(7, 7, false, 0) }
		assertEquals(
			listOf("0,0,true", "1,1,true", "7,7,true", "1,1,false", "0,0,false", "7,7,false"),
			synchronized(events) { events.toList() },
		)
		verify(timeout = TIMEOUT_MS) { connection.close() }
		assertNoCrash()
	}

	@Test
	fun receive_originalLaunchpadReadsRawMidiAcrossUsbTransfers() {
		midiManager.stubDeviceList() returns emptyArray()
		val events = Collections.synchronizedList(mutableListOf<String>())
		val controller = mockk<MidiController>(relaxed = true)
		every { controller.onPadTouch(any(), any(), any(), any()) } answers {
			events.add("pad:${firstArg<Int>()},${secondArg<Int>()},${thirdArg<Boolean>()}")
		}
		every { controller.onChainTouch(any(), any()) } answers {
			events.add("chain:${firstArg<Int>()},${secondArg<Boolean>()}")
		}
		every { controller.onFunctionKeyTouch(any(), any()) } answers {
			events.add("function:${firstArg<Int>()},${secondArg<Boolean>()}")
		}
		MidiConnection.controller = controller
		val (device, connection) = originalLaunchpad()
		// Raw MIDI messages can straddle transfers; a real-time clock may interrupt one.
		val transfers = listOf(
			byteArrayOf(0x90.toByte(), 0, 127, 0x90.toByte(), 17),
			byteArrayOf(127, 0xf8.toByte(), 0x90.toByte(), 0, 0, 0xb0.toByte(), 104),
			byteArrayOf(127, 0x90.toByte(), 120, 127, 0x90.toByte(), 17, 0),
			byteArrayOf(0x80.toByte(), 120, 64, 0xb0.toByte(), 104, 0),
		)
		var next = 0
		every { connection.bulkTransfer(match { it.direction == UsbConstants.USB_DIR_IN }, any(), any<Int>(), any<Int>()) } answers {
			if (next < transfers.size) {
				val bytes = transfers[next++]
				bytes.copyInto(secondArg<ByteArray>())
				bytes.size
			} else -1
		}

		attach(device)

		verify(timeout = TIMEOUT_MS) { connection.close() }
		assertEquals(
			listOf("pad:0,0,true", "pad:1,1,true", "pad:0,0,false", "function:0,true",
				"chain:7,true", "function:15,true", "pad:1,1,false", "chain:7,false", "function:15,false", "function:0,false"),
			synchronized(events) { events.toList() },
		)
		assertNoCrash()
	}

	@Test
	fun send_originalLaunchpadWritesMidiWithoutUsbPacketHeaders() {
		midiManager.stubDeviceList() returns emptyArray()
		val sent = Collections.synchronizedList(mutableListOf<Int>())
		val written = CountDownLatch(1)
		val controller = mockk<MidiController>(relaxed = true)
		every { controller.onAttach() } answers { MidiConnection.driver.sendPadLed(3, 4, 6) }
		MidiConnection.controller = controller
		val (device, connection) = originalLaunchpad()
		every { connection.bulkTransfer(match { it.direction == UsbConstants.USB_DIR_OUT }, any(), any<Int>(), any<Int>()) } answers {
			val count = thirdArg<Int>()
			sent.addAll(secondArg<ByteArray>().take(count).map { it.toInt() and 0xff })
			written.countDown()
			count
		}
		every { connection.bulkTransfer(match { it.direction == UsbConstants.USB_DIR_IN }, any(), any<Int>(), any<Int>()) } answers {
			written.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)
			-1
		}

		attach(device)

		verify(timeout = TIMEOUT_MS) { connection.close() }
		assertEquals(listOf(0x90, 52, 3), synchronized(sent) { sent.toList() })
		assertNoCrash()
	}

	private fun originalLaunchpad(): Pair<UsbDevice, UsbDeviceConnection> {
		val (device, connection) = launchpad(deviceId = 1)
		every { device.vendorId } returns 0x1235
		every { device.productId } returns 0x000e
		every { device.productName } returns "Launchpad"
		val usbInterface = device.getInterface(0)
		every { usbInterface.interfaceClass } returns UsbConstants.USB_CLASS_VENDOR_SPEC
		every { usbInterface.interfaceSubclass } returns 0
		val endpointIn = usbInterface.getEndpoint(0)
		every { endpointIn.maxPacketSize } returns 8
		assertEquals(UsbConstants.USB_DIR_IN, endpointIn.direction)
		return device to connection
	}

	@Test
	fun attach_otherVendorWithOriginalProductIdKeepsMasterKeyboard() {
		midiManager.stubDeviceList() returns emptyArray()
		val names = recordConnectedNames()
		val (device, connection) = originalLaunchpad()
		every { device.vendorId } returns 0x4321
		val usbInterface = device.getInterface(0)
		every { usbInterface.interfaceClass } returns UsbConstants.USB_CLASS_AUDIO
		every { usbInterface.interfaceSubclass } returns 3

		attach(device)

		verify(timeout = TIMEOUT_MS) { connection.close() }
		assertEquals(listOf("Master Keyboard"), names.toList())
		assertNoCrash()
	}

	@Test
	fun attach_coreFwSerialNumber_picksTheCoreFwDriver() {
		midiManager.stubDeviceList() returns emptyArray()
		val drivers = recordDrivers()
		val connectedNames = recordConnectedNames()
		val (device, _) = launchpad(deviceId = 1)
		every { device.serialNumber } returns "COREFW-LPX"

		attach(device)

		assertEquals(LaunchpadCoreFW::class, drivers.last()::class)
		assertEquals("Launchpad X (CoreFW)", connectedNames.last())
		assertNoCrash()
	}

	/** Without the USB permission the serial number throws; the device keeps its official driver. */
	@Test
	fun attach_unreadableSerialNumber_keepsTheOfficialDriver() {
		midiManager.stubDeviceList() returns emptyArray()
		val drivers = recordDrivers()
		val connectedNames = recordConnectedNames()
		val (device, connection) = launchpad(deviceId = 1)
		every { device.serialNumber } throws SecurityException("no permission for the serial number")

		attach(device)

		assertEquals(LaunchpadX::class, drivers.last()::class)
		assertEquals("Launchpad X", connectedNames.last())
		verify(timeout = TIMEOUT_MS) { connection.claimInterface(any(), true) }
		assertNoCrash()
	}

	private fun recordDrivers(): List<DriverRef> {
		val drivers = Collections.synchronizedList(mutableListOf<DriverRef>())
		val listener = mockk<MidiConnection.Listener>(relaxed = true)
		every { listener.onChangeDriver(any()) } answers { drivers.add(firstArg()) }
		MidiConnection.listener = listener
		return drivers
	}

	/**
	 * The fake receive loop ends at once and the session clears `connectedDevice` on another
	 * thread, so the name is read from the connect callback rather than from that field.
	 */
	private fun recordConnectedNames(): List<String> {
		val names = Collections.synchronizedList(mutableListOf<String>())
		val observer = mockk<MidiConnection.ConnectionObserver>(relaxed = true)
		every { observer.onConnected(any()) } answers { names.add(firstArg<MidiConnection.ConnectedDeviceSnapshot>().name) }
		MidiConnection.connectionObserver = observer
		return names
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
