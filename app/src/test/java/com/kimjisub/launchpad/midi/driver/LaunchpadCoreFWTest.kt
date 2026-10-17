package com.kimjisub.launchpad.midi.driver

import com.kimjisub.launchpad.manager.LaunchpadColor
import com.kimjisub.launchpad.midi.MidiConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Launchpads running CoreFW (launchpad-core-firmware v1.2.1) boot into its Performance mode: it reads
 * channel 1 notes on its second port (USB-MIDI cable 1) in a drum-rack layout and drops everything on
 * the first port. The expected notes come from the firmware's own table, not from the driver's math.
 */
class LaunchpadCoreFWTest {

	private data class Model(val name: String, val productId: Int, val productName: String, val serial: String, val twoColour: Boolean)

	private val models = listOf(
		Model("Pro MK3", 0x0123, "Launchpad Pro MK3", "COREFW-LPPMK3", twoColour = false),
		Model("X", 0x0103, "Launchpad X", "COREFW-LPX", twoColour = false),
		Model("Mini MK3", 0x0113, "Launchpad Mini MK3", "COREFW-MINIMK3", twoColour = false),
		Model("Pro", 0x0051, "Launchpad Pro", "COREFW-PRO", twoColour = false),
		Model("MK2", 0x0069, "Launchpad MK2", "COREFW-MK2", twoColour = false),
		Model("S", 0x0020, "Launchpad S", "COREFW-LPS", twoColour = true),
		Model("Mini", 0x0036, "Launchpad Mini", "COREFW-MINI", twoColour = true),
	)

	/** The driver the app picks when this model is plugged in with CoreFW installed. */
	private fun driverFor(model: Model): DriverRef =
		requireNotNull(MidiConnection.driverEntryFor(model.productId, model.productName) { model.serial }).factory()

	private class Capture : DriverRef.OnSendSignalListener, DriverRef.OnReceiveSignalListener {
		val sent = mutableListOf<List<Int>>()
		val raw = mutableListOf<ByteArray>()
		val events = mutableListOf<String>()

		override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
			sent += listOf(cmd, sig, note, velocity).map { it.toInt() and 0xFF }
		}

		override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) {
			raw += messages
		}

		override fun onReceived(cmd: Int, sig: Int, note: Int, velocity: Int) {}
		override fun onUnknownReceived(cmd: Int, sig: Int, note: Int, velocity: Int) {
			events += "unknown $note"
		}

		override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) {
			events += "pad $x,$y,$upDown"
		}

		override fun onChainTouch(c: Int, upDown: Boolean) {
			events += "chain $c,$upDown"
		}

		override fun onFunctionKeyTouch(f: Int, upDown: Boolean) {
			events += "key $f,$upDown"
		}
	}

	private fun capture(driver: DriverRef): Capture = Capture().also {
		driver.setOnSendSignalListener(it)
		driver.setOnGetSignalListener(it)
	}

	/** Programmer-layout index (10 * row + column, row 1 at the bottom) of a pad. */
	private fun padIndex(x: Int, y: Int) = 10 * (8 - x) + y + 1

	/** Programmer-layout index of round button f: top left to right, right top down, bottom right to left, left bottom up. */
	private fun ringIndex(f: Int) = when (f) {
		in 0..7 -> 91 + f
		in 8..15 -> 89 - 10 * (f - 8)
		in 16..23 -> 8 - (f - 16)
		else -> 10 + 10 * (f - 24)
	}

	@Test
	fun padLeds_goOutOnTheSecondPortChannelOneAtTheFirmwareNotes() {
		for (model in models) {
			for (x in 0..7) for (y in 0..7) {
				val driver = driverFor(model)
				val out = capture(driver)
				driver.sendPadLed(x, y, 0)
				val note = FIRMWARE_XY_TO_NOTE[padIndex(x, y)]
				assertEquals("${model.name} pad $x,$y", listOf(listOf(0x19, 0x90, note, 0)), out.sent)
			}
		}
	}

	@Test
	fun roundButtonLeds_goOutAtTheFirmwareNotes() {
		for (model in models) {
			for (f in 0 until DriverRef.ROUND_BUTTON_COUNT) {
				val driver = driverFor(model)
				val out = capture(driver)
				driver.sendFunctionKeyLed(f, 0)
				val note = FIRMWARE_XY_TO_NOTE[ringIndex(f)]
				assertEquals("${model.name} round button $f", listOf(listOf(0x19, 0x90, note, 0)), out.sent)
			}
			for (c in 0..23) {
				val driver = driverFor(model)
				val out = capture(driver)
				driver.sendChainLed(c, 0)
				assertEquals("${model.name} chain $c", listOf(listOf(0x19, 0x90, FIRMWARE_XY_TO_NOTE[ringIndex(c + 8)], 0)), out.sent)
			}
		}
	}

	@Test
	fun logoLed_isTheTopRightCornerNote() {
		for (model in models) {
			val driver = driverFor(model)
			val out = capture(driver)
			driver.sendFunctionKeyLed(DriverRef.LOGO_FUNCTION_KEY, 0)
			assertEquals(model.name, listOf(listOf(0x19, 0x90, FIRMWARE_XY_TO_NOTE[99], 0)), out.sent)
		}
	}

	@Test
	fun padPresses_onTheSecondPort_roundTripToTheSamePad() {
		for (model in models) {
			for (x in 0..7) for (y in 0..7) {
				val driver = driverFor(model)
				val out = capture(driver)
				val note = FIRMWARE_XY_TO_NOTE[padIndex(x, y)]
				driver.getSignal(0x19, 0x90.toByte().toInt(), note, 127)
				driver.getSignal(0x19, 0x90.toByte().toInt(), note, 0)
				driver.getSignal(0x18, 0x80.toByte().toInt(), note, 0)
				assertEquals("${model.name} note $note", listOf("pad $x,$y,true", "pad $x,$y,false", "pad $x,$y,false"), out.events)
			}
		}
	}

	@Test
	fun roundButtonPresses_reportTheirFunctionKeyAndChain() {
		for (model in models) {
			for (f in 0 until DriverRef.ROUND_BUTTON_COUNT) {
				val driver = driverFor(model)
				val out = capture(driver)
				driver.getSignal(0x19, 0x90.toByte().toInt(), FIRMWARE_XY_TO_NOTE[ringIndex(f)], 127)
				val expected = if (f >= 8) listOf("chain ${f - 8},true", "key $f,true") else listOf("key $f,true")
				assertEquals("${model.name} round button $f", expected, out.events)
			}
		}
	}

	@Test
	fun firstPortAndOtherMessages_areNotPresses() {
		for (model in models) {
			val driver = driverFor(model)
			val out = capture(driver)
			driver.getSignal(0x09, 0x90.toByte().toInt(), 36, 127)
			driver.getSignal(0x1B, 0xB0.toByte().toInt(), 36, 127)
			driver.getSignal(0x1A, 0xA0.toByte().toInt(), 36, 127)
			assertTrue("${model.name}: ${out.events}", out.events.none { !it.startsWith("unknown") })
		}
	}

	@Test
	fun ledColours_rgbModelsSendThePackCode_twoColourModelsTheLaunchpadSCode() {
		for (model in models) {
			for (code in 0..127) {
				val driver = driverFor(model)
				val out = capture(driver)
				driver.sendPadLed(0, 0, code)
				driver.sendFunctionKeyLed(0, code)
				val expected = if (model.twoColour) LaunchpadColor.SCode[code] else code
				assertEquals("${model.name} code $code", listOf(expected, expected), out.sent.map { it[3] })
			}
		}
	}

	@Test
	fun connecting_sendsNoModeCommand() {
		for (model in models) {
			val driver = driverFor(model)
			val out = capture(driver)
			assertNull(model.name, driver.getInitSysEx())
			driver.initialize()
			assertEquals(model.name, 0, out.raw.size + out.sent.size)
		}
	}

	private companion object {
		/** XY_DR from src/utils/layout.rs of launchpad-core-firmware v1.2.1 (8bab7cbbe400a26176ebd5246aad7cd4a0fde320). */
		val FIRMWARE_XY_TO_NOTE = intArrayOf(
			0, 116, 117, 118, 119, 120, 121, 122, 123, 0, 115, 36, 37, 38, 39, 68, 69, 70, 71, 107, 114,
			40, 41, 42, 43, 72, 73, 74, 75, 106, 113, 44, 45, 46, 47, 76, 77, 78, 79, 105, 112, 48, 49, 50,
			51, 80, 81, 82, 83, 104, 111, 52, 53, 54, 55, 84, 85, 86, 87, 103, 110, 56, 57, 58, 59, 88, 89,
			90, 91, 102, 109, 60, 61, 62, 63, 92, 93, 94, 95, 101, 108, 64, 65, 66, 67, 96, 97, 98, 99,
			100, 26, 28, 29, 30, 31, 32, 33, 34, 35, 27, 0, 12, 13, 14, 15, 16, 17, 18, 19, 0, 0, 0, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
		)
	}
}
