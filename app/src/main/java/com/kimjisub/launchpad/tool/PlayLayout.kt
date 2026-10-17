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

	/** The chain strips containing visible buttons, in Launchpad order. */
	data class ChainSides(val top: Boolean, val right: Boolean, val bottom: Boolean, val left: Boolean) {
		val horizontal: Boolean get() = top || bottom
		val vertical: Boolean get() = right || left
		val hasCorner: Boolean get() = horizontal && vertical
		// Both rows stay reserved to keep the pad grid vertically centred.
		val rows: Int get() = if (horizontal) 2 else 0
	}

	/** Pro light mode shows function keys and all chains; normal mode hides a lone chain. */
	fun chainButtonVisible(index: Int, proLightMode: Boolean, chains: Int): Boolean =
		proLightMode || (chains > 1 && index - CHAINS_PER_SIDE in 0 until chains)

	fun chainSides(proLightMode: Boolean, chains: Int): ChainSides = ChainSides(
		top = chainButtonVisible(0, proLightMode, chains),
		right = chainButtonVisible(CHAINS_PER_SIDE, proLightMode, chains),
		bottom = chainButtonVisible(2 * CHAINS_PER_SIDE, proLightMode, chains),
		left = chainButtonVisible(3 * CHAINS_PER_SIDE, proLightMode, chains),
	)

	/**
	 * Pad size for [area], keeping the grid centred and reserving rows for [sides]. Chains shrink
	 * only if a visible strip would leave the area or overlap an adjacent visible strip. The
	 * horizontal side margins stay symmetric even when only the right chain column is shown.
	 *
	 * @param rows pads from top to bottom (UniPack `buttonX`); [columns] from left to right (`buttonY`).
	 * @param square square pads keep their size and the chains shrink to fit; stretched pads fill
	 * what the chains leave.
	 */
	fun buttonSize(area: Area, rows: Int, columns: Int, square: Boolean, sides: ChainSides): ButtonSize {
		val width = (area.width - 2 * area.sideMargin).coerceAtLeast(0)
		val height = area.height.coerceAtLeast(0)
		val padRows = rows.coerceAtLeast(1)
		val padColumns = columns.coerceAtLeast(1)
		val stripLimit = min(
			if (sides.horizontal) width / CHAINS_PER_SIDE else Int.MAX_VALUE,
			if (sides.vertical) height / CHAINS_PER_SIDE else Int.MAX_VALUE,
		)
		// A visible strip may extend past the grid, but two visible strips must not meet at a corner.
		fun fits(size: ButtonSize) = size.chain <= stripLimit &&
			(!sides.hasCorner || CHAINS_PER_SIDE * size.chain <= max(padColumns * size.x, padRows * size.y)) &&
			padColumns * size.x + CHAIN_COLUMNS * size.chain <= width &&
			padRows * size.y + sides.rows * size.chain <= height
		val largestChain = min(width / (padColumns + CHAIN_COLUMNS), height / (padRows + sides.rows))
		if (square) {
			fun withPad(pad: Int): ButtonSize {
				val cornerLimit = if (sides.hasCorner) max(padRows, padColumns) * pad / CHAINS_PER_SIDE else Int.MAX_VALUE
				return ButtonSize(pad, pad, minOf(pad, stripLimit, cornerLimit))
			}
			val largestPad = min(width / padColumns, height / padRows)
			return (largestPad downTo largestChain).asSequence().map(::withPad).first(::fits)
		}
		fun withChain(chain: Int) =
			ButtonSize((width - CHAIN_COLUMNS * chain) / padColumns, (height - sides.rows * chain) / padRows, chain)
		return (largestChain downTo 0).asSequence().map(::withChain).first(::fits)
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
