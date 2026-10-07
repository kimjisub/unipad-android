package com.kimjisub.launchpad.tool

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
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

	private val shapes = listOf(878 to 376, 624 to 344, 1264 to 784, 376 to 878, 784 to 621, 2316 to 1038, 715 to 395)

	/** Grid and chain strips as the play screen places them, with the strips that show a button. */
	private class Laid(val pads: IntRect, val strips: List<IntRect>, val size: PlayLayout.ButtonSize)

	private fun lay(width: Int, height: Int, rows: Int, columns: Int, chains: Int, pro: Boolean, square: Boolean): Laid {
		val size = PlayLayout.buttonSize(PlayLayout.Area(width, height, strip), rows, columns, square, PlayLayout.chainRows(pro, chains))
		val across = IntSize(PlayLayout.CHAINS_PER_SIDE * size.chain, size.chain)
		val down = IntSize(size.chain, PlayLayout.CHAINS_PER_SIDE * size.chain)
		val grid = IntSize(columns * size.x, rows * size.y)
		val at = PlayLayout.place(width, height, grid, top = across, right = down, bottom = across, left = down)
		// Normal mode shows chains 1-8 on the right, 9-16 below and 17-24 on the left; Pro light mode all four sides.
		val shown = listOf(
			IntRect(at.top, across) to pro,
			IntRect(at.right, down) to (pro || chains > 1),
			IntRect(at.bottom, across) to (pro || chains > 8),
			IntRect(at.left, down) to (pro || chains > 16),
		)
		return Laid(IntRect(at.pads, grid), shown.filter { it.second }.map { it.first }, size)
	}

	@Test
	fun everyChainButton_staysInTheAreaWhateverThePadCount() {
		val problems = mutableListOf<String>()
		for ((w, h) in shapes) for (rows in 1..8) for (columns in 1..8) for (chains in listOf(1, 8, 9, 16, 17, 24))
			for (pro in listOf(false, true)) for (square in listOf(true, false)) {
				val laid = lay(w, h, rows, columns, chains, pro, square)
				val shape = "$w x $h, $rows x $columns pads, $chains chains, pro = $pro, square = $square"
				val area = IntRect(strip, 0, w - strip, h)
				if (laid.size.chain <= 0) problems += "$shape: no chain size"
				if (laid.size.chain > minOf(laid.size.x, laid.size.y)) problems += "$shape: chains larger than a pad"
				for (s in laid.strips + laid.pads) {
					val inside = s.left >= area.left && s.top >= area.top && s.right <= area.right && s.bottom <= area.bottom
					if (!inside) problems += "$shape: $s outside $area"
				}
				val all = laid.strips + laid.pads
				for (i in all.indices) for (j in i + 1 until all.size) if (all[i].overlaps(all[j])) problems += "$shape: ${all[i]} meets ${all[j]}"
			}
		assertTrue("${problems.size} layouts break:\n" + problems.take(20).joinToString("\n"), problems.isEmpty())
	}

	@Test
	fun fewPadsManyChains_keepThePadsAndShrinkOnlyTheChains() {
		// 4 x 3 pack with 24 chains (INF-002) on a 20:9 phone: eight chains fill the height, the pads stay larger.
		val laid = lay(878, 376, rows = 4, columns = 3, chains = 24, pro = false, square = true)
		assertEquals(75, laid.size.x)
		assertEquals(37, laid.size.chain)
		assertTrue(PlayLayout.CHAINS_PER_SIDE * laid.size.chain <= 376)
	}

	@Test
	fun eightByEightPacks_keepTheirSizeAndPlace() {
		for ((w, h) in shapes) for (pro in listOf(false, true)) for (square in listOf(true, false)) for (columns in listOf(8, 10)) {
			val chainRows = if (pro) 2 else 0
			val width = w - 2 * strip
			// The sizes and corners this screen had before small packs were laid out differently.
			val chain = minOf(width / (columns + 2), h / (8 + chainRows))
			val expected = if (square) PlayLayout.ButtonSize(chain, chain, chain)
			else PlayLayout.ButtonSize((width - 2 * chain) / columns, (h - chainRows * chain) / 8, chain)
			val shape = "$w x $h, 8 x $columns pads, pro = $pro, square = $square"
			val size = PlayLayout.buttonSize(PlayLayout.Area(w, h, strip), 8, columns, square, PlayLayout.chainRows(pro, chains = 8))
			assertEquals(shape, expected, size)

			val grid = IntSize(columns * size.x, 8 * size.y)
			val across = IntSize(8 * size.chain, size.chain)
			val down = IntSize(size.chain, 8 * size.chain)
			val padX = PlayLayout.padLeft(w, grid.width)
			val padY = (h - grid.height) / 2
			assertEquals(
				shape,
				PlayLayout.Placement(
					pads = IntOffset(padX, padY),
					top = IntOffset(padX + (grid.width - across.width) / 2, padY - across.height),
					right = IntOffset(padX + grid.width, padY + (grid.height - down.height) / 2),
					bottom = IntOffset(padX + (grid.width - across.width) / 2, padY + grid.height),
					left = IntOffset(padX - down.width, padY + (grid.height - down.height) / 2),
				),
				PlayLayout.place(w, h, grid, across, down, across, down),
			)
		}
	}

	@Test
	fun chainRows_makeRoomBelowOnceChainsReachTheBottomStrip() {
		assertEquals(0, PlayLayout.chainRows(proLightMode = false, chains = 8))
		assertEquals(2, PlayLayout.chainRows(proLightMode = false, chains = 9))
		assertEquals(2, PlayLayout.chainRows(proLightMode = true, chains = 1))
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
