package com.kimjisub.launchpad.tool.splitties

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.core.net.toUri

fun android.content.Context.browse(link: String) {
	val myIntent = Intent(Intent.ACTION_VIEW, link.toUri())
	myIntent.addFlags(FLAG_ACTIVITY_NEW_TASK)
	startActivity(myIntent)
}
