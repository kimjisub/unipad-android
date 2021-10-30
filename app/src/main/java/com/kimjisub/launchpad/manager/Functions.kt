package com.kimjisub.launchpad.manager

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.fragment.app.Fragment

fun Context.putClipboard(text: String) {
	val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
	val clip: ClipData = ClipData.newPlainText("UniPad", text)
	clipboard.setPrimaryClip(clip)
}

fun Activity.putClipboard(text: String) {
	baseContext.putClipboard(text)
}

fun Fragment.putClipboard(text: String) {
	requireContext().putClipboard(text)
}
