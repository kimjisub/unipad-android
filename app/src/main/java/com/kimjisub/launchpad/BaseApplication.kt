package com.kimjisub.launchpad

import android.os.Build
import androidx.multidex.MultiDexApplication
import com.kimjisub.launchpad.manager.NotificationManager
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.Logger
import com.orhanobut.logger.PrettyFormatStrategy

class BaseApplication : MultiDexApplication() {
	override fun onCreate() {
		super.onCreate()

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
			NotificationManager.createChannel(this)

		val formatStrategy = PrettyFormatStrategy.newBuilder()
				.showThreadInfo(true)
				.methodCount(2)
				.methodOffset(5)
				.tag("com.kimjisub._")
				.build()
		Logger.addLogAdapter(AndroidLogAdapter())
		Logger.d("Logger Ready")
	}
}