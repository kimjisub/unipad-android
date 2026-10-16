package com.kimjisub.launchpad.tool

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.system.Os
import android.system.OsConstants

/**
 * Debug builds only: crashes the app with a native signal, to check what a native crash report
 * carries (screen, keys, logs). Build with -PcrashReportCheck so the report is sent, open the
 * screen to check, then from the shell (the receiver requires android.permission.DUMP):
 *
 *   adb shell am broadcast -n com.kimjisub.launchpad.dev/com.kimjisub.launchpad.tool.NativeCrashCheckReceiver --es check_id <id>
 *
 * Relaunch the app to send the report. It carries test_crash=true and check_id, so it is never
 * read as a user's crash.
 */
class NativeCrashCheckReceiver : BroadcastReceiver() {
	companion object {
		private const val EXTRA_CHECK_ID = "check_id"

		// Crashlytics writes keys and logs on its own thread; a crash before that loses them.
		private const val CRASH_DELAY_MS = 2000L
	}

	override fun onReceive(context: Context, intent: Intent) {
		val checkId = intent.getStringExtra(EXTRA_CHECK_ID) ?: "none"
		FirebaseCrashContext.setKey("test_crash", "true")
		FirebaseCrashContext.setKey(EXTRA_CHECK_ID, checkId)
		FirebaseCrashContext.log("Native crash check $checkId")
		Log.err("Native crash check $checkId: crashing in ${CRASH_DELAY_MS}ms")
		Handler(Looper.getMainLooper()).postDelayed({
			Os.kill(Os.getpid(), OsConstants.SIGSEGV)
		}, CRASH_DELAY_MS)
	}
}
