package com.kimjisub.design

import android.annotation.TargetApi
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import com.kimjisub.design.R.layout
import kotlinx.android.synthetic.main.chain.view.*

class Chain : RelativeLayout {

	@JvmOverloads
	constructor(
			context: Context,
			attrs: AttributeSet? = null,
			defStyleAttr: Int = 0)
			: super(context, attrs, defStyleAttr)

	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(
			context: Context,
			attrs: AttributeSet?,
			defStyleAttr: Int,
			defStyleRes: Int)
			: super(context, attrs, defStyleAttr, defStyleRes)

	init {
		LayoutInflater.from(context)
				.inflate(layout.chain, this, true)
	}

	override fun setOnClickListener(listener: OnClickListener) {
		V_touchView.setOnClickListener(listener)
	}

	override fun setOnTouchListener(listener: OnTouchListener) {
		V_touchView!!.setOnTouchListener(listener)
	}
	//========================================================================================= Background


	fun setBackgroundImageDrawable(drawable: Drawable?): Chain {
		IV_background!!.setImageDrawable(drawable)
		return this
	}

	//========================================================================================= LED


	fun setLedBackground(drawable: Drawable?): Chain {
		IV_LED!!.background = drawable
		return this
	}

	fun setLedBackgroundColor(color: Int): Chain {
		IV_LED!!.setBackgroundColor(color)
		return this
	}

	fun setLedVisibility(visibility: Int): Chain {
		IV_LED!!.visibility = visibility
		return this
	}

	//========================================================================================= Phantom


	fun setPhantomImageDrawable(drawable: Drawable?): Chain {
		IV_phantom!!.setImageDrawable(drawable)
		return this
	}

	fun setPhantomRotation(rotation: Float): Chain {
		IV_phantom!!.rotation = rotation
		return this
	}
}