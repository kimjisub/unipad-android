package com.kimjisub.launchpad.diagnostics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.core.content.edit

/**
 * Keeps on disk which screen the app left when it last went to the background. The system exit
 * report of a later launch sends it, because Android's own record of a process it killed in the
 * background names no screen.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal class BackgroundScreenRecorder(
	private val context: Context,
	private val packScreen: Class<out Activity>,
	private val now: () -> Long = System::currentTimeMillis,
) : Application.ActivityLifecycleCallbacks {
	private var started = 0
	private val packScreens = mutableSetOf<Activity>()

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
		if (packScreen.isInstance(activity)) packScreens.add(activity)
	}

	override fun onActivityStarted(activity: Activity) {
		started++
	}

	override fun onActivityStopped(activity: Activity) {
		started--
		if (started > 0 || activity.isChangingConfigurations) return
		context.getSharedPreferences(SystemExitReporter.PREFS, Context.MODE_PRIVATE).edit {
			putString(SCREEN, activity.javaClass.simpleName)
			putBoolean(PACK_OPEN, packScreens.isNotEmpty())
			putLong(SINCE, now())
		}
	}

	override fun onActivityDestroyed(activity: Activity) {
		packScreens.remove(activity)
	}

	override fun onActivityResumed(activity: Activity) {}
	override fun onActivityPaused(activity: Activity) {}
	override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

	companion object {
		private const val SCREEN = "background_screen"
		private const val PACK_OPEN = "background_pack_open"
		private const val SINCE = "background_since"

		fun read(preferences: SharedPreferences): BackgroundScreen? {
			val screen = preferences.getString(SCREEN, null) ?: return null
			return BackgroundScreen(screen, preferences.getBoolean(PACK_OPEN, false), preferences.getLong(SINCE, 0))
		}
	}
}
