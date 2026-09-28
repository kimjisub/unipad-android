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
import android.media.midi.MidiManager
import com.kimjisub.launchpad.midi.driver.DriverRef
import io.mockk.every
import io.mockk.mockk
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The original Launchpad Pro lights its logo with a SysEx while the pads go out as notes. Both must
 * reach the USB endpoint in the order they were sent, or an "on" overtaken by the "off" after it
 * leaves the logo lit. Each fake connection holds one chosen logo "on" transfer back, which is the
 * scheduling a parallel sender can hit; an ordered sender still has to deliver the "off" last.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MidiConnectionLogoOrderTest {

	private lateinit var mainDispatcher: ExecutorCoroutineDispatcher
	private val uncaught: MutableList<Throwable> = Collections.synchronizedList(mutableListOf())
	private var previousUncaughtHandler: Thread.UncaughtExceptionHandler? = null

	private lateinit var usbManager: UsbManager
	private lateinit var context: Context
	private var dualPadMode = false
	private val pads = mutableListOf<FakePad>()

	@Before
	fun setUp() {
		mainDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
		Dispatchers.setMain(mainDispatcher)
		previousUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
		Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught.add(e) }

		usbManager = mockk(relaxed = true)
		every { usbManager.hasPermission(any<UsbDevice>()) } returns true
		every { usbManager.deviceList } returns HashMap()
		val midiManager = mockk<MidiManager>(relaxed = true)
		every { midiManager.devices } returns emptyArray()
		val prefs = mockk<SharedPreferences>(relaxed = true)
		every { prefs.getBoolean(any(), any()) } answers { dualPadMode }
		context = mockk(relaxed = true)
		every { context.applicationContext } returns context
		every { context.getSystemService(Context.MIDI_SERVICE) } returns midiManager
		every { context.getSharedPreferences(any(), any()) } returns prefs

		MidiConnection.initConnection(intent(action = null, device = null), usbManager, context)
	}

	@After
	fun tearDown() {
		for (pad in pads) pad.unplug()
		for (pad in pads) verify(timeout = TIMEOUT_MS) { pad.connection.close() }
		// Anything written between two transfers would land inside an unfinished SysEx.
		for (pad in pads) assertEquals("transfers that leave a SysEx open", emptyList<String>(), pad.transfersLeavingSysExOpen())
		Thread.setDefaultUncaughtExceptionHandler(previousUncaughtHandler)
		Dispatchers.resetMain()
		mainDispatcher.close()
		assertTrue("uncaught: $uncaught", uncaught.isEmpty())
	}

	@Test
	fun logoOnThenOff_endsOff() {
		val pad = attach(FakePad(deviceId = 1, holdLogo = 5))
		val driver = MidiConnection.driver

		driver.sendFunctionKeyLed(LOGO, 5)
		driver.sendFunctionKeyLed(LOGO, 0)

		pad.awaitLogoWrites(2)
		assertEquals(listOf(5, 0), pad.logoHistory())
	}

	@Test
	fun clearDuringRepeatedPlay_leavesLogoAndPadsOff() {
		// PlayActivity.onPause (leaving the screen) and LED off both end with driver.sendClearLed().
		val pad = attach(FakePad(deviceId = 1, holdLogo = 9))
		val driver = MidiConnection.driver

		repeat(20) {
			driver.sendPadLed(it % 8, it / 8, 21)
			driver.sendFunctionKeyLed(LOGO, 5)
			driver.sendPadLed(it % 8, it / 8, 0)
			driver.sendFunctionKeyLed(LOGO, 0)
		}
		driver.sendPadLed(3, 3, 45)
		driver.sendFunctionKeyLed(LOGO, 9)
		driver.sendClearLed()

		pad.awaitLogoWrites(42)
		assertEquals(0, pad.logoHistory().last())
		pad.awaitPadNotes(41 + 64)
		assertTrue("a pad was left lit: ${pad.litNotes()}", pad.litNotes().isEmpty())
	}

	@Test
	fun twoPads_eachGetsOnlyItsOwnLogoInOrder() {
		dualPadMode = true
		val first = attach(FakePad(deviceId = 1, holdLogo = 5))
		val second = attach(FakePad(deviceId = 2, holdLogo = 5))
		// Switching to the two-pad driver clears the first pad, logo included.
		first.awaitLogoWrites(1)
		val driver = MidiConnection.driver

		driver.sendFunctionKeyLed(LOGO, 5)
		driver.sendFunctionKeyLed(LOGO, 0)

		first.awaitLogoWrites(3)
		second.awaitLogoWrites(2)
		assertEquals(listOf(0, 5, 0), first.logoHistory())
		assertEquals(listOf(5, 0), second.logoHistory())
	}

	@Test
	fun replacedPad_neverReceivesTheOldPadsLogo() {
		// Single-pad mode: a new attach tears the old session down while its logo "on" is held.
		val old = attach(FakePad(deviceId = 1, holdLogo = 5, holdMs = 300))
		MidiConnection.driver.sendFunctionKeyLed(LOGO, 5)
		old.awaitHeld()

		val new = attach(FakePad(deviceId = 2))
		verify(timeout = TIMEOUT_MS) { old.connection.close() }
		MidiConnection.driver.sendFunctionKeyLed(LOGO, 0)

		new.awaitLogoWrites(1)
		Thread.sleep(400)
		assertEquals(listOf(0), new.logoHistory())
	}

	private fun attach(pad: FakePad): FakePad {
		pads += pad
		every { usbManager.openDevice(pad.device) } returns pad.connection
		MidiConnection.initConnection(intent(UsbManager.ACTION_USB_DEVICE_ATTACHED, pad.device), usbManager, context)
		return pad
	}

	private fun intent(action: String?, device: UsbDevice?): Intent {
		val intent = mockk<Intent>(relaxed = true)
		every { intent.action } returns action
		@Suppress("DEPRECATION")
		every { intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE) } returns device
		return intent
	}

	/**
	 * An original Launchpad Pro (PID 0x0051) whose OUT endpoint records every USB-MIDI packet in
	 * arrival order. The first transfer carrying logo velocity [holdLogo] waits until a later logo
	 * message has been written, or [holdMs] at most.
	 */
	private class FakePad(deviceId: Int, private val holdLogo: Int = -1, private val holdMs: Long = 500) {
		private val endpointIn = mockk<UsbEndpoint>(relaxed = true)
		private val endpointOut = mockk<UsbEndpoint>(relaxed = true)
		val device = mockk<UsbDevice>(relaxed = true)
		val connection = mockk<UsbDeviceConnection>(relaxed = true)

		private val packets = Collections.synchronizedList(mutableListOf<List<Int>>())
		private val transfers = Collections.synchronizedList(mutableListOf<List<List<Int>>>())
		private val held = CountDownLatch(1)
		private val laterLogoWritten = CountDownLatch(1)
		@Volatile private var holdArmed = holdLogo >= 0
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
			every { device.deviceId } returns deviceId
			every { device.productId } returns 0x0051
			every { device.productName } returns null
			every { device.interfaceCount } returns 1
			every { device.getInterface(0) } returns usbInterface

			every { connection.claimInterface(any(), any()) } returns true
			// An idle pad: each read times out slowly, so the receive loop keeps the session open.
			every { connection.bulkTransfer(endpointIn, any(), any<Int>(), any<Int>()) } answers {
				if (!unplugged) Thread.sleep(IDLE_READ_MS)
				-1
			}
			every { connection.bulkTransfer(endpointOut, any(), any<Int>(), any<Int>()) } answers {
				write(secondArg(), 0, thirdArg())
			}
			every { connection.bulkTransfer(endpointOut, any(), any<Int>(), any<Int>(), any<Int>()) } answers {
				write(secondArg(), thirdArg(), arg(3))
			}
		}

		private fun write(buffer: ByteArray, offset: Int, length: Int): Int {
			val chunk = (offset until offset + length step 4).map { i -> (0..3).map { buffer[i + it].toInt() and 0xFF } }
			val logos = logoVelocities(chunk)
			if (holdArmed && holdLogo in logos) {
				holdArmed = false
				held.countDown()
				laterLogoWritten.await(holdMs, TimeUnit.MILLISECONDS)
			} else if (logos.isNotEmpty() && held.count == 0L) {
				laterLogoWritten.countDown()
			}
			synchronized(packets) {
				packets.addAll(chunk)
				transfers.add(chunk)
			}
			return length
		}

		fun unplug() {
			unplugged = true
		}

		fun transfersLeavingSysExOpen(): List<String> = synchronized(packets) { transfers.toList() }
			.filter { chunk -> chunk.lastOrNull { it[0] and 0x0F in 0x04..0x07 }?.let { it[0] and 0x0F == 0x04 } == true }
			.map { chunk -> chunk.joinToString(" | ") { p -> p.joinToString(" ") { "%02X".format(it) } } }

		fun awaitHeld() = assertTrue(held.await(TIMEOUT_MS, TimeUnit.MILLISECONDS))

		fun logoHistory(): List<Int> = logoVelocities(synchronized(packets) { packets.toList() })

		fun awaitLogoWrites(count: Int) = await({ "$count logo writes, got ${logoHistory()}" }) { logoHistory().size >= count }

		fun awaitPadNotes(count: Int) = await({ "$count pad notes, got ${padNotes().size}" }) { padNotes().size >= count }

		/** Pad notes (header 09h) whose last velocity was not 0. */
		fun litNotes(): Set<Int> = padNotes().groupBy({ it.first }, { it.second }).filterValues { it.last() != 0 }.keys

		private fun padNotes(): List<Pair<Int, Int>> =
			synchronized(packets) { packets.toList() }.filter { it[0] == 0x09 && it[1] == 0x90 }.map { it[2] to it[3] }

		private fun await(what: () -> String, done: () -> Boolean) {
			val deadline = System.currentTimeMillis() + TIMEOUT_MS
			while (!done()) {
				if (System.currentTimeMillis() > deadline) throw AssertionError("timed out waiting for ${what()}")
				Thread.sleep(10)
			}
		}

		private companion object {
			const val IDLE_READ_MS = 100L

			/** Velocities of the Pro's "light LED 99" SysEx (F0 00 20 29 02 10 0A 63 vv F7), in packet order. */
			fun logoVelocities(packets: List<List<Int>>): List<Int> {
				val bytes = packets.flatMap { p ->
					when (p[0] and 0x0F) {
						0x04, 0x07 -> p.subList(1, 4)
						0x06 -> p.subList(1, 3)
						0x05 -> p.subList(1, 2)
						else -> listOf(-1)
					}
				}
				val header = listOf(0xF0, 0x00, 0x20, 0x29, 0x02, 0x10, 0x0A, 0x63)
				return bytes.indices.filter { i -> i + 9 < bytes.size && bytes.subList(i, i + 8) == header && bytes[i + 9] == 0xF7 }
					.map { bytes[it + 8] }
			}
		}
	}

	private companion object {
		const val LOGO = DriverRef.LOGO_FUNCTION_KEY
		const val TIMEOUT_MS = 5_000L
	}
}
