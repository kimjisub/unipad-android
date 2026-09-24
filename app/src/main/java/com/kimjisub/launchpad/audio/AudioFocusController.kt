package com.kimjisub.launchpad.audio

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.kimjisub.launchpad.tool.Log

/**
 * Requests and abandons audio focus for the play screen and forwards focus changes to
 * [onFocusChange] on the main thread. The attributes match the Oboe stream (Game / Sonification).
 */
class AudioFocusController(
	private val audioManager: AudioManager,
	private val onFocusChange: (Int) -> Unit,
) {

	private val listener = AudioManager.OnAudioFocusChangeListener { onFocusChange(it) }

	@delegate:RequiresApi(Build.VERSION_CODES.O)
	private val focusRequest: AudioFocusRequest by lazy {
		AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
			.setAudioAttributes(
				AudioAttributes.Builder()
					.setUsage(AudioAttributes.USAGE_GAME)
					.setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
					.build()
			)
			.setOnAudioFocusChangeListener(listener, Handler(Looper.getMainLooper()))
			.build()
	}

	/** Returns true when focus was granted; a gain granted here is also reported to [onFocusChange]. */
	fun request(): Boolean {
		val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			audioManager.requestAudioFocus(focusRequest)
		} else {
			@Suppress("DEPRECATION")
			audioManager.requestAudioFocus(listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
		}
		val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
		if (granted) onFocusChange(AudioManager.AUDIOFOCUS_GAIN) else Log.err("audio focus request denied: $result")
		return granted
	}

	fun abandon() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			audioManager.abandonAudioFocusRequest(focusRequest)
		} else {
			@Suppress("DEPRECATION")
			audioManager.abandonAudioFocus(listener)
		}
	}
}
