package com.kimjisub.launchpad.manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.*

class ThemeResources(
	context: Context,
	private val packageName: String = BuildConfig.APPLICATION_ID,
	fullLoad: Boolean = false
) {
	var resources: Resources
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
	var isChainLed = true

	init {

		defaultRes = context.resources
		resources = if (packageName == context.packageName)
			context.resources
		else
			context.packageManager.getResourcesForApplication(packageName)


		icon = getDrawable("theme_ic", drawable.theme_ic)!!
		version = context.packageManager.getPackageInfo(packageName, 0).versionName
		name = getString("theme_name", string.theme_name)!!
		description = getString("theme_description", string.theme_description)!!
		author = getString("theme_author", string.theme_author)!!
		if (fullLoad) {
			playbg = getDrawable("playbg", drawable.playbg)
			custom_logo = try {
				getDrawable("custom_logo")
			} catch (e: Exception) {
				null
			}
			btn = getDrawable("btn", drawable.btn)
			btn_ = getDrawable("btn_", drawable.btn_)
			try {
				chainled = getDrawable("chainled")
			} catch (e: Exception) {
				isChainLed = false
				chain = getDrawable("chain", drawable.chain)
				chain_ = getDrawable("chain_", drawable.chain_)
				chain__ = getDrawable("chain__", drawable.chain__)
			}
			phantom = getDrawable("phantom", drawable.phantom)
			phantom_ = try {
				getDrawable("phantom_")
			} catch (ignore: Exception) {
				null
			}
			xml_prev = getDrawable("xml_prev", drawable.xml_prev)
			xml_play = getDrawable("xml_play", drawable.xml_play)
			xml_pause = getDrawable("xml_pause", drawable.xml_pause)
			xml_next = getDrawable("xml_next", drawable.xml_next)
			checkbox = getColor("checkbox", color.checkbox)
			trace_log = getColor("trace_log", color.trace_log)
			option_window = getColor("option_window", color.option_window)
			option_window_checkbox =
				getColor("option_window_checkbox", color.option_window_checkbox)
			option_window_btn = getColor("option_window_btn", color.option_window_btn)
			option_window_btn_text =
				getColor("option_window_btn_text", color.option_window_btn_text)
		}
	}

	@Throws(Exception::class)
	private fun getResourceId(resources: Resources, type: String, customId: String): Int {
		return resources.getIdentifier(customId, type, packageName)
	}

	@SuppressLint("UseCompatLoadingForDrawables")
	fun getDrawable(resName: String, defaultId: Int? = null): Drawable? {
		return try {
			val resId = getResourceId(resources, "drawable", resName)
			ResourcesCompat.getDrawable(resources, resId, null)
		} catch (e: Exception) {
			if (defaultId != null)
				ResourcesCompat.getDrawable(defaultRes, defaultId, null)
			else
				throw e
		}
	}

	@SuppressLint("UseCompatLoadingForDrawables")
	fun getColor(resName: String, defaultId: Int? = 0): Int? {
		return try {
			val resId = getResourceId(resources, "color", resName)
			ResourcesCompat.getColor(resources, resId, null)
		} catch (e: Exception) {
			if (defaultId != null)
				ResourcesCompat.getColor(defaultRes, defaultId, null)
			else
				throw e
		}
	}

	@SuppressLint("UseCompatLoadingForDrawables")
	fun getString(resName: String, defaultId: Int?): String? {
		return try {
			val resId = getResourceId(resources, "string", resName)
			resources.getString(resId)
		} catch (e: Exception) {
			if (defaultId != null)
				defaultRes.getString(defaultId)
			else
				throw e
		}
	}
}