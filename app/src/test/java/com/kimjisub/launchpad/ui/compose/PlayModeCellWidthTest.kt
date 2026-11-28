package com.kimjisub.launchpad.ui.compose

import androidx.compose.ui.unit.Density
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayModeCellWidthTest {
	@Test
	fun measurementFitsTheSmallestWeightedCellAtFractionalDensity() {
		// At 440dpi, rounded padding is 8px per side and each gap is 6px.
		// The row distributes its 610px as 204/203/203, never three 204px cells.
		assertEquals(203, Density(2.75f).playModeCellWidth(638, 3))
		assertEquals(202, Density(2.75f).playModeCellWidth(636, 3))
		// Padding rounds up at 240dpi: 5px per side, plus two 3px gaps.
		assertEquals(101, Density(1.5f).playModeCellWidth(320, 3))
	}

	@Test
	fun exactPixelWidthsAndNarrowConstraintsRemainUsable() {
		assertEquals(100, Density(2f).playModeCellWidth(320, 3))
		assertEquals(0, Density(2.75f).playModeCellWidth(20, 3))
	}
}
