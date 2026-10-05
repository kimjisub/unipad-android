package com.kimjisub.launchpad.tool

import androidx.compose.ui.unit.IntRect
import kotlin.math.max
import kotlin.math.min

/**
 * Play screen geometry in pixels. The pad grid sits on the window's centre line, as on iOS: the
 * pads are sized so that the chain columns clear the menu strip on the right and the display
 * cutout or system bars on either side while the grid stays centred.
 */
object PlayLayout {

	private const val CHAIN_COLUMNS = 2

	/** The area pads are laid out in, keeping [sideMargin] free at both its left and right edges. */
	data class Area(val width: Int, val height: Int, val sideMargin: Int)

	/** Pad size; chain buttons are [min] square. */
	data class ButtonSize(val x: Int, val y: Int) {
		val min: Int get() = min(x, y)
	}

	/**
	 * The margin kept free on both sides of a centred grid: the wider of what the left edge needs
	 * ([insetLeft], cutout or system bar) and what the right edge needs ([insetRight] and the
	 * [menuStrip] inside it).
	 */
	fun sideMargin(insetLeft: Int, insetRight: Int, menuStrip: Int): Int = max(insetLeft, insetRight + menuStrip)

	/**
	 * Pad size for [area].
	 *
	 * @param rows pads from top to bottom (UniPack `buttonX`); [columns] from left to right (`buttonY`).
	 * @param square square pads leave a chain column on each side of the grid and [chainRows] above
	 *   and below it; stretched pads fill the area between the margins.
	 */
	fun buttonSize(area: Area, rows: Int, columns: Int, square: Boolean, chainRows: Int): ButtonSize {
		val gridWidth = (area.width - 2 * area.sideMargin).coerceAtLeast(0)
		if (!square) return ButtonSize(gridWidth / columns, area.height / rows)
		val size = min(gridWidth / (columns + CHAIN_COLUMNS), area.height / (rows + chainRows))
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
