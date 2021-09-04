package com.kimjisub.design.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import com.kimjisub.design.databinding.ViewPadBinding

class PadView
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {
	private val b: ViewPadBinding =
		ViewPadBinding.inflate(LayoutInflater.from(context), this, true)

	override fun setOnClickListener(listener: OnClickListener?) {
		b.touchSpace.setOnClickListener(listener)
	}

	override fun setOnTouchListener(listener: OnTouchListener) {
		b.touchSpace.setOnTouchListener(listener)
	}
	//========================================================================================= Background


	fun setBackgroundImageDrawable(drawable: Drawable?): PadView {
		b.background.setImageDrawable(drawable)
		return this
	}

	//========================================================================================= LED


	fun setLedBackground(drawable: Drawable?): PadView {
		b.led.background = drawable
		return this
	}

	fun setLedBackgroundColor(color: Int): PadView {
		b.led.setBackgroundColor(color)
		return this
	}

	//========================================================================================= Phantom


	fun setPhantomImageDrawable(drawable: Drawable?): PadView {
		b.phantom.setImageDrawable(drawable)
		return this
	}

	fun setPhantomRotation(rotation: Float): PadView {
		b.phantom.rotation = rotation
		return this
	}

	//========================================================================================= TraceLog


	fun setTraceLogText(string: String?): PadView {
		b.traceLog.text = string
		return this
	}

	fun appendTraceLog(string: String?): PadView {
		b.traceLog.append(string)
		return this
	}

	fun setTraceLogTextColor(color: Int): PadView {
		b.traceLog.setTextColor(color)
		return this
	}
}