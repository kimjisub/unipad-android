package com.kimjisub.launchpad.tool

import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayLayoutTest {

	private val strip = 56

	private fun square(width: Int, height: Int, rows: Int = 8, columns: Int = 8, chainRows: Int = 0, margin: Int = strip) =
		PlayLayout.buttonSize(PlayLayout.Area(width, height, margin), rows, columns, square = true, chainRows = chainRows)

	/** Pads, chain columns and menu strip side by side, the grid on the window's centre line. */
	private fun assertCentredAndClear(width: Int, height: Int, rows: Int = 8, columns: Int = 8, chainRows: Int = 0, square: Boolean = true) {
		val size = PlayLayout.buttonSize(PlayLayout.Area(width, height, strip), rows, columns, square, chainRows)
		val gridWidth = size.x * columns
		val left = PlayLayout.padLeft(width, gridWidth)
		val shape = "$width x $height (square = $square, chain rows = $chainRows)"
		assertTrue("grid off centre in $shape", kotlin.math.abs((left + gridWidth / 2.0) - width / 2.0) <= 1)
		assertTrue("chains larger than a pad in $shape", size.chain <= minOf(size.x, size.y))
		assertTrue("left chain column off screen in $shape", left - size.chain >= 0)
		assertTrue("right chain column under the menu in $shape", left + gridWidth + size.chain <= width - strip)
		assertTrue("grid taller than $shape", size.y * rows + size.chain * chainRows <= height)
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
		assertEquals((784 - 2 * strip) / 10, square(784, 621).chain)
	}

	@Test
	fun landscapeSquarePads_keepTheirHeightLimitedSize() {
		assertEquals(376 / 8, square(878, 376).chain)
		assertEquals(344 / 8, square(624, 344).chain)
	}

	@Test
	fun stretchedPads_fillTheAreaLeftBesideTheChainColumns() {
		// 8 x 10 pack: chains match the 50 px tall pads, the pads widen to the space between them.
		val size = PlayLayout.buttonSize(PlayLayout.Area(900, 400, strip), rows = 8, columns = 10, square = false, chainRows = 0)
		assertEquals(400 / 8, size.y)
		assertEquals(400 / 8, size.chain)
		assertEquals((900 - 2 * strip - 2 * size.chain) / 10, size.x)
	}

	@Test
	fun stretchedPads_keepTheChainsClearOfTheMenu() {
		// 2400 x 1080 px phone with the camera on the right (QA's device check) and the usual shapes.
		for ((w, h) in listOf(2316 to 1038, 878 to 376, 624 to 344, 1264 to 784, 376 to 878, 784 to 621)) {
			assertCentredAndClear(w, h, square = false)
			assertCentredAndClear(w, h, square = false, chainRows = 2)
			assertCentredAndClear(w, h, rows = 8, columns = 10, square = false)
		}
	}

	@Test
	fun sideMargin_isTheWiderOfTheCutoutAndTheMenuStripWithWhatIsBesideIt() {
		assertEquals(strip, PlayLayout.sideMargin(insetLeft = 0, insetRight = 0, menuStrip = strip))
		// Camera on the left: the strip on the right already needs more.
		assertEquals(strip, PlayLayout.sideMargin(insetLeft = 52, insetRight = 0, menuStrip = strip))
		assertEquals(80, PlayLayout.sideMargin(insetLeft = 80, insetRight = 0, menuStrip = strip))
		// Camera or a 3-button bar on the right: the strip sits inside it.
		assertEquals(52 + strip, PlayLayout.sideMargin(insetLeft = 0, insetRight = 52, menuStrip = strip))
	}

	@Test
	fun cutoutOnTheLeft_doesNotShrinkA16x9Phone() {
		// 731 x 411 dp landscape minus the 8 dp padding: the strip alone sets the margin.
		val margin = PlayLayout.sideMargin(insetLeft = 52, insetRight = 0, menuStrip = strip)
		assertEquals(395 / 8, square(715, 395, margin = margin).chain)
		assertCentredAndClear(715, 395)
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
