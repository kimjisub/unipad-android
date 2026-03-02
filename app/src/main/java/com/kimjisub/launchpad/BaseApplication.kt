package com.kimjisub.launchpad

import android.app.Application
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.kimjisub.launchpad.di.appModule
import com.kimjisub.launchpad.manager.NotificationManager
import com.kimjisub.launchpad.tool.Log
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.Logger
import com.orhanobut.logger.PrettyFormatStrategy
import net.lingala.zip4j.ZipFile
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin
import java.io.File

class BaseApplication : Application() {
	companion object {
		private const val DEBUG_REMOTE_CONFIG_FETCH_INTERVAL_SECONDS = 60L
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
		try {
			val themesDir = getExternalFilesDir(null)?.let { File(it, "themes") } ?: return
			if (!themesDir.exists()) themesDir.mkdirs()

			val assetThemes = assets.list("themes") ?: return
			for (zipName in assetThemes) {
				if (!zipName.endsWith(".zip")) continue
				val folderName = zipName.removeSuffix(".zip")
				val targetDir = File(themesDir, folderName)
				if (targetDir.exists()) continue // already extracted

				targetDir.mkdirs()
				val tempZip = File.createTempFile("bundled_theme_", ".zip", cacheDir)
				try {
					assets.open("themes/$zipName").use { input ->
						tempZip.outputStream().use { output -> input.copyTo(output) }
					}
					ZipFile(tempZip).use { zip -> zip.extractAll(targetDir.path) }
				} finally {
					tempZip.delete()
				}
			}
		} catch (e: Exception) {
			Log.err("Bundled theme extraction failed", e)
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