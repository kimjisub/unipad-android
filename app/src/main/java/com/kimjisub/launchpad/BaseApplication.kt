package com.kimjisub.launchpad

import android.app.Application
import android.content.Context
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.kimjisub.launchpad.di.appModule
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.NotificationManager
import com.kimjisub.launchpad.tool.Log
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.Logger
import com.orhanobut.logger.PrettyFormatStrategy
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import java.io.File

class BaseApplication : Application() {
	companion object {
		private const val DEBUG_REMOTE_CONFIG_FETCH_INTERVAL_SECONDS = 60L
		private const val PREF_NAME = "data"
		private const val KEY_SELECTED_THEME = "SelectedTheme"
	}

	override fun onCreate() {
		super.onCreate()

		setupNotification()
		setupLogger()
		setupRemoteConfig()
		setupBundledThemes()

		startKoin {
			androidContext(applicationContext)
			modules(appModule)
		}
	}

	private fun setupNotification() {
		// minSdk 29+ always supports notification channels (introduced in API 26)
		NotificationManager.createChannel(this)
	}

	private fun setupLogger() {
		val formatStrategy = PrettyFormatStrategy.newBuilder()
			.showThreadInfo(true)
			.methodCount(2)
			.methodOffset(5)
			.tag("com.kimjisub._")
			.build()
		Logger.addLogAdapter(AndroidLogAdapter(formatStrategy))
		Logger.d("Logger Ready")
	}

	private fun setupBundledThemes() {
		migrateBundledThemePreference()
		cleanupLegacyExtractedThemes()
	}

	private fun migrateBundledThemePreference() {
		try {
			val pref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
			val selectedTheme = pref.getString(KEY_SELECTED_THEME, null) ?: return

			val bundledNames = getBundledAssetThemeNames()
			if (bundledNames.isEmpty()) return

			for (name in bundledNames) {
				if (selectedTheme == "zip://$name") {
					pref.edit().putString(KEY_SELECTED_THEME, "asset://$name").apply()
					Log.log("Migrated theme preference: zip://$name → asset://$name")
					break
				}
			}
		} catch (e: Exception) {
			Log.err("Theme preference migration failed", e)
		}
	}

	private fun cleanupLegacyExtractedThemes() {
		try {
			val themesDir = getExternalFilesDir(null)?.let { File(it, "themes") } ?: return
			if (!themesDir.exists()) return

			val bundledNames = getBundledAssetThemeNames()
			for (name in bundledNames) {
				val legacyDir = File(themesDir, name)
				if (legacyDir.exists() && legacyDir.isDirectory) {
					FileManager.deleteDirectory(legacyDir)
					Log.log("Cleaned up legacy extracted theme: $name")
				}
			}
		} catch (e: Exception) {
			Log.err("Legacy theme cleanup failed", e)
		}
	}

	private fun getBundledAssetThemeNames(): Set<String> {
		return try {
			val dirs = assets.list("themes") ?: return emptySet()
			dirs.filter { dirName ->
				val files = assets.list("themes/$dirName") ?: return@filter false
				"theme.json" in files
			}.toSet()
		} catch (_: Exception) {
			emptySet()
		}
	}

	private fun setupRemoteConfig() {
		val remoteConfig = FirebaseRemoteConfig.getInstance()

		val configSettings = FirebaseRemoteConfigSettings.Builder().apply {
			if (BuildConfig.DEBUG)
				minimumFetchIntervalInSeconds = DEBUG_REMOTE_CONFIG_FETCH_INTERVAL_SECONDS
		}.build()

		remoteConfig.setConfigSettingsAsync(configSettings)
		remoteConfig.fetchAndActivate()
	}

}