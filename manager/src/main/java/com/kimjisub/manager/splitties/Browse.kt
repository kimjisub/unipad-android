package com.kimjisub.manager.splitties

import android.content.Intent
import android.net.Uri

fun android.content.Context.browse(link: String) {
	val myIntent = Intent(Intent.ACTION_VIEW, Uri.parse(link))
	startActivity(myIntent)
}
