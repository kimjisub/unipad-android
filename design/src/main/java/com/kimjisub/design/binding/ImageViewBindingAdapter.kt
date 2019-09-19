package com.kimjisub.launchpad.binding

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import androidx.databinding.BindingAdapter
import androidx.databinding.BindingMethod
import androidx.databinding.BindingMethods

object ImageViewBindingAdapter {
	@JvmStatic
	@BindingAdapter("imgRes")
	fun imgRes(imageView: ImageView, resId: Int?) {
		if (resId != null)
			imageView.setImageResource(resId)
		 else
			imageView.setImageDrawable(null)

	}
	@JvmStatic
	@BindingAdapter("imgRes")
	fun imgRes(imageView: ImageView, drawable: Drawable?) {
		if (drawable != null)
			imageView.setImageDrawable(drawable)
		 else
			imageView.setImageDrawable(null)
	}
}