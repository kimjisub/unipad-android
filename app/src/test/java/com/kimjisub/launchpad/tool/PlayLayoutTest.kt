package com.kimjisub.launchpad.tool

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayLayoutTest {

	private val strip = 56

	private fun square(width: Int, height: Int, rows: Int = 8, columns: Int = 8, chainRows: Int = 0) =
		PlayLayout.buttonSize(width, height, rows, columns, square = true, chainRows = chainRows, sideStrip = strip)

	/** Pads, chain columns and menu strip side by side; returns the grid's left edge. */
	private fun assertCentredAndClear(width: Int, height: Int, rows: Int = 8, columns: Int = 8, chainRows: Int = 0) {
		val size = square(width, height, rows, columns, chainRows).min
		val gridWidth = size * columns
		val left = PlayLayout.padLeft(width, gridWidth)
		assertTrue("grid off centre in $width x $height", kotlin.math.abs((left + gridWidth / 2.0) - width / 2.0) <= 1)
		assertTrue("left chain column off screen in $width x $height", left - size >= 0)
		assertTrue("right chain column under the menu in $width x $height", left + gridWidth + size <= width - strip)
		assertTrue("grid taller than $width x $height", size * (rows + chainRows) <= height)
	}

	@Test
	fun landscapePhonesAndTablets_centreTheGridClearOfTheMenu() {
		// Content areas in dp-sized px: 20:9 phone (Redmi 13 class), 16:9 phone, tablet.
		for ((w, h) in listOf(878 to 376, 624 to 344, 1264 to 784)) {
			assertCentredAndClear(w, h)
			assertCentredAndClear(w, h, chainRows = 2)
		}
	}

	@Test
	fun portraitAndSplitWindows_shrinkThePadsInsteadOfMovingThem() {
		for ((w, h) in listOf(376 to 878, 784 to 621, 584 to 1264)) assertCentredAndClear(w, h)
		// 800 x 637 dp split window minus the 8 dp padding: width, not height, limits the pads.
		assertEquals((784 - 2 * strip) / 10, square(784, 621).min)
	}

	@Test
	fun landscapeSquarePads_keepTheirHeightLimitedSize() {
		assertEquals(376 / 8, square(878, 376).min)
		assertEquals(344 / 8, square(624, 344).min)
	}

	@Test
	fun nonSquarePads_useRowsAndColumnsOfTheirOwnAxis() {
		val size = PlayLayout.buttonSize(900, 400, rows = 8, columns = 10, square = false, chainRows = 0, sideStrip = strip)
		assertEquals((900 - 2 * strip) / 10, size.x)
		assertEquals(400 / 8, size.y)
		assertCentredAndClear(900, 400, rows = 8, columns = 10)
	}

	@Test
	fun logo_keepsItsFullWidthWhenNothingIsNear() {
		val chains = IntRect(600, 40, 650, 340)
		assertEquals(90, PlayLayout.logoWidth(right = 870, top = 16, maxWidth = 90, aspect = 4.5f, obstacles = listOf(chains), margin = 4))
	}

	@Test
	fun logo_fitsAboveAChainColumnThatReachesTheRightCorner() {
		// Split window: the right chain column ends 49 px from the logo's right edge, its top 26 px below the logo's.
		val chains = IntRect(668, 42, 735, 578)
		val width = PlayLayout.logoWidth(right = 784, top = 16, maxWidth = 90, aspect = 4.5f, obstacles = listOf(chains), margin = 4)
		assertEquals(90, width)
		assertLogoClear(784, 16, width, 4.5f, chains, 4)
	}

	@Test
	fun logo_shrinksToTheWiderOfTheGapsBesideAndAbove() {
		val chains = IntRect(700, 20, 760, 300)
		val width = PlayLayout.logoWidth(right = 800, top = 16, maxWidth = 90, aspect = 4f, obstacles = listOf(chains), margin = 4)
		assertEquals(36, width)
		assertLogoClear(800, 16, width, 4f, chains, 4)
	}

	@Test
	fun logo_isHiddenWhenNoWidthFits() {
		val chains = IntRect(0, 0, 800, 300)
		assertEquals(0, PlayLayout.logoWidth(right = 790, top = 16, maxWidth = 90, aspect = 4f, obstacles = listOf(chains), margin = 4))
	}

	private fun assertLogoClear(right: Int, top: Int, width: Int, aspect: Float, obstacle: IntRect, margin: Int) {
		val logo = IntRect(right - width, top, right, top + (width / aspect).toInt())
		assertTrue("logo $logo touches $obstacle", !logo.overlaps(IntRect(obstacle.left - margin, obstacle.top - margin, obstacle.right + margin, obstacle.bottom + margin)))
	}
}
