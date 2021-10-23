package com.kimjisub.launchpad

import android.app.Application
import android.os.Build
import com.google.android.gms.ads.MobileAds
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.kimjisub.launchpad.manager.Constant
import com.kimjisub.launchpad.manager.NotificationManager
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.Logger
import com.orhanobut.logger.PrettyFormatStrategy

class BaseApplication : Application() {
	override fun onCreate() {
		super.onCreate()

		setupNotification()
		setupLogger()
		setupRemoteConfig()

		MobileAds.initialize(this) { }
		appOpenManager = AppOpenManager(this)
	}

	private fun setupNotification() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			NotificationManager.createChannel(this)
	}

	private fun setupLogger() {
		val formatStrategy = PrettyFormatStrategy.newBuilder()
			.showThreadInfo(true)
			.methodCount(2)
			.methodOffset(5)
			.tag("com.kimjisub._")
			.build()
		Logger.addLogAdapter(AndroidLogAdapter())
		Logger.d("Logger Ready")
	}

	private fun setupRemoteConfig() {
		val remoteConfig = FirebaseRemoteConfig.getInstance()

		val configSettings = FirebaseRemoteConfigSettings.Builder().apply {
			if (BuildConfig.DEBUG)
				minimumFetchIntervalInSeconds = 60
		}.build()

		remoteConfig.setConfigSettingsAsync(configSettings)
		remoteConfig.fetchAndActivate()
	}

	companion object {
		private lateinit var appOpenManager: AppOpenManager
	}
}