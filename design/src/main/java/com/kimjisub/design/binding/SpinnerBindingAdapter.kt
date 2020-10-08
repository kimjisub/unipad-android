package com.kimjisub.design.binding

import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.Spinner
import androidx.appcompat.widget.AppCompatSpinner
import androidx.databinding.BindingAdapter

object SpinnerBindingAdapter {

	@JvmStatic
	@BindingAdapter("android:selectedItemPosition")
	fun setSelectedItemPosition(spinner: AppCompatSpinner, position: Int) {
		spinner.setSelection(position)
	}
}