package com.kimjisub.design.panel

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.RelativeLayout
import androidx.databinding.DataBindingUtil
import androidx.databinding.ObservableField
import com.kimjisub.design.R.*
import com.kimjisub.design.databinding.PanelMainTotalBinding

class MainTotalPanel
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

	private val b: PanelMainTotalBinding = DataBindingUtil.inflate(LayoutInflater.from(context), layout.panel_main_total, this, true)
	val data: Data = Data()

	init {
		/*LayoutInflater.from(context)
			.inflate(layout.panel_main_total, this, true)
		b = PanelMainTotalBinding.bind(this)*/

		b.data = data

		attrs?.let {
			val typedArray = context.obtainStyledAttributes(it, styleable.TotalPanel, defStyleAttr, 0)

			// code

			typedArray.recycle()
		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////

	class Data {
		val logo: ObservableField<Drawable> = ObservableField()
		val version: ObservableField<String> = ObservableField()
		val premium : ObservableField<Boolean> = ObservableField()

		val unipackCount: ObservableField<String> = ObservableField()
		val unipackCapacity: ObservableField<String> = ObservableField()
		val openCount: ObservableField<String> = ObservableField()
		val padTouchCount: ObservableField<String> = ObservableField()
		val selectedTheme: ObservableField<String> = ObservableField()
	}

}