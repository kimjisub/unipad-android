package com.kimjisub.design

import android.annotation.TargetApi
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import com.kimjisub.design.R.layout
import kotlinx.android.synthetic.main.pad.view.*

class Pad : RelativeLayout {

	@JvmOverloads
	constructor(
		context: Context,
		attrs: AttributeSet? = null,
		defStyleAttr: Int = 0
	)
			: super(context, attrs, defStyleAttr)

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(
		context: Context,
		attrs: AttributeSet?,
		defStyleAttr: Int,
		defStyleRes: Int
	)
			: super(context, attrs, defStyleAttr, defStyleRes)

	init {
		LayoutInflater.from(context)
			.inflate(layout.pad, this, true)
	}

	override fun setOnClickListener(listener: OnClickListener) {
		V_touchView.setOnClickListener(listener)
	}

	override fun setOnTouchListener(listener: OnTouchListener) {
		V_touchView.setOnTouchListener(listener)
	}
	//========================================================================================= Background


	fun setBackgroundImageDrawable(drawable: Drawable?): Pad {
		IV_background.setImageDrawable(drawable)
		return this
	}

	//========================================================================================= LED


	fun setLedBackground(drawable: Drawable?): Pad {
		IV_LED.background = drawable
		return this
	}

	fun setLedBackgroundColor(color: Int): Pad {
		IV_LED.setBackgroundColor(color)
		return this
	}

	//========================================================================================= Phantom


	fun setPhantomImageDrawable(drawable: Drawable?): Pad {
		IV_phantom.setImageDrawable(drawable)
		return this
	}

	fun setPhantomRotation(rotation: Float): Pad {
		IV_phantom.rotation = rotation
		return this
	}

	//========================================================================================= TraceLog


	fun setTraceLogText(string: String?): Pad {
		TV_traceLog.text = string
		return this
	}

	fun appendTraceLog(string: String?): Pad {
		TV_traceLog.append(string)
		return this
	}

	fun setTraceLogTextColor(color: Int): Pad {
		TV_traceLog.setTextColor(color)
		return this
	}
}