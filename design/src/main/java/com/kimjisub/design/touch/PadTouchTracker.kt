package com.kimjisub.design.touch

/**
 * Turns the fingers on the pad grid into pad presses and releases, every pointer on its own, so
 * a finger keeps playing whatever other fingers or a palm already touch. With [slide] off (the
 * first-install default) a finger stays bound to the pad it first pressed; with it on ("Slide
 * Mode") dragging into a pad presses it and the pad it left is released.
 *
 * Positions are in the grid's own coordinates, with the grid's current [width] and [height].
 */
class PadTouchTracker(
	/** Called with (row, column, pressed). */
	private val listener: (x: Int, y: Int, down: Boolean) -> Unit,
) {
	var slide = false

	private var rows = 0
	private var cols = 0

	// pointerId -> packed cell (row * cols + col) currently held by that pointer
	private val held = HashMap<Long, Int>()

	fun setGrid(rows: Int, cols: Int) {
		releaseAll()
		this.rows = rows
		this.cols = cols
	}

	fun down(pointerId: Long, px: Float, py: Float, width: Int, height: Int) =
		press(pointerId, cellAt(px, py, width, height, rows, cols))

	fun move(pointerId: Long, px: Float, py: Float, width: Int, height: Int) {
		if (slide) press(pointerId, cellAt(px, py, width, height, rows, cols))
	}

	fun up(pointerId: Long) {
		val cell = held.remove(pointerId) ?: return
		listener(cell / cols, cell % cols, false)
	}

	fun releaseAll() {
		val cells = held.values.toList()
		held.clear()
		for (cell in cells) listener(cell / cols, cell % cols, false)
	}

	private fun press(pointerId: Long, cell: Int?) {
		val previous = held[pointerId]
		if (cell == previous) return
		if (previous != null) listener(previous / cols, previous % cols, false)
		if (cell != null) {
			held[pointerId] = cell
			listener(cell / cols, cell % cols, true)
		} else {
			held.remove(pointerId)
		}
	}

	companion object {
		/**
		 * Packed cell (row * cols + col) under a point, or null when the point is outside the
		 * grid. Rows run top to bottom, columns left to right, matching the pad array [x][y].
		 */
		fun cellAt(px: Float, py: Float, width: Int, height: Int, rows: Int, cols: Int): Int? {
			if (width <= 0 || height <= 0 || rows <= 0 || cols <= 0) return null
			if (px < 0f || py < 0f || px >= width || py >= height) return null
			val col = (px * cols / width).toInt().coerceIn(0, cols - 1)
			val row = (py * rows / height).toInt().coerceIn(0, rows - 1)
			return row * cols + col
		}
	}
}
