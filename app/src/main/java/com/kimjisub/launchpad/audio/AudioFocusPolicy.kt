package com.kimjisub.launchpad.audio

import android.media.AudioManager

/** Every output loss suspends playback. Permission returning never starts a song. */
class AudioFocusPolicy(private val target: Target) {
	interface Target {
		fun pauseAutoPlay()
		fun silence()
	}

	var hasFocus = true
		private set

	fun onFocusChange(focusChange: Int) {
		when (focusChange) {
			AudioManager.AUDIOFOCUS_GAIN -> hasFocus = true
			AudioManager.AUDIOFOCUS_LOSS,
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
				hasFocus = false
				target.pauseAutoPlay()
				target.silence()
			}
		}
	}
}
