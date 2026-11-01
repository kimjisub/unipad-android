package com.kimjisub.launchpad.diagnostics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.kimjisub.launchpad.tool.screenName

/**
 * Keeps on disk which screen the app left when it last went to the background. The system exit
 * report of a later launch sends it, because Android's own record of a process it killed in the
 * background names no screen.
 */
@RequiresApi(Build.VERSION_CODES.R)
internal class BackgroundScreenRecorder(
	private val context: Context,
	private val packScreen: Class<out Activity>,
	private val pid: Int = Process.myPid(),
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
		preferences().edit {
			putString(SCREEN, activity.screenName())
			putBoolean(PACK_OPEN, packScreens.isNotEmpty())
			putLong(SINCE, now())
			putInt(PID, pid)
		}
	}

	override fun onActivityDestroyed(activity: Activity) {
		val closed = packScreens.remove(activity) && !activity.isChangingConfigurations
		// The last pack screen can close after the app left: backed out of, or finished by the system.
		if (closed && packScreens.isEmpty() && started == 0) preferences().edit { putBoolean(PACK_OPEN, false) }
	}

	override fun onActivityResumed(activity: Activity) {}
	override fun onActivityPaused(activity: Activity) {}
	override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

	private fun preferences() = context.getSharedPreferences(SystemExitReporter.PREFS, Context.MODE_PRIVATE)

	companion object {
		private const val SCREEN = "background_screen"
		private const val PACK_OPEN = "background_pack_open"
		private const val SINCE = "background_since"
		private const val PID = "background_pid"

		fun read(preferences: SharedPreferences): BackgroundScreen? {
			val screen = preferences.getString(SCREEN, null) ?: return null
			return BackgroundScreen(
				screen,
				preferences.getBoolean(PACK_OPEN, false),
				preferences.getLong(SINCE, 0),
				preferences.getInt(PID, 0),
			)
		}
	}
}
