package com.kimjisub.launchpad.manager

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
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
			checkbox = ResourcesCompat.getColor(res, color.checkbox, null)
			traceLog = ResourcesCompat.getColor(res, color.trace_log, null)
			optionWindow = ResourcesCompat.getColor(res, color.option_window, null)
			optionWindowCheckbox = ResourcesCompat.getColor(res, color.option_window_checkbox, null)
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
		else -> {
			DefaultThemeResources(context, fullLoad)
		}
	}
}