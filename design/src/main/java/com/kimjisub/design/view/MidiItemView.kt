package com.kimjisub.design.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import com.kimjisub.design.R.styleable
import com.kimjisub.design.databinding.ViewMidiItemBinding

class MidiItemView
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
) : RelativeLayout(context, attrs, defStyleAttr) {
	private val b: ViewMidiItemBinding =
		ViewMidiItemBinding.inflate(LayoutInflater.from(context), this, true)

	init {
		attrs?.let {
			val typedArray =
				context.obtainStyledAttributes(it, styleable.MidiItemView, defStyleAttr, 0)

			b.title = typedArray.getString(styleable.MidiItemView_title)
			b.subscription = typedArray.getString(styleable.MidiItemView_subscription)

			typedArray.recycle()
		}
	}
}

