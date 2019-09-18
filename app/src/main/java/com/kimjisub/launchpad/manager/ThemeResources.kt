package com.kimjisub.launchpad.manager

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.*

class ThemeResources {
	var packageName: String? = null
	var customRes: Resources? = null
	var defaultRes: Resources
	var icon: Drawable
	var name: String
	var description: String
	var author: String
	var version: String

	var playbg: Drawable? = null
	var custom_logo: Drawable? = null
	var btn: Drawable? = null
	var btn_: Drawable? = null
	var chainled: Drawable? = null
	var chain: Drawable? = null
	var chain_: Drawable? = null
	var chain__: Drawable? = null
	var phantom: Drawable? = null
	var phantom_: Drawable? = null
	var xml_prev: Drawable? = null
	var xml_play: Drawable? = null
	var xml_pause: Drawable? = null
	var xml_next: Drawable? = null
	var checkbox: Int? = null
	var trace_log: Int? = null
	var option_window: Int? = null
	var option_window_checkbox: Int? = null
	var option_window_btn: Int? = null
	var option_window_btn_text: Int? = null
	var isChainLED = true

	constructor(context: Context, packageName: String?, fullLoad: Boolean) {
		this.packageName = packageName
		customRes = context.packageManager.getResourcesForApplication(packageName)
		defaultRes = context.resources
		icon = getCustomDrawable("theme_ic", drawable.theme_ic)
		version = context.packageManager.getPackageInfo(packageName, 0).versionName
		name = getCustomString("theme_name", string.theme_name)
		description = getCustomString("theme_description", string.theme_description)
		author = getCustomString("theme_author", string.theme_author)
		if (!fullLoad) return

		////////////////////////////////////////////////////////////////////////////////////////////////

		// Drawable

		playbg = getCustomDrawable("playbg", drawable.playbg)
		custom_logo = try {
			getCustomDrawable("custom_logo")
		} catch (ignore: Exception) {
			null
		}
		btn = getCustomDrawable("btn", drawable.btn)
		btn_ = getCustomDrawable("btn_", drawable.btn_)
		try {
			chainled = getCustomDrawable("chainled")
		} catch (e: Exception) {
			isChainLED = false
			chain = getCustomDrawable("chain")
			chain_ = getCustomDrawable("chain_")
			chain__ = getCustomDrawable("chain__")
		}
		phantom = getCustomDrawable("phantom")
		try {
			phantom_ = getCustomDrawable("phantom_")
		} catch (ignore: Exception) {
		}
		xml_prev = getCustomDrawable("xml_prev", drawable.xml_prev)
		xml_play = getCustomDrawable("xml_play", drawable.xml_play)
		xml_pause = getCustomDrawable("xml_pause", drawable.xml_pause)
		xml_next = getCustomDrawable("xml_next", drawable.xml_next)
		checkbox = getCustomColor("checkbox", color.checkbox)
		trace_log = getCustomColor("trace_log", color.trace_log)
		option_window = getCustomColor("option_window", color.option_window)
		option_window_checkbox = getCustomColor("option_window_checkbox", color.option_window_checkbox)
		option_window_btn = getCustomColor("option_window_btn", color.option_window_btn)
		option_window_btn_text = getCustomColor("option_window_btn_text", color.option_window_btn_text)
	}

	constructor(context: Context, fullLoad: Boolean) {
		defaultRes = context.resources
		icon = defaultRes.getDrawable(drawable.theme_ic)
		version = BuildConfig.VERSION_NAME
		name = defaultRes.getString(string.theme_name)
		description = defaultRes.getString(string.theme_description)
		author = defaultRes.getString(string.theme_author)
		if (!fullLoad) return

		////////////////////////////////////////////////////////////////////////////////////////////////

		// Drawable

		playbg = defaultRes.getDrawable(drawable.playbg)
		custom_logo = defaultRes.getDrawable(drawable.custom_logo)
		btn = defaultRes.getDrawable(drawable.btn)
		btn_ = defaultRes.getDrawable(drawable.btn_)
		chainled = defaultRes.getDrawable(drawable.chainled)
		phantom = defaultRes.getDrawable(drawable.phantom)
		phantom_ = defaultRes.getDrawable(drawable.phantom_)
		xml_prev = defaultRes.getDrawable(drawable.xml_prev)
		xml_play = defaultRes.getDrawable(drawable.xml_play)
		xml_pause = defaultRes.getDrawable(drawable.xml_pause)
		xml_next = defaultRes.getDrawable(drawable.xml_next)
		checkbox = defaultRes.getColor(color.checkbox)
		trace_log = defaultRes.getColor(color.trace_log)
		option_window = defaultRes.getColor(color.option_window)
		option_window_checkbox = defaultRes.getColor(color.option_window_checkbox)
		option_window_btn = defaultRes.getColor(color.option_window_btn)
		option_window_btn_text = defaultRes.getColor(color.option_window_btn_text)
	}

	@Throws(Exception::class)
	private fun getCustomDrawable(customId: String): Drawable {
		val resId = getResourceId("drawable", customId)
		return customRes!!.getDrawable(resId)
	}

	@Throws(Exception::class)
	private fun getCustomColor(customId: String): Int {
		val resId = getResourceId("color", customId)
		return customRes!!.getColor(resId)
	}

	@Throws(Exception::class)
	private fun getCustomString(customId: String): String {
		val resId = getResourceId("string", customId)
		return customRes!!.getString(resId)
	}

	private fun getCustomDrawable(customId: String, defaultId: Int): Drawable {
		return try {
			getCustomDrawable(customId)
		} catch (e: Exception) {
			defaultRes.getDrawable(defaultId)
		}
	}

	private fun getCustomColor(customId: String, defaultId: Int): Int {
		return try {
			getCustomColor(customId)
		} catch (e: Exception) {
			defaultRes.getColor(defaultId)
		}
	}

	private fun getCustomString(customId: String, defaultId: Int): String {
		return try {
			getCustomString(customId)
		} catch (e: Exception) {
			defaultRes.getString(defaultId)
		}
	}

	@Throws(Exception::class)
	private fun getResourceId(type: String, customId: String): Int {
		return customRes!!.getIdentifier("$packageName:$type/$customId", null, null)
	}
}