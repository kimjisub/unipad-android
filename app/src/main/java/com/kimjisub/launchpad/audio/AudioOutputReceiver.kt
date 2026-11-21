package com.kimjisub.launchpad.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.core.content.ContextCompat

/** Android reports an impending speaker fallback, rather than merely a change of connected devices. */
class AudioOutputReceiver(private val interrupt: () -> Unit) : BroadcastReceiver() {
	private var registered = false

	fun start(context: Context) {
		if (registered) return
		ContextCompat.registerReceiver(
			context, this, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
			ContextCompat.RECEIVER_NOT_EXPORTED,
		)
		registered = true
	}

	fun stop(context: Context) {
		if (!registered) return
		registered = false
		context.unregisterReceiver(this)
	}

	override fun onReceive(context: Context, intent: Intent) {
		// A queued delivery after the play screen stopped must not affect another screen.
		if (registered && intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) interrupt()
	}
}
