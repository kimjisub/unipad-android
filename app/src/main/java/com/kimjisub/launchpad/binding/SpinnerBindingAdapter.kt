package com.kimjisub.launchpad.binding

import android.view.View
import android.widget.AdapterView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.databinding.BindingAdapter
import androidx.databinding.InverseBindingAdapter
import androidx.databinding.InverseBindingListener

@BindingAdapter("selectedItemPosition")
fun setSelectedItemPosition(spinner: AppCompatSpinner, position: Int) {
	spinner.setSelection(position)
}

@BindingAdapter("selectedItemPositionAttrChanged")
fun selectedItemPositionListeners(
	spinner: AppCompatSpinner,
	attrChange: InverseBindingListener,
) {
	spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
		override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
			attrChange.onChange()
		}

		override fun onNothingSelected(p0: AdapterView<*>?) {
		}
	}
}

@InverseBindingAdapter(attribute = "selectedItemPosition",
	event = "selectedItemPositionAttrChanged")
fun getSelectedItemPosition(spinner: AppCompatSpinner): Int {
	return spinner.selectedItemPosition
}