package com.kimjisub.launchpad

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.StrictMode
import com.google.android.gms.oss.licenses.v2.OssLicensesMenuActivity
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.kimjisub.launchpad.di.appModule
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.NotificationManager
import com.kimjisub.launchpad.manager.PackStaging
import com.kimjisub.launchpad.manager.WorkspaceManager
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.ui.theme.UniPadColorScheme
import com.kimjisub.launchpad.ui.theme.UniPadTypography
import com.orhanobut.logger.AndroidLogAdapter
import com.orhanobut.logger.Logger
import com.orhanobut.logger.PrettyFormatStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.GlobalContext.startKoin
import java.io.File
import kotlin.concurrent.thread

class BaseApplication : Application() {
	companion object {
		private const val DEBUG_REMOTE_CONFIG_FETCH_INTERVAL_SECONDS = 60L
		private const val PREF_NAME = "data"
		private const val KEY_SELECTED_THEME = "SelectedTheme"
		private const val CRASH_KEY_INSTALL_SPLITS = "install_splits"
	}

	private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	override fun onCreate() {
		super.onCreate()

		setupStrictMode()
		recordInstallSplits()
		setupNotification()
		setupLogger()
		setupRemoteConfig()
		setupBundledThemes()
		setupOssLicensesTheme()

		startKoin {
			androidContext(applicationContext)
			modules(appModule)
		}
		clearPackStagingLeftovers()
	}

	/**
	 * Removes what an install or a move was building when the app was last killed. Runs once per
	 * process, before any screen can start a new one; see [PackStaging].
	 */
	private fun clearPackStagingLeftovers() {
		try {
			val workspaces = GlobalContext.get().get<WorkspaceManager>().availableWorkspaces
			val leftovers = workspaces.flatMap { PackStaging.setAsideLeftovers(it.file) }
			if (leftovers.isEmpty()) return
			Log.log("Clearing ${leftovers.size} unfinished pack folders")
			thread(name = "PackStagingCleanup") { leftovers.forEach(FileManager::deleteDirectory) }
		} catch (e: Exception) {
			Log.err("Pack staging cleanup failed", e)
		}
	}

	/**
	 * Records which Play split APKs are installed, so a resource crash can be matched to an
	 * install that lacks a split (e.g. base APK only, without the screen-density split).
	 * Before API 26 only the split file names (`split_config.xxhdpi.apk`) are available.
	 */
	private fun recordInstallSplits() {
		val names = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) applicationInfo.splitNames
		else applicationInfo.splitSourceDirs?.map { File(it).name }?.toTypedArray()
		val splits = names?.joinToString(",") ?: "none"
		FirebaseCrashlytics.getInstance().setCustomKey(CRASH_KEY_INSTALL_SPLITS, splits)
		Log.log("Install splits: $splits")
	}

	// Debug builds log file access on the main thread (`adb logcat -s StrictMode`), where a slow
	// storage turns it into a frozen screen or an ANR. Logging only: platform and library reads
	// are reported too, so stopping the app would break every test run.
	private fun setupStrictMode() {
		if (!BuildConfig.DEBUG) return
		StrictMode.setThreadPolicy(
			StrictMode.ThreadPolicy.Builder()
				.detectDiskReads()
				.detectDiskWrites()
				.penaltyLog()
				.build()
		)
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

	/**
	 * The license screen follows the system light/dark setting unless told otherwise; UniPad is
	 * always dark. Set here rather than when the screen is opened, because a process restored
	 * onto the license screen never passes through the screen that opened it.
	 */
	private fun setupOssLicensesTheme() {
		OssLicensesMenuActivity.setTheme(UniPadColorScheme, UniPadColorScheme, UniPadTypography)
	}

	private fun setupBundledThemes() {
		migrateBundledThemePreference()
		// Deleting whole theme folders on the first launch after an update froze startup (ANR).
		ioScope.launch { cleanupLegacyExtractedThemes() }
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