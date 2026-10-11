package com.kimjisub.launchpad.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kimjisub.launchpad.BuildConfig

@RequiresApi(Build.VERSION_CODES.R)
internal object SystemExitReporter {
    private const val PREFS = "system_exit_diagnostics"
    private const val CHECKPOINT = "last_processed_timestamp"
    private const val TAG = "SystemExitReporter"

    /** Must run on an IO dispatcher: both the system history and checkpoint use disk. */
    fun collect(context: Context) {
        val now = System.currentTimeMillis()
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastProcessed = if (preferences.contains(CHECKPOINT))
            preferences.getLong(CHECKPOINT, 0) else null
        val manager = context.getSystemService(ActivityManager::class.java) ?: return
        val lmkSupported = ActivityManager.isLowMemoryKillReportSupported()
        val history = manager.getHistoricalProcessExitReasons(context.packageName, 0, 0).map {
            SystemExit(it.reason, it.status, it.description, it.importance, it.timestamp, it.pss, it.rss)
        }
        val batch = SystemExitPolicy.select(history, lastProcessed, now, lmkSupported)
        for (exit in batch.reports) {
            FirebaseCrashlytics.getInstance().recordException(exceptionFor(exit), keysFor(exit, lmkSupported))
            Log.d(TAG, "Recorded system exit reason=${exit.reason} timestamp=${exit.timestamp}")
        }
        preferences.edit(commit = true) {
            putLong(CHECKPOINT, batch.checkpoint)
        }
    }

    private fun keysFor(exit: SystemExit, lmkSupported: Boolean) = CustomKeysAndValues.Builder()
        .putInt("exit_reason", exit.reason)
        .putString("exit_description", exit.description.orEmpty().take(1024))
        .putInt("exit_status", exit.status)
        .putInt("exit_importance", exit.importance)
        .putLong("exit_pss_kb", exit.pssKb)
        .putLong("exit_rss_kb", exit.rssKb)
        .putLong("exit_timestamp", exit.timestamp)
        .putBoolean("lmk_report_supported", lmkSupported)
        .apply {
            // Explicit server checks from debug builds must stay out of production crash counts.
            if (BuildConfig.DEBUG) putBoolean("harness_test", true)
        }
        .build()

    // Stable exception types keep distinct system exit causes in separate Crashlytics groups.
    private fun exceptionFor(exit: SystemExit): Exception = when (exit.reason) {
        SystemExitPolicy.LOW_MEMORY -> LowMemoryExit()
        SystemExitPolicy.SIGNALED -> KillSignalExit()
        SystemExitPolicy.OTHER -> MemoryLimiterExit()
        SystemExitPolicy.INITIALIZATION_FAILURE -> InitializationFailureExit()
        SystemExitPolicy.EXCESSIVE_RESOURCE_USAGE -> ExcessiveResourceExit()
        SystemExitPolicy.FREEZER -> FreezerExit()
        else -> error("Unselected system exit reason")
    }

    private class LowMemoryExit : Exception("System terminated a perceptible process under memory pressure")
    private class KillSignalExit : Exception("System sent SIGKILL on a device without low-memory reporting")
    private class MemoryLimiterExit : Exception("System terminated a process at its memory limit")
    private class InitializationFailureExit : Exception("System terminated a process during initialization")
    private class ExcessiveResourceExit : Exception("System terminated a process for excessive resource usage")
    private class FreezerExit : Exception("System terminated a frozen process")
}
