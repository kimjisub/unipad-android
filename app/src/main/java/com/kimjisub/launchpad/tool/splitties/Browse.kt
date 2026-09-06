package com.kimjisub.launchpad.tool.splitties

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.widget.Toast
import androidx.core.net.toUri
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.tool.Log

fun android.content.Context.browse(link: String) {
	val myIntent = Intent(Intent.ACTION_VIEW, link.toUri())
	myIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
	try {
		startActivity(myIntent)
	} catch (e: ActivityNotFoundException) {
		// Devices without a browser or YouTube app (Crashlytics d783e2d5)
		Log.err("no activity for $link", e)
		Toast.makeText(this, R.string.no_app_for_link, Toast.LENGTH_SHORT).show()
	}
}
