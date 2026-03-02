package com.kimjisub.launchpad.adapter

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.res.ResourcesCompat
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.ZipThemeMetadata
import com.kimjisub.launchpad.tool.Log
import kotlinx.serialization.json.Json
import java.io.File

class ThemeItem private constructor(
	val id: String,
	val icon: Drawable,
	val name: String,
	val author: String,
	val version: String?,
	val isBundled: Boolean = false,
) {
	val isZipTheme: Boolean get() = id.startsWith("zip://")
	val isDeletable: Boolean get() = isZipTheme && !isBundled

	companion object {
		fun defaultTheme(context: Context): ThemeItem {
			val icon = requireNotNull(ResourcesCompat.getDrawable(context.resources, R.drawable.theme_ic, null))
			val name = context.getString(R.string.theme_name)
			val author = context.getString(R.string.theme_author)
			return ThemeItem(context.packageName, icon, name, author, BuildConfig.VERSION_NAME)
		}

		@SuppressLint("DiscouragedApi")
		fun fromPackage(context: Context, packageName: String): ThemeItem {
			val res = context.packageManager.getResourcesForApplication(packageName)
			val icon = requireNotNull(
				ResourcesCompat.getDrawable(res, res.getIdentifier("$packageName:drawable/theme_ic", null, null), null)
			)
			val name = res.getString(res.getIdentifier("$packageName:string/theme_name", null, null))
			val author = res.getString(res.getIdentifier("$packageName:string/theme_author", null, null))
			val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
			} else {
				@Suppress("DEPRECATION")
				context.packageManager.getPackageInfo(packageName, 0).versionName
			}
			return ThemeItem(packageName, icon, name, author, version)
		}

		fun fromZipDir(context: Context, dir: File, isBundled: Boolean = false): ThemeItem {
			val json = Json { ignoreUnknownKeys = true }
			val themeJsonFile = File(dir, "theme.json")
			val metadata = json.decodeFromString<ZipThemeMetadata>(themeJsonFile.readText())
			val iconFile = File(dir, "theme_ic.png")
			val icon: Drawable = if (iconFile.exists()) {
				val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
				BitmapDrawable(context.resources, bitmap)
			} else {
				requireNotNull(ResourcesCompat.getDrawable(context.resources, R.drawable.theme_ic, null))
			}
			return ThemeItem("zip://${dir.name}", icon, metadata.name, metadata.author, metadata.version, isBundled)
		}
	}
}

object ThemeTool {
	fun getThemePackList(context: Context): MutableList<ThemeItem> {
		val ret = mutableListOf<ThemeItem>()
		ret.add(ThemeItem.defaultTheme(context))
		val packages: List<ApplicationInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			context.packageManager.getInstalledApplications(
				PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
			)
		} else {
			@Suppress("DEPRECATION")
			context.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
		}
		for (applicationInfo in packages) {
			val packageName: String = applicationInfo.packageName
			if (packageName.startsWith("com.kimjisub.launchpad.theme.")) {
				try {
					ret.add(ThemeItem.fromPackage(context, packageName))
				} catch (e: Exception) {
					Log.err("Theme pack load failed: $packageName", e)
				}
			}
		}

		// ZIP themes from themes/
		val themesDir = context.getExternalFilesDir(null)?.let { File(it, "themes") }
		if (themesDir != null && themesDir.isDirectory) {
			val bundledNames = try {
				context.assets.list("themes")
					?.filter { it.endsWith(".zip") }
					?.map { it.removeSuffix(".zip") }
					?.toSet() ?: emptySet()
			} catch (_: Exception) {
				emptySet()
			}

			themesDir.listFiles()?.forEach { dir ->
				if (dir.isDirectory && File(dir, "theme.json").exists()) {
					try {
						val isBundled = dir.name in bundledNames
						ret.add(ThemeItem.fromZipDir(context, dir, isBundled))
					} catch (e: Exception) {
						Log.err("ZIP theme load failed: ${dir.name}", e)
					}
				}
			}
		}

		return ret
	}
}