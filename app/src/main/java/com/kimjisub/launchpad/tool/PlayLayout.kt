package com.kimjisub.launchpad.tool

import androidx.compose.ui.unit.IntRect
import kotlin.math.max
import kotlin.math.min

/**
 * Play screen geometry in pixels. The pad grid sits on the window's centre line, as on iOS: the
 * menu strip at the right edge is kept free on both sides when pads are sized, so the chain
 * columns and the menu stay clear of the grid without moving it off centre.
 */
object PlayLayout {

	private const val CHAIN_COLUMNS = 2

	/** Pad size; chain buttons are [min] square. */
	data class ButtonSize(val x: Int, val y: Int) {
		val min: Int get() = min(x, y)
	}

	/**
	 * Pad size for an area of [width] x [height] whose right edge holds a [sideStrip] wide menu.
	 *
	 * @param rows pads from top to bottom (UniPack `buttonX`); [columns] from left to right (`buttonY`).
	 * @param square square pads leave a chain column on each side of the grid and [chainRows] above
	 *   and below it; stretched pads fill the area between the strips.
	 */
	fun buttonSize(width: Int, height: Int, rows: Int, columns: Int, square: Boolean, chainRows: Int, sideStrip: Int): ButtonSize {
		val gridWidth = (width - 2 * sideStrip).coerceAtLeast(0)
		if (!square) return ButtonSize(gridWidth / columns, height / rows)
		val size = min(gridWidth / (columns + CHAIN_COLUMNS), height / (rows + chainRows))
		return ButtonSize(size, size)
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
