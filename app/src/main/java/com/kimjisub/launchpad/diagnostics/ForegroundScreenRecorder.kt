package com.kimjisub.launchpad.diagnostics

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import com.kimjisub.launchpad.tool.screenName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Saves screen transitions for the next launch's system exit report, without disk work during play. */
@RequiresApi(Build.VERSION_CODES.R)
internal class ForegroundScreenRecorder(
	private val context: Context,
	private val packScreen: Class<out Activity>,
	private val pid: Int = Process.myPid(),
	private val processStartedAt: Long = System.currentTimeMillis() -
		(SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()),
	private val now: () -> Long = System::currentTimeMillis,
) : Application.ActivityLifecycleCallbacks {
	private val packScreens = mutableSetOf<Activity>()
	private var resumed: Activity? = null
	// Only the latest screen is needed if storage or collection is slow. Never retain activities on IO.
	private val pending = Channel<ForegroundScreen>(Channel.CONFLATED)

	/** Call once with an IO scope. Read the previous process before any current-process write. */
	fun start(scope: CoroutineScope, collectPreviousExit: () -> Unit) = scope.launch {
		collectPreviousExit()
		persist {
			remove(SCREEN)
			remove(PACK_OPEN)
			remove(SINCE)
			remove(PID)
			putLong(PROCESS_STARTED_AT, processStartedAt)
			putInt(PROCESS_PID, pid)
		}
		for (record in pending) persist {
			putString(SCREEN, record.screen)
			putBoolean(PACK_OPEN, record.packOpen)
			putLong(SINCE, record.since)
			putInt(PID, record.pid)
			putLong(PROCESS_STARTED_AT, processStartedAt)
			putInt(PROCESS_PID, pid)
		}
	}

	@WorkerThread
	private fun persist(write: SharedPreferences.Editor.() -> Unit) {
		try {
			val editor = context.getSharedPreferences(SystemExitReporter.PREFS, Context.MODE_PRIVATE).edit()
			editor.write()
			if (!editor.commit()) Log.w("ForegroundScreen", "Could not persist foreground screen")
		} catch (e: Exception) {
			// Diagnostics must not prevent launch or later screen transitions when storage is unavailable.
			Log.w("ForegroundScreen", "Could not save foreground screen", e)
		}
	}

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
		if (packScreen.isInstance(activity)) packScreens.add(activity)
	}

	override fun onActivityResumed(activity: Activity) {
		if (resumed === activity) return
		resumed = activity
		pending.trySend(ForegroundScreen(activity.screenName(), packScreens.isNotEmpty(), now(), pid, processStartedAt))
	}

	override fun onActivityPaused(activity: Activity) {
		if (resumed === activity) resumed = null
	}

	override fun onActivityDestroyed(activity: Activity) {
		packScreens.remove(activity)
		if (resumed === activity) resumed = null
	}

	override fun onActivityStarted(activity: Activity) {}
	override fun onActivityStopped(activity: Activity) {}
	override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

	companion object {
		private const val SCREEN = "foreground_screen"
		private const val PACK_OPEN = "foreground_pack_open"
		private const val SINCE = "foreground_since"
		private const val PID = "foreground_pid"
		private const val PROCESS_STARTED_AT = "process_started_at"
		private const val PROCESS_PID = "process_started_pid"

		fun read(preferences: SharedPreferences): ForegroundScreen? {
			if (listOf(SCREEN, PACK_OPEN, SINCE, PID, PROCESS_STARTED_AT, PROCESS_PID).any { !preferences.contains(it) }) return null
			val screen = preferences.getString(SCREEN, null)?.takeIf { it.isNotEmpty() } ?: return null
			val pid = preferences.getInt(PID, 0)
			if (pid <= 0 || pid != preferences.getInt(PROCESS_PID, 0)) return null
			return ForegroundScreen(screen, preferences.getBoolean(PACK_OPEN, false), preferences.getLong(SINCE, 0),
				pid, preferences.getLong(PROCESS_STARTED_AT, 0))
		}
	}
}
