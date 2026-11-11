package com.kimjisub.launchpad.midi

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.os.Bundle
import com.kimjisub.launchpad.midi.driver.LaunchpadX
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.Executors

/** Mode commands must follow the attached USB device, regardless of MIDI service list order. */
@OptIn(ExperimentalCoroutinesApi::class)
class MidiConnectionMidiDeviceSelectionTest {

	private lateinit var mainDispatcher: ExecutorCoroutineDispatcher
	private lateinit var usbManager: UsbManager
	private lateinit var midiManager: MidiManager
	private lateinit var context: Context
	private var dualPadMode = false
	private val pads = mutableListOf<FakePad>()
	private val modeMessages = LaunchpadX().getInitSysEx().first

	@Before
	fun setUp() {
		mainDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
		Dispatchers.setMain(mainDispatcher)
		usbManager = mockk(relaxed = true)
		every { usbManager.hasPermission(any<UsbDevice>()) } returns true
		every { usbManager.deviceList } returns HashMap()
		midiManager = mockk(relaxed = true)
		val prefs = mockk<SharedPreferences>(relaxed = true)
		every { prefs.getBoolean(any(), any()) } answers { dualPadMode }
		context = mockk(relaxed = true)
		every { context.applicationContext } returns context
		every { context.getSystemService(Context.MIDI_SERVICE) } returns midiManager
		every { context.getSharedPreferences(any(), any()) } returns prefs
		MidiConnection.initConnection(intent(null, null), usbManager, context)
	}

	@After
	fun tearDown() {
		for (pad in pads) pad.unplugged = true
		for (pad in pads) verify(timeout = TIMEOUT_MS) { pad.connection.close() }
		MidiConnection.controller = null
		MidiConnection.connectionObserver = null
		MidiConnection.listener = null
		Dispatchers.resetMain()
		mainDispatcher.close()
	}

	@Test
	fun virtualDeviceBeforeUsbDevice_sendsModeOnlyToAttachedPad() {
		val pad = pad(1)
		val virtual = midiInfo(null)
		val matching = midiInfo(pad.device)
		val port = openWithPort(matching)
		openWithPort(virtual)
		midiManager.stubDeviceList() returns arrayOf(virtual, matching)

		attach(pad)

		assertModeSent(port)
		verify(exactly = 0) { midiManager.openDevice(virtual, any(), any()) }
		assertEquals(emptyList<List<Byte>>(), pad.usbWrites.toList())
	}

	@Test
	fun twoIdenticalPads_eachReceivesItsOwnModeCommands() {
		dualPadMode = true
		val first = pad(1)
		val second = pad(2)
		val firstInfo = midiInfo(first.device)
		val secondInfo = midiInfo(second.device)
		val firstPort = openWithPort(firstInfo)
		val secondPort = openWithPort(secondInfo)
		// Same model and product id; only the USB device identifies the intended pad.
		midiManager.stubDeviceList() returns arrayOf(firstInfo, secondInfo)

		attach(first)
		attach(second)

		assertModeSent(firstPort)
		assertModeSent(secondPort)
		verify(exactly = 1) { midiManager.openDevice(firstInfo, any(), any()) }
		verify(exactly = 1) { midiManager.openDevice(secondInfo, any(), any()) }
		assertEquals(2, MidiConnection.connectedDevices.size)
	}

	@Test
	fun noMatchingUsbDevice_sendsModeOverUsbWithoutOpeningOtherDevices() {
		val pad = pad(1)
		val otherPad = pad(2)
		val virtual = midiInfo(null)
		val otherInfo = midiInfo(otherPad.device)
		openWithPort(virtual)
		openWithPort(otherInfo)
		midiManager.stubDeviceList() returns arrayOf(virtual, otherInfo)

		attach(pad)

		verify(exactly = 0) { midiManager.openDevice(any(), any(), any()) }
		assertUsbModeSent(pad)
	}

	@Test
	fun matchingDeviceWithoutInputPort_fallsBackToUsb() {
		val pad = pad(1)
		val virtual = midiInfo(null)
		openWithPort(virtual)
		midiManager.stubDeviceList() returns arrayOf(virtual, midiInfo(pad.device, inputPorts = 0))

		attach(pad)

		verify(exactly = 0) { midiManager.openDevice(any(), any(), any()) }
		assertUsbModeSent(pad)
	}

	private fun assertModeSent(port: MidiInputPort) {
		for (message in modeMessages) {
			verify(exactly = 1) { port.send(match { it.contentEquals(message) }, 0, message.size) }
		}
	}

	private fun assertUsbModeSent(pad: FakePad) {
		for (message in modeMessages) {
			val encoded = UsbMidiSysEx.encode(message, 1)
			verify(timeout = TIMEOUT_MS, exactly = 1) {
				pad.connection.bulkTransfer(pad.endpointOut, match { it.contentEquals(encoded) }, 0, encoded.size, any())
			}
		}
	}

	private fun pad(id: Int): FakePad = FakePad(id).also {
		every { usbManager.openDevice(it.device) } returns it.connection
	}

	private fun attach(pad: FakePad) {
		pads.add(pad)
		MidiConnection.initConnection(intent(UsbManager.ACTION_USB_DEVICE_ATTACHED, pad.device), usbManager, context)
		verify(timeout = TIMEOUT_MS) { pad.connection.claimInterface(any(), true) }
	}

	@Suppress("DEPRECATION")
	private fun midiInfo(device: UsbDevice?, inputPorts: Int = 1): MidiDeviceInfo {
		val props = mockk<Bundle>(relaxed = true)
		every { props.getParcelable<UsbDevice>(MidiDeviceInfo.PROPERTY_USB_DEVICE) } returns device
		val port = mockk<MidiDeviceInfo.PortInfo>(relaxed = true)
		every { port.type } returns MidiDeviceInfo.PortInfo.TYPE_INPUT
		every { port.portNumber } returns 0
		return mockk<MidiDeviceInfo>(relaxed = true).also {
			every { it.properties } returns props
			every { it.inputPortCount } returns inputPorts
			every { it.ports } returns if (inputPorts > 0) arrayOf(port) else emptyArray()
		}
	}

	private fun openWithPort(info: MidiDeviceInfo): MidiInputPort {
		val port = mockk<MidiInputPort>(relaxed = true)
		val device = mockk<MidiDevice>(relaxed = true)
		every { device.openInputPort(0) } returns port
		every { midiManager.openDevice(info, any(), any()) } answers {
			secondArg<MidiManager.OnDeviceOpenedListener>().onDeviceOpened(device)
		}
		return port
	}

	@Suppress("DEPRECATION")
	private fun intent(action: String?, device: UsbDevice?): Intent = mockk<Intent>(relaxed = true).also {
		every { it.action } returns action
		every { it.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) } returns device
	}

	private class FakePad(id: Int) {
		val device = mockk<UsbDevice>(relaxed = true)
		val connection = mockk<UsbDeviceConnection>(relaxed = true)
		val endpointOut = mockk<UsbEndpoint>(relaxed = true)
		val usbWrites = Collections.synchronizedList(mutableListOf<List<Byte>>())
		@Volatile var unplugged = false

		init {
			val endpointIn = mockk<UsbEndpoint>(relaxed = true)
			every { endpointIn.direction } returns UsbConstants.USB_DIR_IN
			every { endpointIn.maxPacketSize } returns 64
			every { endpointOut.direction } returns UsbConstants.USB_DIR_OUT
			val usbInterface = mockk<UsbInterface>(relaxed = true)
			every { usbInterface.interfaceClass } returns UsbConstants.USB_CLASS_AUDIO
			every { usbInterface.interfaceSubclass } returns 3
			every { usbInterface.endpointCount } returns 2
			every { usbInterface.getEndpoint(0) } returns endpointIn
			every { usbInterface.getEndpoint(1) } returns endpointOut
			every { device.deviceId } returns id
			every { device.deviceName } returns "/dev/bus/usb/001/00$id"
			every { device.productId } returns 0x0103
			every { device.interfaceCount } returns 1
			every { device.getInterface(0) } returns usbInterface
			every { connection.claimInterface(any(), any()) } returns true
			// A slow idle read keeps this session alive until the explicit detach in tearDown.
			every { connection.bulkTransfer(endpointIn, any(), any<Int>(), any<Int>()) } answers {
				if (unplugged) -1 else {
					Thread.sleep(100)
					0
				}
			}
			every { connection.bulkTransfer(endpointOut, any(), any<Int>(), any<Int>()) } answers { thirdArg<Int>() }
			every { connection.bulkTransfer(endpointOut, any(), any<Int>(), any<Int>(), any<Int>()) } answers {
				val offset = thirdArg<Int>()
				val length = arg<Int>(3)
				usbWrites.add(secondArg<ByteArray>().slice(offset until offset + length))
				length
			}
		}
	}

	private companion object {
		const val TIMEOUT_MS = 3_000L
	}
}
