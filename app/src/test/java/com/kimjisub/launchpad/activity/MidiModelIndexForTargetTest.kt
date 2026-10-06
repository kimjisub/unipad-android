package com.kimjisub.launchpad.activity

import com.kimjisub.launchpad.midi.MidiConnection.SessionSummary
import com.kimjisub.launchpad.midi.driver.LaunchpadMK2
import com.kimjisub.launchpad.midi.driver.LaunchpadMiniMK3
import com.kimjisub.launchpad.midi.driver.LaunchpadX
import com.kimjisub.launchpad.midi.driver.Noting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
