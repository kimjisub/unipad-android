package com.kimjisub.design

import android.content.Context
import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import androidx.appcompat.widget.SwitchCompat

// https://stackoverflow.com/questions/52064205/how-to-set-width-and-track-text-in-a-switch-switchcompat-button-and-achieve-this

class TextSwitch : SwitchCompat {

	companion object {

		val TRACK_COLOR = 0xFFFFFFFF.toInt()
		val TRACK_STROKE_WIDTH = 2f.dp2Px.toInt()
		val TRACK_STROKE_COLOR = 0xFF00A1FF.toInt()
		val TRACK_LABEL_COLOR = 0xFF00A1FF.toInt()
		val TRACK_LABEL_SIZE = 14f.sp2Px

		val THUMB_COLOR = 0xFF00A1FF.toInt()
		val THUMB_LABEL_COLOR = 0xFFFFFFFF.toInt()
		val THUMB_LABEL_SIZE = 14f.sp2Px

		fun drawLabel(canvas: Canvas,
		              bounds: Rect,
		              paint: Paint,
		              text: CharSequence?) {
			text ?: return

			val tb = RectF();
			tb.right = paint.measureText(text, 0, text.length)
			tb.bottom = paint.descent() - paint.ascent()
			tb.left += bounds.centerX() - tb.centerX()
			tb.top += bounds.centerY() - tb.centerY() - paint.ascent()

			canvas.drawText(text.toString(), tb.left, tb.top, paint)
		}

		private inline val Float.sp2Px
			get() = TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_SP,
				this,
				Resources.getSystem().displayMetrics)

		private inline val Float.dp2Px
			get() = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
				this,
				Resources.getSystem().displayMetrics)
	}

	private val trackLabelPaint = Paint().apply {
		isAntiAlias = true
		textSize = TRACK_LABEL_SIZE
		color = TRACK_LABEL_COLOR
	}

	private val thumbLabelPaint = Paint().apply {
		isAntiAlias = true
		textSize = THUMB_LABEL_SIZE
		color = THUMB_LABEL_COLOR
	}

	private val thumbLabel
		get () = if (isChecked) textOn else textOff

	constructor(context: Context?) : super(context)
	constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
	constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

	init {
		background = null
		trackDrawable = TrackDrawable()
		thumbDrawable = ThumbDrawable()

		isChecked = isChecked
	}

	override fun onSizeChanged(w: Int,
	                           h: Int,
	                           oldw: Int,
	                           oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)

		(trackDrawable as GradientDrawable).setSize(w, h)
		(thumbDrawable as GradientDrawable).setSize(w / 2, h)
	}

	inner class TrackDrawable : GradientDrawable() {

		private val textOffBounds = Rect()
		private val textOnBounds = Rect()

		init {
			setColor(TRACK_COLOR)
			setStroke(TRACK_STROKE_WIDTH, TRACK_STROKE_COLOR)
		}

		override fun onBoundsChange(r: Rect) {
			super.onBoundsChange(r)

			cornerRadius = r.height() / 2f

			textOffBounds.set(r)
			textOffBounds.right /= 2

			textOnBounds.set(textOffBounds)
			textOnBounds.offset(textOffBounds.right, 0)
		}

		override fun draw(canvas: Canvas) {
			super.draw(canvas)

			drawLabel(canvas, textOffBounds, trackLabelPaint, textOff)
			drawLabel(canvas, textOnBounds, trackLabelPaint, textOn)
		}
	}

	inner class ThumbDrawable : GradientDrawable() {

		private val thumbLabelBounds = Rect()

		init {
			setColor(THUMB_COLOR)
		}

		override fun onBoundsChange(r: Rect) {
			super.onBoundsChange(r)

			cornerRadius = r.height() / 2f

			thumbLabelBounds.set(r)
		}

		override fun draw(canvas: Canvas) {
			super.draw(canvas)

			drawLabel(canvas, thumbLabelBounds, thumbLabelPaint, thumbLabel)
		}
	}
}