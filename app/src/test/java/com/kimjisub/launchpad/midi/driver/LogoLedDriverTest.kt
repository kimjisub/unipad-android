package com.kimjisub.launchpad.midi.driver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Function key 32 is the logo (keyLED `l`). Each Launchpad whose programmer's reference addresses the
 * logo gets it the way iOS sends it; the others drop it without sending anything.
 */
class LogoLedDriverTest {

	private val logo = 32

	/** Records every message a driver hands to the USB layer, as hex. */
	private class FakeOutput : DriverRef.OnSendSignalListener {
		val sent = mutableListOf<String>()

		override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
			sent += hex(byteArrayOf(cmd, sig, note, velocity))
		}

		override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) {
			for (m in messages) sent += "raw$cableNumber " + hex(m)
		}

		private fun hex(bytes: ByteArray) = bytes.joinToString(" ") { "%02X".format(it) }
	}

	/** F0 00 20 29 02 10 0A 63 vv F7 as USB-MIDI packets on cable 0. */
	private fun proLogo(velocity: String) = listOf("04 F0 00 20", "04 29 02 10", "04 0A 63 $velocity", "05 F7 00 00")

	private fun sent(driver: DriverRef, block: DriverRef.() -> Unit): List<String> {
		val out = FakeOutput()
		driver.setOnSendSignalListener(out)
		driver.block()
		return out.sent
	}

	@Test
	fun launchpadX_logo_isCC99OnTheSecondCable() {
		// Launchpad X programmer's reference: the logo is CC 99. Cable 1 (CIN 0Bh) like its round buttons.
		assertEquals(listOf("1B B0 63 05"), sent(LaunchpadX()) { sendFunctionKeyLed(logo, 5) })
		assertEquals(listOf("1B B0 63 00"), sent(LaunchpadX()) { sendFunctionKeyLed(logo, 0) })
	}

	@Test
	fun launchpadMiniMK3_logo_isCC99LikeLaunchpadX() {
		assertEquals(listOf("1B B0 63 2D"), sent(LaunchpadMiniMK3()) { sendFunctionKeyLed(logo, 45) })
	}

	@Test
	fun launchpadProMK3_logo_isCC99OnTheFirstCable() {
		// Launchpad Pro [MK3] programmer's reference: "Logo (CC 99)".
		assertEquals(listOf("0B B0 63 15"), sent(LaunchpadMK3()) { sendFunctionKeyLed(logo, 21) })
	}

	@Test
	fun launchpadPro_logo_isTheSideLedSysExInTheOrderedQueue() {
		// Launchpad Pro programmer's reference: the side LED is index 99 (63h) of the 0Ah "light LED" SysEx.
		// It goes out as USB-MIDI packets beside the pad notes, never as a raw message sent on its own.
		assertEquals(proLogo("05"), sent(LaunchpadPRO()) { sendFunctionKeyLed(logo, 5) })
		assertEquals(proLogo("00"), sent(LaunchpadPRO()) { sendFunctionKeyLed(logo, 0) })
	}

	@Test
	fun launchpadPro_logoVelocity_staysADataByte() {
		assertEquals(proLogo("7F"), sent(LaunchpadPRO()) { sendFunctionKeyLed(logo, 200) })
	}

	@Test
	fun launchpadPro_logoBetweenPads_keepsItsPlace() {
		val out = sent(LaunchpadPRO()) {
			sendPadLed(0, 0, 5)
			sendFunctionKeyLed(logo, 5)
			sendFunctionKeyLed(logo, 0)
			sendPadLed(0, 0, 0)
		}
		assertEquals(listOf("09 90 51 05") + proLogo("05") + proLogo("00") + "09 90 51 00", out)
	}

	@Test
	fun launchpadPro_initStillGoesOutAsRawSysEx() {
		assertTrue(sent(LaunchpadPRO()) { initialize() }.all { it.startsWith("raw") })
	}

	@Test
	fun launchpadProCFW_logo_isStillNote27() {
		assertEquals(listOf("09 9F 1B 05"), sent(LaunchpadPROCFW()) { sendFunctionKeyLed(logo, 5) })
	}

	@Test
	fun devicesWithoutAnAddressableLogo_sendNothing() {
		for (driver in listOf(LaunchpadMK2(), LaunchpadS(), Matrix(), MidiFighter(), MasterKeyboard(), Noting())) {
			assertEquals(driver::class.simpleName, emptyList<String>(), sent(driver) { sendFunctionKeyLed(logo, 5) })
		}
	}

	@Test
	fun indexesPastTheLogo_sendNothing() {
		for (driver in listOf(LaunchpadX(), LaunchpadMK3(), LaunchpadPRO(), LaunchpadPROCFW())) {
			assertEquals(driver::class.simpleName, emptyList<String>(), sent(driver) { sendFunctionKeyLed(33, 5) })
		}
	}

	@Test
	fun roundButtons_areUnchanged() {
		assertEquals(listOf("1B B0 5B 05"), sent(LaunchpadX()) { sendFunctionKeyLed(0, 5) })
		assertEquals(listOf("0B B0 50 05"), sent(LaunchpadMK3()) { sendFunctionKeyLed(31, 5) })
		assertEquals(listOf("0B B0 59 05"), sent(LaunchpadPRO()) { sendFunctionKeyLed(8, 5) })
	}

	@Test
	fun clearLed_turnsTheLogoOff() {
		assertTrue(sent(LaunchpadX()) { sendClearLed() }.contains("1B B0 63 00"))
		assertTrue(sent(LaunchpadMiniMK3()) { sendClearLed() }.contains("1B B0 63 00"))
		assertTrue(sent(LaunchpadMK3()) { sendClearLed() }.contains("0B B0 63 00"))
		assertEquals(proLogo("00"), sent(LaunchpadPRO()) { sendClearLed() }.takeLast(4))
		assertTrue(sent(LaunchpadPROCFW()) { sendClearLed() }.contains("09 9F 1B 00"))
	}
}
