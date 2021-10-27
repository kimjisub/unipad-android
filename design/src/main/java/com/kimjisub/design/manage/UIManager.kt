package com.kimjisub.design.manage

import android.content.Context
import android.util.DisplayMetrics

object UIManager {
	fun pxToDp(context: Context, pixel: Int): Int {
		var dp = 0f
		try {
			val metrics: DisplayMetrics = context.resources.displayMetrics
			dp = pixel / (metrics.densityDpi / 160f)
		} catch (e: Exception) {
		}
		return dp.toInt()
	}

	fun dpToPx(context: Context, dp: Float): Int {
		val metrics: DisplayMetrics = context.resources.displayMetrics
		val px = dp * (metrics.densityDpi / 160f)
		return Math.round(px)
	}
}