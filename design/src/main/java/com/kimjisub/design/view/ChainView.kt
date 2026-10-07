package com.kimjisub.design.view

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
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


	// Only chains that switch something are reached by Tab; the top row is LED-only.
	override fun setOnClickListener(listener: OnClickListener?) {
		b.touchSpace.setOnClickListener(listener)
		b.touchSpace.isFocusable = listener != null
	}

	/*
	 * Pointers (fingers and mice) select a chain through the play screen's own input handling,
	 * which calls [click]: Compose stops handing an embedded view the press that follows a mouse
	 * hover, so a click from the view's own touch handling never came with a mouse. Touches stop
	 * here; the click stays for the keyboard and accessibility services.
	 */
	override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

	/** Runs the click listener, as a tap on the chain does. False when the chain has none. */
	fun click(): Boolean = b.touchSpace.performClick()

	//========================================================================================= Background


	fun setBackgroundImageDrawable(drawable: Drawable?): ChainView {
		b.background.setImageDrawable(drawable)
		return this
	}

	//========================================================================================= LED


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

}