package com.kimjisub.launchpad.manager

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object Functions {
	fun putClipboard(activity: Activity, text: String) {
		val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
		val clip: ClipData = ClipData.newPlainText("UniPad", text)
		clipboard.setPrimaryClip(clip)
	}
}