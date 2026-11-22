package com.kimjisub.launchpad.activity

import com.kimjisub.launchpad.midi.MidiConnection.SessionSummary
import com.kimjisub.launchpad.midi.MidiConnectionStatus
import com.kimjisub.launchpad.midi.MidiConnectionStatus.Phase
import com.kimjisub.launchpad.midi.driver.LaunchpadMK2
import com.kimjisub.launchpad.midi.driver.LaunchpadX
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MidiTargetAfterReadTest {
	private val padA = SessionSummary(1, "A", LaunchpadX::class, isPrimary = true)
	private val padB = SessionSummary(2, "B", LaunchpadMK2::class, isPrimary = false)
	private val padC = SessionSummary(3, "C", LaunchpadMK2::class, isPrimary = false)
	private fun detached(id: Int) = MidiConnectionStatus(id, "pad $id", Phase.DISCONNECTED)

	@Test fun connectedTargetStays() {
		assertEquals(2, midiTargetAfterRead(listOf(padA, padB), 2, MidiConnectionStatus(2, "B", Phase.WAITING)))
	}

	@Test fun lastPadLeavingStaysTargetedSoItsDisconnectionShows() {
		assertEquals(1, midiTargetAfterRead(emptyList(), 1, detached(1)))
	}

	// A model pick goes to the pad the status describes; a detached target with another pad
	// connected would show "disconnected" while the pick changed the remaining pad.
	@Test fun targetLeavingWhileAnotherPadStaysMovesToTheRemainingPad() {
		assertEquals(2, midiTargetAfterRead(listOf(padB), 1, detached(1)))
	}

	@Test fun targetLeavingOneOfThreeMovesToThePrimaryPad() {
		assertEquals(1, midiTargetAfterRead(listOf(padA, padC), 2, detached(2)))
	}

	@Test fun withoutPadsOrTargetNothingIsTargeted() {
		assertNull(midiTargetAfterRead(emptyList(), null, MidiConnectionStatus()))
	}
}
