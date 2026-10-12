package com.kimjisub.launchpad.activity

import com.kimjisub.launchpad.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MidiConnectionHelpTest {
	@Test fun miniInstructionsRequireAnExplicitChoice() {
		assertNull(midiHelpModelNote(R.string.midi_lp_mini_mk3, false))
		assertEquals(R.string.midi_help_mini, midiHelpModelNote(R.string.midi_lp_mini_mk3, true))
	}

	@Test fun originalProAndProMk3AreDistinguished() {
		for (model in listOf(R.string.midi_lp_pro, R.string.midi_lp_mk3)) {
			assertEquals(R.string.midi_help_pro, midiHelpModelNote(model, true))
			assertEquals(R.string.midi_help_pro, midiHelpModelNote(model, false))
		}
	}

	@Test fun commonModelsDoNotReceiveMiniOrSubstitutionAdvice() {
		for (model in listOf(R.string.midi_lp_s, R.string.midi_lp_mk2, R.string.midi_lp_x)) {
			assertNull(midiHelpModelNote(model, true))
			assertNull(midiHelpModelNote(model, false))
		}
	}

	@Test fun otherAndUnknownModelsDoNotOfferSubstitutes() {
		for (model in listOf(R.string.midi_lp_pro_cfw, R.string.midi_midi_fighter,
			R.string.midi_matrix, R.string.midi_master_keyboard, android.R.string.unknownName)) {
			assertEquals(R.string.midi_help_other, midiHelpModelNote(model, true))
			assertEquals(R.string.midi_help_other, midiHelpModelNote(model, false))
		}
	}
}
