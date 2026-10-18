package com.kimjisub.launchpad.tool

import android.content.ActivityNotFoundException
import androidx.activity.result.ActivityResultLauncher

/**
 * Launches like [ActivityResultLauncher.launch], but returns false instead of throwing when no app
 * on the device handles the request (some TV boxes and low-end devices ship without a file picker).
 */
fun <I> ActivityResultLauncher<I>.tryLaunch(input: I): Boolean =
	try {
		launch(input)
		true
	} catch (e: ActivityNotFoundException) {
		Log.err("no app handles this request", e)
		false
	}
