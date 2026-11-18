package com.kimjisub.design.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import com.kimjisub.design.R

class PadView
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
	private val padBackground = image(R.id.background)
	private val led = image(R.id.led)
	private var phantom: ImageView? = null
	private var phantomRotation = 0f
	private var guideTiming: GuideTimingView? = null
	private var traceLog: TextView? = null
	private var traceColor: Int? = null
	private var traceShadowColor = Color.TRANSPARENT
	private val touchSpace = View(context).apply {
		id = R.id.touchSpace
		isClickable = true
		// Skip pads during keyboard Tab traversal so chain and menu buttons take only a few presses to reach.
		isFocusable = false
	}

	init {
		addView(padBackground, fill())
		addView(led, fill())
		addView(touchSpace, fill())
	}

	private fun fill() = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

	private fun image(viewId: Int) = ImageView(context).apply {
		id = viewId
		importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
	}

	override fun setOnClickListener(listener: OnClickListener?) {
		touchSpace.setOnClickListener(listener)
	}

	override fun setOnTouchListener(listener: OnTouchListener) {
		touchSpace.setOnTouchListener(listener)
	}

	fun setBackgroundImageDrawable(drawable: Drawable?): PadView {
		padBackground.setImageDrawable(drawable)
		return this
	}

	fun setLedBackground(drawable: Drawable?): PadView {
		led.background = drawable
		return this
	}

	fun setLedBackgroundColor(color: Int): PadView {
		led.setBackgroundColor(color)
		return this
	}

	fun setPhantomImageDrawable(drawable: Drawable?): PadView {
		if (drawable != null && phantom == null) {
			phantom = image(R.id.phantom).apply {
				rotation = phantomRotation
				addView(this, 2, fill())
			}
		}
		phantom?.setImageDrawable(drawable)
		return this
	}

	fun setPhantomRotation(rotation: Float): PadView {
		phantomRotation = rotation
		phantom?.rotation = rotation
		return this
	}

	fun startGuideAnimation(targetTimeMs: Long): PadView {
		val guide = guideTiming ?: GuideTimingView(context).apply {
			id = R.id.guideTiming
			importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
			visibility = GONE
			// Guide remains below numbers and the touch surface, regardless of creation order.
			addView(this, indexOfChild(traceLog ?: touchSpace), fill())
			guideTiming = this
		}
		guide.startAnimation(targetTimeMs)
		return this
	}

	fun stopGuideAnimation(): PadView {
		guideTiming?.stopAnimation()
		return this
	}

	private fun traceText(): TextView = traceLog ?: AppCompatTextView(context).apply {
		id = R.id.traceLog
		setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.pad_number_text_size).toFloat())
		traceColor?.let(::setTextColor)
		applyTraceShadow(this)
		val margin = resources.getDimensionPixelSize(R.dimen.pad_number_margin)
		val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
		params.setMargins(margin, margin, margin, margin)
		addView(this, indexOfChild(touchSpace), params)
		traceLog = this
	}

	fun setTraceLogText(string: String?): PadView {
		if (!string.isNullOrEmpty()) traceText().text = string else traceLog?.text = string
		return this
	}

	fun appendTraceLog(string: String?): PadView {
		if (!string.isNullOrEmpty()) traceText().append(string)
		return this
	}

	fun setTraceLogTextColor(color: Int, shadowColor: Int = Color.TRANSPARENT): PadView {
		traceColor = color
		traceShadowColor = shadowColor
		traceLog?.let {
			it.setTextColor(color)
			applyTraceShadow(it)
		}
		return this
	}

	private fun applyTraceShadow(text: TextView) {
		if (traceShadowColor == Color.TRANSPARENT) {
			text.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
		} else {
			val density = resources.displayMetrics.density
			text.setShadowLayer(density, 0f, density / 2f, traceShadowColor)
		}
	}
}
