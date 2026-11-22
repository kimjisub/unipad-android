package com.kimjisub.launchpad.midi

import com.kimjisub.launchpad.midi.driver.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class VirtualMidiLayoutTest(
	private val driver: DriverRef,
	private val status: Int,
	private val note: Int,
	private val row: Int,
	private val column: Int,
) {
	@Test fun rawStreamPressNoteOffAndZeroVelocityReachTheSelectedLayout() = runTest {
		val listener = mockk<DriverRef.OnReceiveSignalListener>(relaxed = true)
		driver.setOnGetSignalListener(listener)
		var receive: ((ByteArray, Int, Int) -> Unit)? = null
		val device = mockk<VirtualMidiBackend.Device>(relaxed = true)
		every { device.openPerformancePort(0, any()) } answers { receive = secondArg(); mockk(relaxed = true) }
		coEvery { device.isAlive() } returns true
		val backend = mockk<VirtualMidiBackend>(relaxed = true)
		coEvery { backend.open(7) } returns device
		val descriptor = VirtualMidiDevice(7, "Pads", "", "", listOf(VirtualMidiPort(0, "Pads")), emptyList())
		val dispatcher = StandardTestDispatcher(testScheduler)
		val connection = VirtualMidiConnection(backend, backgroundScope, dispatcher, dispatcher, MidiTransportGate()) { _,_,_ -> AutoCloseable { } }
		connection.connect(VirtualMidiSelection(descriptor, 0, null), driver)
		runCurrent()
		try {
			val bytes = byteArrayOf(status.toByte(), note.toByte(), 100, (status - 0x10).toByte(), note.toByte(), 64, status.toByte(), note.toByte(), 0)
			receive!!(bytes, 0, bytes.size)
			runCurrent()
			verify(exactly = 1) { listener.onPadTouch(row, column, true, 100) }
			verify(exactly = 2) { listener.onPadTouch(row, column, false, 0) }
			assertEquals(VirtualMidiConnection.State.Connected(false), connection.state)
		} finally { connection.disconnect(); runCurrent() }
	}

	companion object {
		@JvmStatic @Parameterized.Parameters(name = "{0}")
		fun layouts(): List<Array<Any>> = listOf(
			arrayOf(LaunchpadS(), 0x90, 0, 0, 0),
			arrayOf(LaunchpadMK2(), 0x90, 81, 0, 0),
			arrayOf(LaunchpadPRO(), 0x90, 81, 0, 0),
			arrayOf(LaunchpadMK3(), 0x90, 81, 0, 0),
			arrayOf(LaunchpadX(), 0x90, 81, 0, 0),
			arrayOf(LaunchpadMiniMK3(), 0x90, 81, 0, 0),
			arrayOf(Matrix(), 0x91, 64, 0, 0),
			arrayOf(MasterKeyboard(), 0x90, 64, 0, 0),
			arrayOf(MidiFighter(), 0x92, 64, 0, 0),
			arrayOf(LaunchpadPROCFW(), 0x9f, 64, 0, 0),
			arrayOf(LaunchpadCoreFW(), 0x90, 64, 0, 0),
		)
	}
}
