package com.kimjisub.launchpad.manager

import android.content.Context
import androidx.core.content.ContextCompat
import com.kimjisub.launchpad.R.*

class ColorManager(val context: Context) {
	private fun getColor(id: Int) = lazy { ContextCompat.getColor(context, id) }

	val background1 by getColor(color.background1)
	val gray1 by getColor(color.gray1)
	val background2 by getColor(color.background2)
	val gray2 by getColor(color.gray2)
	val white by getColor(color.white)


	val title by getColor(color.title)
	val subtitle by getColor(color.subtitle)


	val checkbox by getColor(color.checkbox)
	val trace_log by getColor(color.trace_log)
	val option_window by getColor(color.option_window)
	val option_window_checkbox by getColor(color.option_window_checkbox)
	val option_window_btn by getColor(color.option_window_btn)
	val option_window_btn_text by getColor(color.option_window_btn_text)


	val skyblue by getColor(color.skyblue)
	val blue by getColor(color.blue)
	val green by getColor(color.green)
	val orange by getColor(color.orange)
	val red by getColor(color.red)
	val pink by getColor(color.pink)


	val border_icon_on_background by getColor(color.border_icon_on_background)
	val popup_background by getColor(color.popup_background)


	val app_orange by getColor(color.app_orange)
	val app_blue by getColor(color.app_blue)
	val app_blue_dark by getColor(color.app_blue_dark)
}