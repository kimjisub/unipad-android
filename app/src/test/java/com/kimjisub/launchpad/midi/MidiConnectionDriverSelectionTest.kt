package com.kimjisub.launchpad.midi

import com.kimjisub.launchpad.midi.driver.LaunchpadCoreFW
import com.kimjisub.launchpad.midi.driver.LaunchpadMK2
import com.kimjisub.launchpad.midi.driver.LaunchpadMK3
import com.kimjisub.launchpad.midi.driver.LaunchpadMiniMK3
import com.kimjisub.launchpad.midi.driver.LaunchpadPRO
import com.kimjisub.launchpad.midi.driver.LaunchpadPROCFW
import com.kimjisub.launchpad.midi.driver.LaunchpadS
import com.kimjisub.launchpad.midi.driver.LaunchpadX
import com.kimjisub.launchpad.midi.driver.MidiFighter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** CoreFW keeps the official product id and name; only a "COREFW-" serial number may change the driver. */
class MidiConnectionDriverSelectionTest {

	private val officialDrivers = mapOf(
		0x0123 to LaunchpadMK3::class,
		0x0103 to LaunchpadX::class,
		0x0113 to LaunchpadMiniMK3::class,
		0x0051 to LaunchpadPRO::class,
		0x0069 to LaunchpadMK2::class,
		0x0020 to LaunchpadS::class,
		0x0036 to LaunchpadS::class,
	)

	private fun driverClass(pid: Int, productName: String? = null, serial: () -> String?) =
		MidiConnection.driverEntryFor(pid, productName, serial)?.factory?.invoke()?.let { it::class }

	@Test
	fun coreFwSerial_picksTheCoreFwDriverForEveryModel() {
		for (pid in officialDrivers.keys) {
			assertEquals("PID $pid", LaunchpadCoreFW::class, driverClass(pid) { "COREFW-LPX" })
		}
		assertEquals("Launchpad X (CoreFW)", MidiConnection.driverEntryFor(0x0103, "Launchpad X") { "COREFW-LPX" }?.name)
	}

	@Test
	fun missingOrOtherSerial_keepsTheOfficialDriver() {
		for ((pid, official) in officialDrivers) {
			for (serial in listOf(null, "", "LPX12345", "corefw-lpx", " COREFW-LPX", "COREFW")) {
				assertEquals("PID $pid serial $serial", official, driverClass(pid) { serial })
			}
		}
	}

	@Test
	fun otherDevices_neverHaveTheirSerialRead() {
		var reads = 0
		val serial = { reads++; "COREFW-LPX" }
		assertEquals(MidiFighter::class, driverClass(8, serial = serial))
		assertEquals(LaunchpadPROCFW::class, driverClass(0x0051, "Launchpad Open", serial))
		assertNull(driverClass(0x1234, serial = serial))
		assertEquals(0, reads)
	}

	@Test
	fun officialDeviceIds_stillMatchTheirModel() {
		// A Launchpad set to device id 16 reports base id + 15.
		assertEquals(LaunchpadX::class, driverClass(0x0112) { null })
		assertEquals(LaunchpadCoreFW::class, driverClass(0x0112) { "COREFW-LPX" })
	}
}
