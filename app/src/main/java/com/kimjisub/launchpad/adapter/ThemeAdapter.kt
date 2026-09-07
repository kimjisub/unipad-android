package com.kimjisub.launchpad.adapter

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.res.ResourcesCompat
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.ZipThemeMetadata
import com.kimjisub.launchpad.tool.Log
import kotlinx.serialization.json.Json
import java.io.File

enum class ThemeType {
	BUILTIN,
	ZIP,
}

class ThemeItem private constructor(
	val id: String,
	val icon: Drawable,
	val name: String,
	val author: String,
	val version: String?,
	val type: ThemeType,
	val isBundled: Boolean = false,
) {
	val isZipTheme: Boolean get() = id.startsWith("zip://")
	val isAssetTheme: Boolean get() = id.startsWith("asset://")
	val isDeletable: Boolean get() = isZipTheme && !isBundled

	companion object {
		fun defaultTheme(context: Context): ThemeItem {
			val icon = requireNotNull(ResourcesCompat.getDrawable(context.resources, R.drawable.theme_ic, null))
			val name = context.getString(R.string.theme_name)
			val author = context.getString(R.string.theme_author)
			return ThemeItem(context.packageName, icon, name, author, BuildConfig.VERSION_NAME, ThemeType.BUILTIN)
		}

		fun fromAssetDir(context: Context, assetPath: String, dirName: String): ThemeItem {
			val json = Json { ignoreUnknownKeys = true }
			val themeJsonText = context.assets.open("$assetPath/theme.json").bufferedReader().use { it.readText() }
			val metadata = json.decodeFromString<ZipThemeMetadata>(themeJsonText)
			val icon: Drawable = try {
				val inputStream = context.assets.open("$assetPath/theme_ic.png")
				val bitmap = BitmapFactory.decodeStream(inputStream)
				inputStream.close()
				BitmapDrawable(context.resources, bitmap)
			} catch (_: Exception) {
				requireNotNull(ResourcesCompat.getDrawable(context.resources, R.drawable.theme_ic, null))
			}
			return ThemeItem("asset://$dirName", icon, metadata.name, metadata.author, metadata.version, ThemeType.BUILTIN, isBundled = true)
		}

		fun fromZipDir(context: Context, dir: File, isBundled: Boolean = false): ThemeItem {
			val json = Json { ignoreUnknownKeys = true }
			val themeJsonFile = File(dir, "theme.json")
			val metadata = json.decodeFromString<ZipThemeMetadata>(themeJsonFile.readText())
			val iconFile = File(dir, "theme_ic.png")
			val icon: Drawable = decodeIconBounded(iconFile)?.let { BitmapDrawable(context.resources, it) }
				?: requireNotNull(ResourcesCompat.getDrawable(context.resources, R.drawable.theme_ic, null))
			return ThemeItem("zip://${dir.name}", icon, metadata.name, metadata.author, metadata.version, ThemeType.ZIP, isBundled)
		}

		private const val MAX_ICON_EDGE = 512

		/** A user theme icon is arbitrary; a 4000x4000 PNG decoded at full size threw OOM out of composition. */
		private fun decodeIconBounded(file: File): android.graphics.Bitmap? {
			if (!file.exists()) return null
			val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
			BitmapFactory.decodeFile(file.absolutePath, bounds)
			if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
			var sample = 1
			while (bounds.outWidth / sample > MAX_ICON_EDGE || bounds.outHeight / sample > MAX_ICON_EDGE) sample *= 2
			val opts = BitmapFactory.Options().apply { inSampleSize = sample }
			return try {
				BitmapFactory.decodeFile(file.absolutePath, opts)
			} catch (e: OutOfMemoryError) {
				Log.err("theme icon too large: ${file.path}", e)
				null
			}
		}
	}
}

object ThemeTool {
	fun getThemePackList(context: Context): MutableList<ThemeItem> {
		val ret = mutableListOf<ThemeItem>()
		ret.add(ThemeItem.defaultTheme(context))

		// Bundled asset themes
		val bundledNames = mutableSetOf<String>()
		try {
			val assetThemeDirs = context.assets.list("themes") ?: emptyArray()
			for (dirName in assetThemeDirs) {
				val files = context.assets.list("themes/$dirName") ?: continue
				if ("theme.json" in files) {
					try {
						ret.add(ThemeItem.fromAssetDir(context, "themes/$dirName", dirName))
						bundledNames.add(dirName)
					} catch (e: Exception) {
						Log.err("Asset theme load failed: $dirName", e)
					}
				}
			}
		} catch (_: Exception) {}

		// User-imported ZIP themes from external storage
		val themesDir = context.getExternalFilesDir(null)?.let { File(it, "themes") }
		if (themesDir != null && themesDir.isDirectory) {
			themesDir.listFiles()?.forEach { dir ->
				if (dir.isDirectory && dir.name !in bundledNames && File(dir, "theme.json").exists()) {
					try {
						ret.add(ThemeItem.fromZipDir(context, dir))
					} catch (e: Exception) {
						Log.err("ZIP theme load failed: ${dir.name}", e)
					}
				}
			}
		}

		return ret
	}
}