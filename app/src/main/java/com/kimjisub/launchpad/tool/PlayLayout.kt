package com.kimjisub.launchpad.tool

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.min

/**
 * Play screen geometry in pixels. The pad grid sits on the window's centre line, as on iOS: the
 * pads are sized so that the chain columns clear the menu strip on the right and the display
 * cutout or system bars on either side while the grid stays centred.
 */
object PlayLayout {

	private const val CHAIN_COLUMNS = 2

	/** Chain buttons on each side of the grid: 8 above, right, below and left of it. */
	const val CHAINS_PER_SIDE = 8

	/** The area pads are laid out in, keeping [sideMargin] free at both its left and right edges. */
	data class Area(val width: Int, val height: Int, val sideMargin: Int)

	/** Pad size [x] by [y]; chain buttons are [chain] square. */
	data class ButtonSize(val x: Int, val y: Int, val chain: Int)

	/**
	 * The margin kept free on both sides of a centred grid: the wider of what the left edge needs
	 * ([insetLeft], cutout or system bar) and what the right edge needs ([insetRight] and the
	 * [menuStrip] inside it).
	 */
	fun sideMargin(insetLeft: Int, insetRight: Int, menuStrip: Int): Int = max(insetLeft, insetRight + menuStrip)

	/**
	 * Chain rows kept free above and below the grid: the top and bottom strips in Pro light mode,
	 * and in normal mode once a pack's chains reach the bottom strip (chain 9 and on).
	 */
	fun chainRows(proLightMode: Boolean, chains: Int): Int = if (proLightMode || chains > CHAINS_PER_SIDE) 2 else 0

	/**
	 * Pad size for [area]. The grid leaves a chain column on each side and [chainRows] above and
	 * below it. Chains are as large as the largest square pad that fits, and smaller only where
	 * the [CHAINS_PER_SIDE] chains of a side would otherwise leave the area or meet the strip
	 * beside them: a pack with few pads, such as 4 x 3, has a side much shorter than eight pads.
	 *
	 * @param rows pads from top to bottom (UniPack `buttonX`); [columns] from left to right (`buttonY`).
	 * @param square square pads keep their size and the chains shrink to fit; stretched pads fill
	 * what the chains leave.
	 */
	fun buttonSize(area: Area, rows: Int, columns: Int, square: Boolean, chainRows: Int): ButtonSize {
		val width = (area.width - 2 * area.sideMargin).coerceAtLeast(0)
		val height = area.height.coerceAtLeast(0)
		// A strip may run past the grid's side as long as it stays in the area and the strips
		// across from it stay within the grid's side, so that no two strips meet at a corner.
		fun fits(size: ButtonSize) = CHAINS_PER_SIDE * size.chain <= min(width, height) &&
			CHAINS_PER_SIDE * size.chain <= max(columns * size.x, rows * size.y) &&
			columns * size.x + CHAIN_COLUMNS * size.chain <= width &&
			rows * size.y + chainRows * size.chain <= height
		val largestChain = min(width / (columns + CHAIN_COLUMNS), height / (rows + chainRows))
		if (square) {
			fun withPad(pad: Int): ButtonSize {
				val chain = minOf(pad, min(width, height) / CHAINS_PER_SIDE, max(rows, columns) * pad / CHAINS_PER_SIDE)
				return ButtonSize(pad, pad, chain)
			}
			val largestPad = min(width / columns, height / rows)
			return (largestPad downTo largestChain).asSequence().map(::withPad).firstOrNull(::fits) ?: withPad(largestChain)
		}
		fun withChain(chain: Int) =
			ButtonSize((width - CHAIN_COLUMNS * chain) / columns, (height - chainRows * chain) / rows, chain)
		return (largestChain downTo 0).asSequence().map(::withChain).firstOrNull(::fits) ?: withChain(0)
	}

	/** Top-left corners of the pad grid and of the chain strips centred on its four sides. */
	data class Placement(val pads: IntOffset, val top: IntOffset, val right: IntOffset, val bottom: IntOffset, val left: IntOffset)

	/**
	 * Places a [pads] grid centred in a [width] x [height] area, as [padLeft] does horizontally,
	 * and the [top], [right], [bottom] and [left] chain strips against its sides. The strips are
	 * centred in the area like the grid, so a strip longer than the grid's side stays in the
	 * area too.
	 */
	fun place(width: Int, height: Int, pads: IntSize, top: IntSize, right: IntSize, bottom: IntSize, left: IntSize): Placement {
		val x = padLeft(width, pads.width)
		val y = (height - pads.height) / 2
		return Placement(
			pads = IntOffset(x, y),
			top = IntOffset(padLeft(width, top.width), y - top.height),
			right = IntOffset(x + pads.width, (height - right.height) / 2),
			bottom = IntOffset(padLeft(width, bottom.width), y + pads.height),
			left = IntOffset(x - left.width, (height - left.height) / 2),
		)
	}

	/** Left edge of a [gridWidth] wide grid centred in an area [width] wide. */
	fun padLeft(width: Int, gridWidth: Int): Int = (width - gridWidth) / 2

	/**
	 * Width of a logo pinned by its top-right corner at ([right], [top]): [maxWidth] unless that
	 * would bring it within [margin] of an [obstacles] rect, then the widest that stays clear,
	 * beside or above the obstacle. [aspect] is its width / height. 0 when no width fits.
	 */
	fun logoWidth(right: Int, top: Int, maxWidth: Int, aspect: Float, obstacles: List<IntRect>, margin: Int): Int {
		var width = maxWidth
		for (o in obstacles) {
			if (o.left - margin >= right || o.bottom + margin <= top) continue
			val beside = right - (o.right + margin)
			val above = ((o.top - margin - top) * aspect).toInt()
			width = min(width, max(beside, above))
		}
		return width.coerceAtLeast(0)
	}
}
