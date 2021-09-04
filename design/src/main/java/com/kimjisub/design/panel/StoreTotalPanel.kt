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
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr){
	val b: PanelStoreTotalBinding = PanelStoreTotalBinding.bind(this)

	init {
		attrs?.let {
			val typedArray =
				context.obtainStyledAttributes(it, styleable.TotalPanel, defStyleAttr, 0)

			// code

			typedArray.recycle()
		}
	}
}