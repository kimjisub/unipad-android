package com.kimjisub.design.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlideTouchOverlayViewTest {

	@Test
	fun cellAt_mapsCornersOfAnEightByEightGrid() {
		assertEquals(0, SlideTouchOverlayView.cellAt(0f, 0f, 800, 800, 8, 8))
		assertEquals(7, SlideTouchOverlayView.cellAt(799f, 0f, 800, 800, 8, 8))
		assertEquals(56, SlideTouchOverlayView.cellAt(0f, 799f, 800, 800, 8, 8))
		assertEquals(63, SlideTouchOverlayView.cellAt(799f, 799f, 800, 800, 8, 8))
	}

	@Test
	fun cellAt_usesRowTimesColsPlusCol_forNonSquareGrids() {
		// 4 rows x 6 columns over 600x400: cell 100x100; point (250, 150) -> row 1, col 2
		assertEquals(1 * 6 + 2, SlideTouchOverlayView.cellAt(250f, 150f, 600, 400, 4, 6))
	}

	@Test
	fun cellAt_isNullOutsideTheGridOrWithoutAGrid() {
		assertNull(SlideTouchOverlayView.cellAt(-1f, 10f, 800, 800, 8, 8))
		assertNull(SlideTouchOverlayView.cellAt(800f, 10f, 800, 800, 8, 8))
		assertNull(SlideTouchOverlayView.cellAt(10f, 10f, 800, 800, 0, 8))
		assertNull(SlideTouchOverlayView.cellAt(10f, 10f, 0, 800, 8, 8))
	}
}
