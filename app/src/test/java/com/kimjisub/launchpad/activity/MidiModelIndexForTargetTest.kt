package com.kimjisub.launchpad.activity

import com.kimjisub.launchpad.midi.MidiConnection.SessionSummary
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.LaunchpadCoreFW
import com.kimjisub.launchpad.midi.driver.LaunchpadMK2
import com.kimjisub.launchpad.midi.driver.LaunchpadMiniMK3
import com.kimjisub.launchpad.midi.driver.LaunchpadS
import com.kimjisub.launchpad.midi.driver.LaunchpadX
import com.kimjisub.launchpad.midi.driver.Noting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiModelIndexForTargetTest {
	private val padA = SessionSummary(1, "A", LaunchpadX::class, isPrimary = true)
	private val padB = SessionSummary(2, "B", LaunchpadMK2::class, isPrimary = false)

	@Test fun eachTargetedPadHighlightsItsOwnModel() {
		val sessions = listOf(padA, padB)
		assertEquals(4, midiModelIndexForTarget(sessions, 1, Noting::class))
		assertEquals(1, midiModelIndexForTarget(sessions, 2, Noting::class))
	}

	@Test fun modelChosenForOnePadIsKeptAfterTargetingTheOtherAndBack() {
		val afterChoosingMini = listOf(padA.copy(driverClass = LaunchpadMiniMK3::class), padB)
		assertEquals(1, midiModelIndexForTarget(afterChoosingMini, 2, Noting::class))
		assertEquals(5, midiModelIndexForTarget(afterChoosingMini, 1, Noting::class))
	}

	@Test fun withoutATargetedPadTheCurrentDriverIsHighlighted() {
		assertEquals(5, midiModelIndexForTarget(emptyList(), null, LaunchpadMiniMK3::class))
		assertEquals(5, midiModelIndexForTarget(listOf(padA), 9, LaunchpadMiniMK3::class))
	}

	@Test fun driverOutsideTheModelListHighlightsNothing() {
		assertNull(midiModelIndexForTarget(emptyList(), null, Noting::class))
	}

	@Test fun padDetectedAsCoreFwHighlightsTheCoreFwModel() {
		// The S and the first Mini get the same driver class with their own colour codes.
		val coreFwPad = SessionSummary(3, "Launchpad X", LaunchpadCoreFW::class, isPrimary = true)
		val index = midiModelIndexForTarget(listOf(coreFwPad, padB), 3, Noting::class)
		assertEquals(10, index)
		assertEquals(10, midiModelIndexForTarget(emptyList(), null, LaunchpadCoreFW::class))
	}

	@Test fun pickingTheHighlightedCoreFwModelKeepsTheRedGreenCodesOfTheS() {
		val detected = LaunchpadCoreFW(LaunchpadS::sCode)
		val picked = midiDriverForPick(10, detected)
		assertEquals(listOf(LaunchpadS.sCode(5)), padLedVelocities(picked, 5))
	}

	@Test fun pickingAnotherModelGivesThatModelsDriver() {
		assertTrue(midiDriverForPick(4, LaunchpadCoreFW(LaunchpadS::sCode)) is LaunchpadX)
		assertTrue(midiDriverForPick(10, LaunchpadX()) is LaunchpadCoreFW)
		assertTrue(midiDriverForPick(10, null) is LaunchpadCoreFW)
	}

	private fun padLedVelocities(driver: DriverRef, velocity: Int): List<Int> {
		val sent = mutableListOf<Int>()
		driver.setOnSendSignalListener(object : DriverRef.OnSendSignalListener {
			override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) {
				sent += velocity.toInt() and 0xFF
			}

			override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) {}
		})
		driver.sendPadLed(0, 0, velocity)
		return sent
	}

}
