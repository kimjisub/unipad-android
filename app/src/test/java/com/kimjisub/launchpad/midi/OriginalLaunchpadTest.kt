package com.kimjisub.launchpad.midi

import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.LaunchpadS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Original Launchpad X-Y layout and two-colour LEDs, through USB driver selection. */
class OriginalLaunchpadTest {
	private fun driver(): DriverRef = requireNotNull(
		MidiConnection.driverEntryFor(0x000e, "Launchpad") { error("No serial lookup is needed") },
	) { "The original Launchpad must have a driver" }.factory()

	@Test fun originalUsbIdSelectsTheCompatibleDriverWithoutReadingTheSerial() {
		for (name in listOf(null, "Launchpad", "Launchpad MIDI 1", "Novation Launchpad")) {
			val entry = MidiConnection.driverEntryFor(0x000e, name) { error("No serial lookup is needed") }
			assertEquals("USB identity must also work without a product name", LaunchpadS::class, entry?.factory?.invoke()?.let { it::class })
		}
		assertNull(MidiConnection.driverEntryFor(0x000d, "Other MIDI device") { null })
		assertNull(MidiConnection.driverEntryFor(0x000f, "Other MIDI device") { null })
	}

	@Test fun everyGridPadUsesSixteenTimesTheRowPlusTheColumn() {
		val driver = driver()
		val input = Input()
		val output = Output()
		driver.setOnGetSignalListener(input).setOnSendSignalListener(output)
		for (row in 0..7) for (column in 0..7) {
			val note = 16 * row + column
			driver.getSignal(9, 0x90, note, 127)
			driver.getSignal(9, 0x90, note, 0)
			assertEquals(listOf(listOf(row, column, 1, 127), listOf(row, column, 0, 0)), input.pads)
			input.pads.clear()
			driver.sendPadLed(row, column, 6)
			assertEquals(listOf(listOf(9, 0x90, note, 3)), output.packets)
			output.packets.clear()
		}
	}

	@Test fun topControlsAndRightNotesKeepTheirDocumentedAddresses() {
		val driver = driver()
		val input = Input()
		val output = Output()
		driver.setOnGetSignalListener(input).setOnSendSignalListener(output)
		for (index in 0..7) {
			driver.getSignal(11, 0xb0, 104 + index, 127)
			driver.getSignal(11, 0xb0, 104 + index, 0)
			assertEquals(listOf(listOf(index, 1), listOf(index, 0)), input.functions)
			input.functions.clear()
			driver.sendFunctionKeyLed(index, 6)
			assertEquals(listOf(listOf(11, 0xb0, 104 + index, 3)), output.packets)
			output.packets.clear()

			val note = 16 * index + 8
			driver.getSignal(9, 0x90, note, 127)
			driver.getSignal(9, 0x90, note, 0)
			assertEquals(listOf(listOf(index, 1), listOf(index, 0)), input.chains)
			assertEquals(listOf(listOf(index + 8, 1), listOf(index + 8, 0)), input.functions)
			input.chains.clear()
			input.functions.clear()
			driver.sendChainLed(index, 6)
			assertEquals(listOf(listOf(9, 0x90, note, 3)), output.packets)
			output.packets.clear()
		}
	}

	@Test fun packColoursUseRedGreenIntensityValuesInsteadOfRgbPaletteIndices() {
		val driver = driver()
		val output = Output()
		driver.setOnSendSignalListener(output)
		// Red, green, amber and off in the existing two-colour mapping. Bits 5..4 are green,
		// bits 1..0 are red, and bits 3..2 select the device's buffer behaviour.
		for ((packCode, deviceCode) in listOf(6 to 3, 21 to 56, 3 to 63, 0 to 0)) {
			driver.sendPadLed(0, 0, packCode)
			assertEquals(listOf(listOf(9, 0x90, 0, deviceCode)), output.packets)
			output.packets.clear()
		}
	}

	private class Input : DriverRef.OnReceiveSignalListener {
		val pads = ArrayList<List<Int>>()
		val chains = ArrayList<List<Int>>()
		val functions = ArrayList<List<Int>>()
		override fun onReceived(cmd: Int, sig: Int, note: Int, velocity: Int) {}
		override fun onUnknownReceived(cmd: Int, sig: Int, note: Int, velocity: Int) {}
		override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) { pads.add(listOf(x, y, if (upDown) 1 else 0, velocity)) }
		override fun onChainTouch(c: Int, upDown: Boolean) { chains.add(listOf(c, if (upDown) 1 else 0)) }
		override fun onFunctionKeyTouch(f: Int, upDown: Boolean) { functions.add(listOf(f, if (upDown) 1 else 0)) }
	}

	private class Output : DriverRef.OnSendSignalListener {
		val packets = ArrayList<List<Int>>()
		override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) { packets.add(listOf(cmd, sig, note, velocity).map { it.toInt() and 0xff }) }
		override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) { error("Unexpected SysEx") }
	}
}
