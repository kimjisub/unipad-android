package com.kimjisub.design

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import androidx.databinding.DataBindingUtil
import com.kimjisub.design.R.layout
import com.kimjisub.design.R.styleable
import com.kimjisub.design.databinding.ViewMidiItemBinding

class MidiItemView
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

	private val b: ViewMidiItemBinding =
		DataBindingUtil.inflate(LayoutInflater.from(context), layout.view_midi_item, this, true)

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

