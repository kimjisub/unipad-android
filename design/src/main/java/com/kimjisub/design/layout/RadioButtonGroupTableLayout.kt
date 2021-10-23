package com.kimjisub.design.layout

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.TableLayout
import android.widget.TableRow


class RadioButtonGroupTableLayout
@JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null
) : TableLayout(context, attrs), View.OnClickListener {
	companion object {
		private const val TAG = "RadioButtonGroupTableLayout"
	}

	private var activeRadioButton: RadioButton? = null

	var onCheckedChangeListener: ((radioButton: RadioButton) -> Unit)? = null

	override fun onClick(v: View) {
		val rb = v as RadioButton
		if (activeRadioButton != null)
			activeRadioButton!!.isChecked = false
		rb.isChecked = true
		activeRadioButton = rb

		onCheckedChangeListener?.invoke(rb)
	}

	override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
		super.addView(child, index, params)
		setChildrenOnClickListener(child as TableRow)
	}

	override fun addView(child: View, params: ViewGroup.LayoutParams?) {
		super.addView(child, params)
		setChildrenOnClickListener(child as TableRow)
	}

	private fun setChildrenOnClickListener(tr: TableRow) {
		val c: Int = tr.childCount
		for (i in 0 until c) {
			val v: View = tr.getChildAt(i)
			if (v is RadioButton) {
				v.setOnClickListener(this)
			}
		}
	}

	val checkedRadioButtonId: Int
		get() {
			return if (activeRadioButton != null)
				activeRadioButton!!.id
			else -1
		}
}