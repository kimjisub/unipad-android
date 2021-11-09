package com.kimjisub.launchpad.tool.splitties

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.net.Uri

fun android.content.Context.browse(link: String) {
	val myIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
	myIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
	startActivity(myIntent)
}
