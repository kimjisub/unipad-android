package com.kimjisub.launchpad.manager

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.kimjisub.launchpad.R
import kotlinx.serialization.json.Json
import java.io.File

class ZipThemeResources(
	private val context: Context,
	private val themeDir: File,
	fullLoad: Boolean = false,
) : IThemeResources {

	private val json = Json { ignoreUnknownKeys = true }
	private val metadata: ZipThemeMetadata
	private val colors: ZipThemeColors?

	override val icon: Drawable
	override val name: String
	override val author: String
	override val version: String

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
	override var isChainLed: Boolean = true

	init {
		val themeJsonFile = File(themeDir, "theme.json")
		require(themeJsonFile.exists()) { "theme.json not found in ${themeDir.absolutePath}" }
		metadata = json.decodeFromString<ZipThemeMetadata>(themeJsonFile.readText())

		val colorsJsonFile = File(themeDir, "colors.json")
		colors = if (colorsJsonFile.exists()) {
			json.decodeFromString<ZipThemeColors>(colorsJsonFile.readText())
		} else null

		icon = loadPng("theme_ic") ?: defaultDrawable(R.drawable.theme_ic)
		name = metadata.name
		author = metadata.author
		version = metadata.version

		if (fullLoad) {
			playbg = loadPng("playbg") ?: defaultDrawable(R.drawable.playbg)
			customLogo = loadPng("custom_logo")
			btn = loadPng("btn") ?: defaultDrawable(R.drawable.btn)
			btnPressed = loadPng("btn_") ?: defaultDrawable(R.drawable.btn_)

			val chainledDrawable = loadPng("chainled")
			if (chainledDrawable != null) {
				chainled = chainledDrawable
			} else {
				isChainLed = false
				chain = loadPng("chain") ?: defaultDrawable(R.drawable.chain)
				chainSelected = loadPng("chain_") ?: defaultDrawable(R.drawable.chain_)
				chainGuide = loadPng("chain__") ?: defaultDrawable(R.drawable.chain__)
			}

			phantom = loadPng("phantom") ?: defaultDrawable(R.drawable.phantom)
			phantomVariant = loadPng("phantom_")

			checkbox = parseColor(colors?.checkbox) ?: defaultColor(R.color.checkbox)
			traceLog = parseColor(colors?.traceLog) ?: defaultColor(R.color.trace_log)
			optionWindow = parseColor(colors?.optionWindow) ?: defaultColor(R.color.option_window)
			optionWindowCheckbox = parseColor(colors?.optionWindowCheckbox) ?: defaultColor(R.color.option_window_checkbox)
		}
	}

	private fun loadPng(name: String): Drawable? {
		val file = File(themeDir, "$name.png")
		if (!file.exists()) return null
		val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
		return BitmapDrawable(context.resources, bitmap)
	}

	private fun defaultDrawable(resId: Int): Drawable {
		return requireNotNull(ResourcesCompat.getDrawable(context.resources, resId, null))
	}

	private fun defaultColor(resId: Int): Int {
		return ResourcesCompat.getColor(context.resources, resId, null)
	}

	private fun parseColor(hex: String?): Int? {
		if (hex == null) return null
		return try {
			Color.parseColor(hex)
		} catch (_: IllegalArgumentException) {
			null
		}
	}
}
