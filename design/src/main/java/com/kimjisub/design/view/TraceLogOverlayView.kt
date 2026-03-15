package com.kimjisub.design.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

class TraceLogOverlayView
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

	private var sequence: List<Pair<Int, Int>> = emptyList()
	private var gridRows: Int = 0
	private var gridCols: Int = 0

	private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.STROKE
		strokeJoin = Paint.Join.ROUND
		strokeCap = Paint.Cap.ROUND
		color = Color.WHITE
		alpha = 217
	}

	private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
		color = Color.WHITE
		alpha = 242
	}

	init {
		isClickable = false
		isFocusable = false
	}

	fun setTraceColor(color: Int) {
		linePaint.color = color
		linePaint.alpha = 217
		dotPaint.color = color
		dotPaint.alpha = 242
		invalidate()
	}

	fun setData(sequence: List<Pair<Int, Int>>, rows: Int, cols: Int) {
		this.sequence = sequence
		this.gridRows = rows
		this.gridCols = cols
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		if (sequence.isEmpty() || gridRows == 0 || gridCols == 0) return

		val cellW = width.toFloat() / gridCols
		val cellH = height.toFloat() / gridRows
		val cellMin = min(cellW, cellH)
		val maxOffset = cellMin * 0.3f

		linePaint.strokeWidth = max(1.5f, cellMin * 0.04f)
		val dotRadius = max(2.5f, cellMin * 0.06f)

		val visitCount = mutableMapOf<Pair<Int, Int>, Int>()
		val visitIndex = mutableMapOf<Pair<Int, Int>, Int>()

		for (point in sequence) {
			visitCount[point] = (visitCount[point] ?: 0) + 1
		}

		data class PointCoord(val cx: Float, val cy: Float)

		val coords = mutableListOf<PointCoord>()
		for (point in sequence) {
			val (x, y) = point
			val totalVisits = visitCount[point] ?: 1
			val currentIndex = visitIndex[point] ?: 0
			visitIndex[point] = currentIndex + 1

			var ox = 0f
			var oy = 0f
			if (totalVisits > 1) {
				val t = currentIndex.toFloat() / (totalVisits - 1).toFloat() - 0.5f
				ox = t * maxOffset
				oy = t * maxOffset
			}

			val cx = y * cellW + cellW / 2f + ox
			val cy = x * cellH + cellH / 2f + oy
			coords.add(PointCoord(cx, cy))
		}

		if (coords.size >= 2) {
			val path = Path()
			path.moveTo(coords[0].cx, coords[0].cy)
			for (i in 1 until coords.size) {
				path.lineTo(coords[i].cx, coords[i].cy)
			}
			canvas.drawPath(path, linePaint)
		}

		for (coord in coords) {
			canvas.drawCircle(coord.cx, coord.cy, dotRadius, dotPaint)
		}
	}
}
