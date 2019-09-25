package com.kimjisub.design.panel

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import androidx.databinding.DataBindingUtil
import com.kimjisub.design.R.layout
import com.kimjisub.design.R.styleable
import com.kimjisub.design.databinding.PanelStoreTotalBinding

class StoreTotalPanel
@JvmOverloads
constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

	/*@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	constructor(
		context: Context,
		attrs: AttributeSet?,
		defStyleAttr: Int,
		defStyleRes: Int)
			: super(context, attrs, defStyleAttr, defStyleRes)*/

	val b: PanelStoreTotalBinding = DataBindingUtil.inflate(LayoutInflater.from(context), layout.panel_store_total, this, true)

	init {
		/*val v = LayoutInflater.from(context)
			.inflate(layout.panel_store_total, this, true)
		b = PanelStoreTotalBinding.bind(v)*/

		attrs?.let {
			val typedArray = context.obtainStyledAttributes(it, styleable.TotalPanel, defStyleAttr, 0)

			// code

			typedArray.recycle()
		}
	}
}