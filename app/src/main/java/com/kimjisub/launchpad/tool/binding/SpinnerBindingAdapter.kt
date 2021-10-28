package com.kimjisub.launchpad.tool.binding

import androidx.appcompat.widget.AppCompatSpinner
import androidx.databinding.BindingAdapter

object SpinnerBindingAdapter {

	@JvmStatic
	@BindingAdapter("android:selectedItemPosition")
	fun setSelectedItemPosition(spinner: AppCompatSpinner, position: Int) {
		spinner.setSelection(position)
	}
}