package com.kimjisub.launchpad.binding

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.databinding.BindingAdapter

@BindingAdapter("imgRes")
fun imgRes(imageView: ImageView, resId: Int?) {
	if (resId != null)
		imageView.setImageResource(resId)
	else
		imageView.setImageDrawable(null)

}

@BindingAdapter("imgRes")
fun imgRes(imageView: ImageView, drawable: Drawable?) {
	if (drawable != null)
		imageView.setImageDrawable(drawable)
	else
		imageView.setImageDrawable(null)
}