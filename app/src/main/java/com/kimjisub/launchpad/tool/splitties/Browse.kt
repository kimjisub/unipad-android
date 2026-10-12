package com.kimjisub.launchpad.tool.splitties

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.widget.Toast
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

private fun android.content.Context.tryStart(intent: Intent): Boolean {
	intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
	return try {
		startActivity(intent)
		true
	} catch (e: ActivityNotFoundException) {
		Log.err("no activity for ${intent.data}", e)
		false
	}
}
