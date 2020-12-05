package com.kimjisub.launchpad.manager

import android.content.Context
import androidx.core.content.ContextCompat
import com.kimjisub.launchpad.R.color

class ColorManager(val context: Context) {
	fun get(id: Int) = ContextCompat.getColor(context, id)
	private fun getLazy(id: Int) = lazy { get(id) }

	val background1 by getLazy(color.background1)
	val gray1 by getLazy(color.gray1)
	val background2 by getLazy(color.background2)
	val gray2 by getLazy(color.gray2)
	val white by getLazy(color.white)


	val title by getLazy(color.title)
	val subtitle by getLazy(color.subtitle)


	val checkbox by getLazy(color.checkbox)
	val trace_log by getLazy(color.trace_log)
	val option_window by getLazy(color.option_window)
	val option_window_checkbox by getLazy(color.option_window_checkbox)
	val option_window_btn by getLazy(color.option_window_btn)
	val option_window_btn_text by getLazy(color.option_window_btn_text)


	val skyblue by getLazy(color.skyblue)
	val blue by getLazy(color.blue)
	val green by getLazy(color.green)
	val orange by getLazy(color.orange)
	val red by getLazy(color.red)
	val pink by getLazy(color.pink)


	val border_icon_on_background by getLazy(color.border_icon_on_background)
	val popup_background by getLazy(color.popup_background)


	val app_orange by getLazy(color.app_orange)
	val app_blue by getLazy(color.app_blue)
	val app_blue_dark by getLazy(color.app_blue_dark)
}