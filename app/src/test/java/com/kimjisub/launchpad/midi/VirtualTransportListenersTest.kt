package com.kimjisub.launchpad.midi

import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.Matrix
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class VirtualTransportListenersTest {
	@After fun clearController() { MidiConnection.controller = null }

	/** The app connection is often the first MIDI connection of the process: no USB device was ever attached. */
	@Test fun aProcessThatNeverSawUsbStillDeliversAppInputToTheController() {
		// A fresh process: the listeners a USB attach would have installed do not exist yet.
		for (name in listOf("onCycleListener", "onSendSignalListener", "onReceiveSignalListener")) {
			MidiConnection::class.java.getDeclaredField(name).apply { isAccessible = true }.set(null, null)
		}
		val events = mutableListOf<String>()
		MidiConnection.controller = object : MidiController() {
			override fun onAttach() { events += "attach" }
			override fun onDetach() { events += "detach" }
			override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) { events += "pad $x $y $upDown" }
			override fun onFunctionKeyTouch(f: Int, upDown: Boolean) { }
			override fun onChainTouch(c: Int, upDown: Boolean) { }
			override fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velocity: Int) { }
		}
		val driver = Matrix()
		val output = object : DriverRef.OnSendSignalListener {
			override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) = Unit
			override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) = Unit
		}
		val attachment = MidiConnection.attachTransport("App", driver, output)
		try {
			val command = (driver.receiveCableNumber shl 4) or 9
			driver.getSignal(command, 0x91, 36, 100)
			driver.getSignal(command, 0x91, 36, 0)
			assertEquals(listOf("attach", "pad 7 0 true", "pad 7 0 false"), events)
		} finally { attachment.close() }
		assertEquals("detach", events.last())
	}
}
