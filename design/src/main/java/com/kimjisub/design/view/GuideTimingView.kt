package com.kimjisub.design.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class GuideTimingView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

	private val maskPaint = Paint().apply {
		color = 0xDD000000.toInt()
	}
	private var targetTimeMs: Long = 0
	private var animationDurationMs: Long = 0
	private var running = false

	companion object {
		const val LOOKAHEAD_MS = 800L
	}

	fun startAnimation(targetWallTimeMs: Long) {
		stopAnimation()
		targetTimeMs = targetWallTimeMs
		animationDurationMs = (targetWallTimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(100)
		running = true
		visibility = VISIBLE
		postInvalidateOnAnimation()
	}

	fun stopAnimation() {
		running = false
		visibility = GONE
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)
		if (!running) return

		val now = SystemClock.elapsedRealtime()
		val remaining = (targetTimeMs - now).coerceAtLeast(0)
		val progress = (1f - remaining.toFloat() / animationDurationMs).coerceIn(0f, 1f)

		val w = width.toFloat()
		val h = height.toFloat()
		val maxBorder = min(w, h) / 2f
		val revealed = maxBorder * progress

		canvas.drawRect(revealed, revealed, w - revealed, h - revealed, maskPaint)

		if (remaining > 0) {
			postInvalidateOnAnimation()
		}
	}
}
