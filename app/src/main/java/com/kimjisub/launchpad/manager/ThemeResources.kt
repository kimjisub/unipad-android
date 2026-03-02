package com.kimjisub.launchpad.manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.color
import com.kimjisub.launchpad.R.drawable
import com.kimjisub.launchpad.R.string

/**
 * 기본 테마: 앱 자체 리소스를 직접 사용 (PackageManager 불필요)
 */
class DefaultThemeResources(
	context: Context,
	fullLoad: Boolean = false,
) : IThemeResources {
	private val res: Resources = context.resources

	override var icon: Drawable = requireNotNull(ResourcesCompat.getDrawable(res, drawable.theme_ic, null))
	override var name: String = res.getString(string.theme_name)
	override var author: String = res.getString(string.theme_author)
	override var version: String = BuildConfig.VERSION_NAME

	override var playbg: Drawable? = null
	override var customLogo: Drawable? = null
	override var btn: Drawable? = null
	override var btnPressed: Drawable? = null
	override var chainled: Drawable? = null
	override var chain: Drawable? = null
	override var chainSelected: Drawable? = null
	override var chainGuide: Drawable? = null
	override var phantom: Drawable? = null
	override var phantomVariant: Drawable? = null
	override var xmlPrev: Drawable? = null
	override var xmlPlay: Drawable? = null
	override var xmlPause: Drawable? = null
	override var xmlNext: Drawable? = null
	override var checkbox: Int? = null
	override var traceLog: Int? = null
	override var optionWindow: Int? = null
	override var optionWindowCheckbox: Int? = null
	override var isChainLed = true

	init {
		if (fullLoad) {
			playbg = ResourcesCompat.getDrawable(res, drawable.playbg, null)
			btn = ResourcesCompat.getDrawable(res, drawable.btn, null)
			btnPressed = ResourcesCompat.getDrawable(res, drawable.btn_, null)
			chainled = ResourcesCompat.getDrawable(res, drawable.chainled, null)
			phantom = ResourcesCompat.getDrawable(res, drawable.phantom, null)
			xmlPrev = ResourcesCompat.getDrawable(res, drawable.xml_prev, null)
			xmlPlay = ResourcesCompat.getDrawable(res, drawable.xml_play, null)
			xmlPause = ResourcesCompat.getDrawable(res, drawable.xml_pause, null)
			xmlNext = ResourcesCompat.getDrawable(res, drawable.xml_next, null)
			checkbox = ResourcesCompat.getColor(res, color.checkbox, null)
			traceLog = ResourcesCompat.getColor(res, color.trace_log, null)
			optionWindow = ResourcesCompat.getColor(res, color.option_window, null)
			optionWindowCheckbox = ResourcesCompat.getColor(res, color.option_window_checkbox, null)
		}
	}
}

/**
 * 외부 테마 패키지: PackageManager를 통해 다른 앱의 리소스 로드
 */
class ThemeResources(
	context: Context,
	private val packageName: String,
	fullLoad: Boolean = false,
) : IThemeResources {
	var resources: Resources = try {
		context.packageManager.getResourcesForApplication(packageName)
	} catch (_: PackageManager.NameNotFoundException) {
		context.resources
	}
	var defaultRes: Resources = context.resources

	override var icon: Drawable
	override var name: String
	override var author: String
	override var version: String

	override var playbg: Drawable? = null
	override var customLogo: Drawable? = null
	override var btn: Drawable? = null
	override var btnPressed: Drawable? = null
	override var chainled: Drawable? = null
	override var chain: Drawable? = null
	override var chainSelected: Drawable? = null
	override var chainGuide: Drawable? = null
	override var phantom: Drawable? = null
	override var phantomVariant: Drawable? = null
	override var xmlPrev: Drawable? = null
	override var xmlPlay: Drawable? = null
	override var xmlPause: Drawable? = null
	override var xmlNext: Drawable? = null
	override var checkbox: Int? = null
	override var traceLog: Int? = null
	override var optionWindow: Int? = null
	override var optionWindowCheckbox: Int? = null
	override var isChainLed = true

	init {
		icon = requireNotNull(getDrawable("theme_ic", drawable.theme_ic))
		version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName ?: "unknown"
		} else {
			@Suppress("DEPRECATION")
			context.packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
		}
		name = requireNotNull(getString("theme_name", string.theme_name))
		author = requireNotNull(getString("theme_author", string.theme_author))
		if (fullLoad) {
			playbg = getDrawable("playbg", drawable.playbg)
			customLogo = try {
				getDrawable("custom_logo")
			} catch (_: Resources.NotFoundException) {
				null
			}
			btn = getDrawable("btn", drawable.btn)
			btnPressed = getDrawable("btn_", drawable.btn_)
			try {
				chainled = getDrawable("chainled")
			} catch (_: Resources.NotFoundException) {
				isChainLed = false
				chain = getDrawable("chain", drawable.chain)
				chainSelected = getDrawable("chain_", drawable.chain_)
				chainGuide = getDrawable("chain__", drawable.chain__)
			}
			phantom = getDrawable("phantom", drawable.phantom)
			phantomVariant = try {
				getDrawable("phantom_")
			} catch (_: Resources.NotFoundException) {
				null
			}
			xmlPrev = getDrawable("xml_prev", drawable.xml_prev)
			xmlPlay = getDrawable("xml_play", drawable.xml_play)
			xmlPause = getDrawable("xml_pause", drawable.xml_pause)
			xmlNext = getDrawable("xml_next", drawable.xml_next)
			checkbox = getColor("checkbox", color.checkbox)
			traceLog = getColor("trace_log", color.trace_log)
			optionWindow = getColor("option_window", color.option_window)
			optionWindowCheckbox =
				getColor("option_window_checkbox", color.option_window_checkbox)
		}
	}

	@SuppressLint("DiscouragedApi")
	@Throws(Resources.NotFoundException::class)
	private fun getResourceId(resources: Resources, type: String, customId: String): Int {
		return resources.getIdentifier(customId, type, packageName)
	}

	fun getDrawable(resName: String, defaultId: Int? = null): Drawable? {
		return try {
			val resId = getResourceId(resources, "drawable", resName)
			ResourcesCompat.getDrawable(resources, resId, null)
		} catch (e: Resources.NotFoundException) {
			if (defaultId != null)
				ResourcesCompat.getDrawable(defaultRes, defaultId, null)
			else
				throw e
		}
	}

	fun getColor(resName: String, defaultId: Int? = 0): Int? {
		return try {
			val resId = getResourceId(resources, "color", resName)
			ResourcesCompat.getColor(resources, resId, null)
		} catch (e: Resources.NotFoundException) {
			if (defaultId != null)
				ResourcesCompat.getColor(defaultRes, defaultId, null)
			else
				throw e
		}
	}

	fun getString(resName: String, defaultId: Int?): String? {
		return try {
			val resId = getResourceId(resources, "string", resName)
			resources.getString(resId)
		} catch (e: Resources.NotFoundException) {
			if (defaultId != null)
				defaultRes.getString(defaultId)
			else
				throw e
		}
	}
}

fun loadTheme(context: Context, themeId: String, fullLoad: Boolean = false): IThemeResources {
	return when {
		themeId.startsWith("asset://") -> {
			val themeName = themeId.removePrefix("asset://")
			AssetThemeResources(context, "themes/$themeName", fullLoad)
		}
		themeId.startsWith("zip://") -> {
			val folderName = themeId.removePrefix("zip://")
			val themesDir = context.getExternalFilesDir(null)?.let { java.io.File(it, "themes/$folderName") }
				?: throw java.io.FileNotFoundException("External files dir not available")
			ZipThemeResources(context, themesDir, fullLoad)
		}
		themeId == context.packageName || themeId == BuildConfig.APPLICATION_ID -> {
			DefaultThemeResources(context, fullLoad)
		}
		else -> {
			ThemeResources(context, themeId, fullLoad)
		}
	}
}