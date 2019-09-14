package com.kimjisub.launchpad.binding

import android.view.View
import android.widget.ImageView
import androidx.databinding.BindingAdapter

object ImageViewBindingAdapter {
	@JvmStatic
	@BindingAdapter("imgRes")
	fun imgRes(imageView: ImageView, resId: Int?) {
		if (resId != null) {
			imageView.setImageResource(resId)
			imageView.visibility = View.VISIBLE
		} else {
			imageView.setImageDrawable(null)
			imageView.visibility = View.GONE
		}
	}
}