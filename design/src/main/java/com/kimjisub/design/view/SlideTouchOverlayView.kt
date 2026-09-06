package com.kimjisub.design.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View

/**
 * Transparent layer over the pad grid for "Slide Mode": a finger that drags into a pad
 * presses it and the pad it left is released, so a run can be played with one stroke.
 * Multi-touch: every pointer is tracked on its own. When the layer is GONE the pads
 * underneath get the touches as before.
 */
class SlideTouchOverlayView
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

	/** Called with (row, column, pressed). */
	var listener: ((x: Int, y: Int, down: Boolean) -> Unit)? = null

	private var rows = 0
	private var cols = 0

	// pointerId -> packed cell (row * cols + col) currently held by that pointer
	private val held = SparseArray<Int>()

	fun setGrid(rows: Int, cols: Int) {
		this.rows = rows
		this.cols = cols
		releaseAll()
	}

	private fun releaseAll() {
		for (i in 0 until held.size()) {
			val cell = held.valueAt(i)
			listener?.invoke(cell / cols, cell % cols, false)
		}
		held.clear()
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (rows == 0 || cols == 0 || listener == null) return false
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
				val i = event.actionIndex
				press(event.getPointerId(i), event.getX(i), event.getY(i))
			}
			MotionEvent.ACTION_MOVE -> {
				for (i in 0 until event.pointerCount) {
					press(event.getPointerId(i), event.getX(i), event.getY(i))
				}
			}
			MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
				release(event.getPointerId(event.actionIndex))
			}
			MotionEvent.ACTION_CANCEL -> releaseAll()
		}
		return true
	}

	private fun press(pointerId: Int, px: Float, py: Float) {
		val cell = cellAt(px, py, width, height, rows, cols)
		val previous = held.get(pointerId)
		if (cell == previous) return
		if (previous != null) listener?.invoke(previous / cols, previous % cols, false)
		if (cell != null) {
			held.put(pointerId, cell)
			listener?.invoke(cell / cols, cell % cols, true)
		} else {
			held.remove(pointerId)
		}
	}

	private fun release(pointerId: Int) {
		val cell = held.get(pointerId) ?: return
		held.remove(pointerId)
		listener?.invoke(cell / cols, cell % cols, false)
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
