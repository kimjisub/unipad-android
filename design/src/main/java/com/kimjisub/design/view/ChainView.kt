package com.kimjisub.design.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import com.kimjisub.design.databinding.ViewChainBinding

class ChainView
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr) {
	private val b: ViewChainBinding =
		ViewChainBinding.inflate(LayoutInflater.from(context), this, true)


	override fun setOnClickListener(listener: OnClickListener?) {
		b.touchSpace.setOnClickListener(listener)
	}

	override fun setOnTouchListener(listener: OnTouchListener) {
		b.touchSpace.setOnTouchListener(listener)
	}
	//========================================================================================= Background


	fun setBackgroundImageDrawable(drawable: Drawable?): ChainView {
		b.background.setImageDrawable(drawable)
		return this
	}

	//========================================================================================= LED


	fun setLedBackground(drawable: Drawable?): ChainView {
		b.led.background = drawable
		return this
	}

	fun setLedBackgroundColor(color: Int): ChainView {
		b.led.setBackgroundColor(color)
		return this
	}

	fun setLedVisibility(visibility: Int): ChainView {
		b.led.visibility = visibility
		return this
	}

	//========================================================================================= Phantom


	fun setPhantomImageDrawable(drawable: Drawable?): ChainView {
		b.phantom.setImageDrawable(drawable)
		return this
	}

	fun setPhantomRotation(rotation: Float): ChainView {
		b.phantom.rotation = rotation
		return this
	}
}