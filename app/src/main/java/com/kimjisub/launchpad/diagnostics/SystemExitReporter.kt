package com.kimjisub.launchpad.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.Keep
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.google.firebase.crashlytics.CustomKeysAndValues
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kimjisub.launchpad.BuildConfig

@RequiresApi(Build.VERSION_CODES.R)
internal object SystemExitReporter {
	const val PREFS = "system_exit_diagnostics"
	private const val CHECKPOINT = "last_processed_timestamp"
	private const val TAG = "SystemExitReporter"

	/** Must run on an IO dispatcher: the system history, native crash records and checkpoint use disk. */
	fun collect(context: Context, sdkInt: Int = Build.VERSION.SDK_INT) {
		val now = System.currentTimeMillis()
		val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		val lastProcessed = if (preferences.contains(CHECKPOINT))
			preferences.getLong(CHECKPOINT, 0) else null
		val manager = context.getSystemService(ActivityManager::class.java) ?: return
		val lmkSupported = ActivityManager.isLowMemoryKillReportSupported()
		val records = manager.getHistoricalProcessExitReasons(context.packageName, 0, 0)
		val history = records.map {
			SystemExit(it.reason, it.status, it.description, it.importance, it.timestamp, it.pss, it.rss, it.pid)
		}
		val batch = SystemExitPolicy.select(history, lastProcessed, now, lmkSupported)
		val background = BackgroundScreenRecorder.read(preferences)
		for (exit in batch.reports) {
			val nativeTrace = if (exit.reason == SystemExitPolicy.CRASH_NATIVE)
				nativeTrace(records[history.indexOfFirst { it === exit }], sdkInt) else null
			val keys = keysFor(exit, lmkSupported, SystemExitPolicy.backgroundScreenAt(exit, background), nativeTrace)
			FirebaseCrashlytics.getInstance().recordException(exceptionFor(exit), keys)
			Log.d(TAG, "Recorded system exit reason=${exit.reason} timestamp=${exit.timestamp}" +
				(nativeTrace?.let { " native=$it" } ?: ""))
		}
		preferences.edit(commit = true) {
			putLong(CHECKPOINT, batch.checkpoint)
		}
	}

	/** Android 12 and later attach the native crash's tombstone; reading it must never stop the report. */
	private fun nativeTrace(record: ApplicationExitInfo, sdkInt: Int): NativeCrashTrace {
		if (sdkInt < Build.VERSION_CODES.S) return NativeCrashTrace(NativeCrashTrace.NONE)
		return try {
			record.traceInputStream.use { TombstoneReader.summarize(it) }
		} catch (e: Exception) {
			NativeCrashTrace(NativeCrashTrace.UNREADABLE)
		}
	}

	private fun keysFor(
		exit: SystemExit,
		lmkSupported: Boolean,
		background: BackgroundScreen?,
		nativeTrace: NativeCrashTrace?,
	) = CustomKeysAndValues.Builder()
		.putInt("exit_reason", exit.reason)
		.putString("exit_description", SystemExitPolicy.description(exit))
		.putInt("exit_status", exit.status)
		.putInt("exit_importance", exit.importance)
		.putLong("exit_pss_kb", exit.pssKb)
		.putLong("exit_rss_kb", exit.rssKb)
		.putLong("exit_timestamp", exit.timestamp)
		.putBoolean("lmk_report_supported", lmkSupported)
		.apply {
			// Explicit server checks from debug builds must stay out of production crash counts.
			if (BuildConfig.DEBUG) putBoolean("harness_test", true)
			background?.let {
				putString("exit_last_screen", it.screen)
				putBoolean("exit_pack_open", it.packOpen)
				putLong("exit_background_ms", exit.timestamp - it.since)
			}
			nativeTrace?.let {
				putString("native_trace", it.status)
				putString("native_signal", it.signal)
				putString("native_frames", it.frames)
				putString("native_build_ids", it.buildIds)
			}
		}
		.build()

	// Keep names and prevent R8 class merging so system exit causes stay in separate groups.
	private fun exceptionFor(exit: SystemExit): Exception = when (exit.reason) {
		SystemExitPolicy.LOW_MEMORY -> LowMemoryExit()
		SystemExitPolicy.ANR -> AnrReasonExit()
		SystemExitPolicy.CRASH_NATIVE -> NativeCrashExit()
		SystemExitPolicy.SIGNALED -> KillSignalExit()
		SystemExitPolicy.OTHER -> MemoryLimiterExit()
		SystemExitPolicy.INITIALIZATION_FAILURE -> InitializationFailureExit()
		SystemExitPolicy.EXCESSIVE_RESOURCE_USAGE -> ExcessiveResourceExit()
		SystemExitPolicy.FREEZER -> FreezerExit()
		else -> error("Unselected system exit reason")
	}

	@Keep
	private class LowMemoryExit : Exception("System terminated a perceptible process under memory pressure")
	/**
	 * Crashlytics already reports the same ANR with its threads; count ANRs there. This group only carries
	 * the system's reason in `exit_description` (Android 14 and later; Android 12 keeps only "bg anr" or
	 * "user request after error"), which that report lacks.
	 */
	@Keep
	private class AnrReasonExit : Exception("Reason the system gave for an ANR (count ANRs from Crashlytics ANR reports, not this)")
	/**
	 * Crashlytics already reports the same crash with its own native report; count native crashes there. This
	 * group only carries the crashing thread's frames as the system named them on the device (`native_frames`),
	 * which that report lacks when it cannot name a system library's functions.
	 */
	@Keep
	private class NativeCrashExit : Exception(
		"Where the system saw a native crash (count native crashes from Crashlytics native reports, not this)"
	)
	@Keep
	private class KillSignalExit : Exception("System sent SIGKILL on a device without low-memory reporting")
	@Keep
	private class MemoryLimiterExit : Exception("System terminated a process at its memory limit")
	@Keep
	private class InitializationFailureExit : Exception("System terminated a process during initialization")
	@Keep
	private class ExcessiveResourceExit : Exception("System terminated a process for excessive resource usage")
	@Keep
	private class FreezerExit : Exception("System terminated a frozen process")
}
