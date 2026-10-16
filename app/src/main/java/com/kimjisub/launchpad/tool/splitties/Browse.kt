package com.kimjisub.launchpad.tool.splitties

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.tool.Log

private const val PLAY_STORE_PACKAGE = "com.android.vending"

fun android.content.Context.browse(link: String, action: String = Intent.ACTION_VIEW) {
	if (!tryStart(Intent(action, link.toUri()))) {
		// Devices without a browser or YouTube app (Crashlytics d783e2d5)
		Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
	}
}

/** Opens [appId]'s Play page in the Play Store app, else the same page in a browser; false when neither exists. */
fun android.content.Context.openPlayStorePage(appId: String): Boolean {
	val page = "https://play.google.com/store/apps/details?id=$appId".toUri()
	return tryStart(Intent(Intent.ACTION_VIEW, page).setPackage(PLAY_STORE_PACKAGE)) ||
		tryStart(Intent(Intent.ACTION_VIEW, page))
}

/**
 * Opens the system's per-app language screen for this app on top of [this] activity, so picking a
 * language or going back returns here rather than to whatever the Settings app last showed. Some
 * manufacturers remove or lock that screen; then nothing opens and the app carries on.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Activity.openAppLanguageSettings() {
	tryStart(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, "package:$packageName".toUri()), newTask = false)
}

private fun android.content.Context.tryStart(intent: Intent, newTask: Boolean = true): Boolean {
	if (newTask) intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
	return try {
		startActivity(intent)
		true
	} catch (e: ActivityNotFoundException) {
		Log.err("no activity for ${intent.data}", e)
		false
	} catch (e: SecurityException) {
		Log.err("activity for ${intent.data} not allowed", e)
		false
	}
}
