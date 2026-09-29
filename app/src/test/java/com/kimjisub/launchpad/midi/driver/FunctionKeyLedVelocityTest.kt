package com.kimjisub.launchpad.midi.driver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A pack line such as `o mc 1 ff0000 200` hands the driver a velocity outside the 7-bit MIDI data range.
 * Pad LEDs already clamp it; the chain/function-key LEDs must too, or the data byte goes out with its
 * high bit set (0xC8 for 200, 0xFF for -1) and the pad reads it as a status byte.
 */
class FunctionKeyLedVelocityTest {

	private class Sent(val cmd: Byte, val sig: Byte, val note: Byte, val velocity: Byte)

	private fun record(driver: DriverRef): List<Sent> {
		val sent = mutableListOf<Sent>()
		driver.setOnSendSignalListener(object : DriverRef.OnSendSignalListener {
			override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
				sent += Sent(cmd, sig, note, velocity)
			}

			override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) {}
		})
		return sent
	}

	private val drivers: Map<String, () -> DriverRef> = mapOf(
		"MK2" to ::LaunchpadMK2,
		"MK3" to ::LaunchpadMK3,
		"PRO" to ::LaunchpadPRO,
		"X" to ::LaunchpadX,
		"MiniMK3" to ::LaunchpadMiniMK3,
		"Matrix" to ::Matrix,
	)

	@Test
	fun functionKeyLed_outOfRangeVelocity_isClampedToMidiDataRange() {
		for ((name, create) in drivers) {
			for ((velocity, expected) in listOf(200 to 127, 128 to 127, -1 to 0, 127 to 127, 0 to 0, 64 to 64)) {
				val driver = create()
				val sent = record(driver)
				driver.sendFunctionKeyLed(0, velocity)
				assertEquals("$name sends one packet", 1, sent.size)
				assertEquals("$name function key LED, velocity $velocity", expected, sent[0].velocity.toInt())
			}
		}
	}

	@Test
	fun chainLed_outOfRangeVelocity_isClampedToMidiDataRange() {
		for ((name, create) in drivers) {
			val driver = create()
			val sent = record(driver)
			driver.sendChainLed(0, 200)
			assertEquals("$name sends one packet", 1, sent.size)
			assertEquals("$name chain LED", 127, sent[0].velocity.toInt())
		}
	}

	@Test
	fun padLed_outOfRangeVelocity_matchesFunctionKeyBehaviour() {
		for ((name, create) in drivers) {
			val driver = create()
			val sent = record(driver)
			driver.sendPadLed(0, 0, 200)
			assertTrue("$name pad LED velocity stays a data byte", sent[0].velocity in 0..127)
		}
	}
}
