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
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.LaunchpadMK3
import com.kimjisub.launchpad.midi.driver.LaunchpadMiniMK3
import com.kimjisub.launchpad.midi.driver.LaunchpadX
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.Executors

/**
 * Where the Programmer-mode SysEx of each MK3-generation Launchpad goes, on the three paths that
 * send it: the MIDI service at connect time, the bulk endpoint when the MIDI service is not usable,
 * and the bulk endpoint when the same model is picked again after the MIDI service ports closed.
 *
 * The Pro MK3's USB interfaces are MIDI (cable 0), DIN (cable 1) and DAW (cable 2); its pads and
 * LEDs use cable 0, and cable 1 is the rear MIDI socket. The X and Mini MK3 have DAW (cable 0) and
 * MIDI (cable 1), and their pads and LEDs use cable 1.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MidiConnectionModeCableTest {

	private lateinit var mainDispatcher: ExecutorCoroutineDispatcher
	private lateinit var usbManager: UsbManager
	private lateinit var midiManager: MidiManager
	private lateinit var context: Context
	private val pads = mutableListOf<FakePad>()

	@Before
	fun setUp() {
		mainDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
		Dispatchers.setMain(mainDispatcher)
		usbManager = mockk(relaxed = true)
		every { usbManager.hasPermission(any<UsbDevice>()) } returns true
		every { usbManager.deviceList } returns HashMap()
		midiManager = mockk(relaxed = true)
		val prefs = mockk<SharedPreferences>(relaxed = true)
		every { prefs.getBoolean(any(), any()) } returns false
		context = mockk(relaxed = true)
		every { context.applicationContext } returns context
		every { context.getSystemService(Context.MIDI_SERVICE) } returns midiManager
		every { context.getSharedPreferences(any(), any()) } returns prefs
		midiManager.stubDeviceList() returns emptyArray()
		MidiConnection.initConnection(intent(action = null, device = null), usbManager, context)
	}

	@After
	fun tearDown() {
		for (pad in pads) pad.unplug()
		for (pad in pads) verify(timeout = TIMEOUT_MS) { pad.connection.close() }
		Dispatchers.resetMain()
		mainDispatcher.close()
	}

	@Test fun proMk3_midiServiceUnavailable_sendsModeOnMidiCable() = assertUsbFallbackCable(PRO_MK3, expectedCable = 0)
	@Test fun proMk3_sameModelPickedAgain_sendsModeOnMidiCable() = assertReselectCable(PRO_MK3, ::LaunchpadMK3, expectedCable = 0)
	@Test fun proMk3_connectThroughMidiService_reachesMidiPort() = assertMidiServicePortsInclude(PRO_MK3, port = 0)

	@Test fun x_midiServiceUnavailable_sendsModeOnMidiCable() = assertUsbFallbackCable(X, expectedCable = 1)
	@Test fun x_sameModelPickedAgain_sendsModeOnMidiCable() = assertReselectCable(X, ::LaunchpadX, expectedCable = 1)
	@Test fun x_connectThroughMidiService_reachesMidiPort() = assertMidiServicePortsInclude(X, port = 1)

	@Test fun miniMk3_midiServiceUnavailable_sendsModeOnMidiCable() = assertUsbFallbackCable(MINI_MK3, expectedCable = 1)
	@Test fun miniMk3_sameModelPickedAgain_sendsModeOnMidiCable() = assertReselectCable(MINI_MK3, ::LaunchpadMiniMK3, expectedCable = 1)
	@Test fun miniMk3_connectThroughMidiService_reachesMidiPort() = assertMidiServicePortsInclude(MINI_MK3, port = 1)

	private fun assertUsbFallbackCable(model: Model, expectedCable: Int) {
		val pad = attach(FakePad(model))
		pad.awaitModeMessages(2)
		assertEquals("cables of the ${model.name} mode SysEx", listOf(expectedCable, expectedCable), pad.modeCables())
	}

	private fun assertReselectCable(model: Model, driver: () -> DriverRef, expectedCable: Int) {
		val pad = attachThroughMidiService(FakePad(model))
		verify(timeout = TIMEOUT_MS) { pad.connection.claimInterface(any(), true) }
		assertEquals("nothing on the bulk endpoint before the pick", emptyList<Int>(), pad.modeCables())

		MidiConnection.driver = driver()

		pad.awaitModeMessages(2)
		assertEquals("cables of the ${model.name} mode SysEx after picking it again", listOf(expectedCable, expectedCable), pad.modeCables())
	}

	private fun assertMidiServicePortsInclude(model: Model, port: Int) {
		val pad = attachThroughMidiService(FakePad(model))
		verify(timeout = TIMEOUT_MS) { pad.connection.claimInterface(any(), true) }
		val ports = pad.midiServiceModePorts()
		assertTrue("${model.name} mode SysEx reached MIDI service ports $ports, not $port", port in ports)
	}

	private fun attach(pad: FakePad): FakePad {
		pads += pad
		every { usbManager.openDevice(pad.device) } returns pad.connection
		MidiConnection.initConnection(intent(UsbManager.ACTION_USB_DEVICE_ATTACHED, pad.device), usbManager, context)
		return pad
	}

	/** The MIDI service lists the pad with one input port per cable and opens it at once. */
	@Suppress("DEPRECATION")
	private fun attachThroughMidiService(pad: FakePad): FakePad {
		val info = mockk<MidiDeviceInfo>(relaxed = true)
		val properties = mockk<Bundle>(relaxed = true)
		every { properties.getParcelable<UsbDevice>(MidiDeviceInfo.PROPERTY_USB_DEVICE) } returns pad.device
		every { info.properties } returns properties
		val ports = (0 until pad.model.cables).map { n ->
			mockk<MidiDeviceInfo.PortInfo>(relaxed = true).also {
				every { it.type } returns MidiDeviceInfo.PortInfo.TYPE_INPUT
				every { it.portNumber } returns n
			}
		}
		every { info.inputPortCount } returns ports.size
		every { info.ports } returns ports.toTypedArray()
		midiManager.stubDeviceList() returns arrayOf(info)
		val opened = slot<MidiManager.OnDeviceOpenedListener>()
		every { midiManager.openDevice(info, capture(opened), any()) } answers { opened.captured.onDeviceOpened(pad.midiDevice) }
		return attach(pad)
	}

	private fun intent(action: String?, device: UsbDevice?): Intent {
		val intent = mockk<Intent>(relaxed = true)
		every { intent.action } returns action
		@Suppress("DEPRECATION")
		every { intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) } returns device
		return intent
	}

	class Model(val name: String, val productId: Int, val header: Int, val cables: Int)

	/** A connected pad that records the cable of every mode SysEx written to its OUT endpoint. */
	private class FakePad(val model: Model) {
		private val endpointIn = mockk<UsbEndpoint>(relaxed = true)
		private val endpointOut = mockk<UsbEndpoint>(relaxed = true)
		val device = mockk<UsbDevice>(relaxed = true)
		val connection = mockk<UsbDeviceConnection>(relaxed = true)
		val midiDevice = mockk<MidiDevice>(relaxed = true)
		private val packets = Collections.synchronizedList(mutableListOf<List<Int>>())
		private val portMessages = Collections.synchronizedList(mutableListOf<Pair<Int, List<Int>>>())
		@Volatile private var unplugged = false

		init {
			every { endpointIn.direction } returns UsbConstants.USB_DIR_IN
			every { endpointIn.maxPacketSize } returns 64
			every { endpointOut.direction } returns UsbConstants.USB_DIR_OUT
			val usbInterface = mockk<UsbInterface>(relaxed = true)
			every { usbInterface.interfaceClass } returns UsbConstants.USB_CLASS_AUDIO
			every { usbInterface.interfaceSubclass } returns 3
			every { usbInterface.endpointCount } returns 2
			every { usbInterface.getEndpoint(0) } returns endpointIn
			every { usbInterface.getEndpoint(1) } returns endpointOut
			every { device.deviceId } returns model.productId
			every { device.vendorId } returns 0x1235
			every { device.productId } returns model.productId
			every { device.productName } returns null
			every { device.serialNumber } returns null
			every { device.interfaceCount } returns 1
			every { device.getInterface(0) } returns usbInterface

			every { connection.claimInterface(any(), any()) } returns true
			every { connection.bulkTransfer(endpointIn, any(), any<Int>(), any<Int>()) } answers {
				if (!unplugged) Thread.sleep(IDLE_READ_MS)
				-1
			}
			every { connection.bulkTransfer(endpointOut, any(), any<Int>(), any<Int>()) } answers {
				record(secondArg(), 0, thirdArg())
			}
			every { connection.bulkTransfer(endpointOut, any(), any<Int>(), any<Int>(), any<Int>()) } answers {
				record(secondArg(), thirdArg(), arg(3))
			}

			every { midiDevice.openInputPort(any()) } answers {
				val portNumber = firstArg<Int>()
				mockk<MidiInputPort>(relaxed = true).also { port ->
					every { port.send(any(), any(), any()) } answers {
						val bytes = firstArg<ByteArray>().copyOfRange(secondArg(), secondArg<Int>() + thirdArg<Int>())
						portMessages.add(portNumber to bytes.map { it.toInt() and 0xFF })
					}
				}
			}
		}

		private fun record(buffer: ByteArray, offset: Int, length: Int): Int {
			packets.addAll((offset until offset + length step 4).map { i -> (0..3).map { buffer[i + it].toInt() and 0xFF } })
			return length
		}

		fun unplug() {
			unplugged = true
		}

		/** The cable of each complete mode SysEx (header F0 00 20 29 02 <model>, command 10h or 0Eh). */
		fun modeCables(): List<Int> {
			val cables = mutableListOf<Int>()
			val open = HashMap<Int, MutableList<Int>>()
			for (p in synchronized(packets) { packets.toList() }) {
				val cable = p[0] shr 4
				val payload = when (p[0] and 0x0F) {
					0x04, 0x07 -> p.subList(1, 4)
					0x06 -> p.subList(1, 3)
					0x05 -> p.subList(1, 2)
					else -> continue
				}
				val bytes = open.getOrPut(cable) { mutableListOf() }
				bytes.addAll(payload)
				if (p[0] and 0x0F != 0x04) {
					if (isModeMessage(bytes)) cables += cable
					bytes.clear()
				}
			}
			return cables
		}

		fun midiServiceModePorts(): Set<Int> =
			synchronized(portMessages) { portMessages.toList() }.filter { isModeMessage(it.second) }.map { it.first }.toSet()

		private fun isModeMessage(bytes: List<Int>): Boolean =
			bytes.size == 9 && bytes.subList(0, 6) == listOf(0xF0, 0x00, 0x20, 0x29, 0x02, model.header) &&
				bytes[6] in listOf(0x10, 0x0E)

		fun awaitModeMessages(count: Int) {
			val deadline = System.currentTimeMillis() + TIMEOUT_MS
			while (modeCables().size < count) {
				if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for $count mode SysEx, got cables ${modeCables()}")
				Thread.sleep(10)
			}
		}
	}

	private companion object {
		const val TIMEOUT_MS = 5_000L
		const val IDLE_READ_MS = 100L
		val PRO_MK3 = Model("Pro MK3", productId = 0x0123, header = 0x0E, cables = 3)
		val X = Model("X", productId = 0x0103, header = 0x0C, cables = 2)
		val MINI_MK3 = Model("Mini MK3", productId = 0x0113, header = 0x0D, cables = 2)
	}
}
