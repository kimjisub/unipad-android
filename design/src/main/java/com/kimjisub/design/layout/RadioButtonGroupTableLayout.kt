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
	attrs: AttributeSet? = null,
) : TableLayout(context, attrs), View.OnClickListener {

	private var activeRadioButton: RadioButton? = null

	var onCheckedChangeListener: ((radioButton: RadioButton) -> Unit)? = null

	override fun onClick(v: View) {
		val rb = v as? RadioButton ?: return
		activeRadioButton?.isChecked = false
		rb.isChecked = true
		activeRadioButton = rb

		onCheckedChangeListener?.invoke(rb)
	}

	override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
		super.addView(child, index, params)
		(child as? TableRow)?.let { setChildrenOnClickListener(it) }
	}

	override fun addView(child: View, params: ViewGroup.LayoutParams?) {
		super.addView(child, params)
		(child as? TableRow)?.let { setChildrenOnClickListener(it) }
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

}