package com.kimjisub.launchpad.audio

import android.media.AudioManager

/**
 * Decides what playback does when audio focus changes (incoming call, another app taking output).
 *
 * A transient loss silences the pack and pauses autoplay, and the matching gain resumes autoplay.
 * A permanent loss silences and pauses too, but nothing resumes by itself: Android expects the user
 * to start playback again. Ducking is left to the system, which lowers the volume for us.
 */
class AudioFocusPolicy(private val target: Target) {

	interface Target {
		val isAutoPlayPlaying: Boolean
		fun silence()
		fun pauseAutoPlay()
		fun resumeAutoPlay()
	}

	private var resumeAutoPlayOnGain = false

	fun onFocusChange(focusChange: Int) {
		when (focusChange) {
			AudioManager.AUDIOFOCUS_GAIN -> {
				if (resumeAutoPlayOnGain) target.resumeAutoPlay()
				resumeAutoPlayOnGain = false
			}

			AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
				// A second transient loss arrives while already paused; keep the first decision.
				resumeAutoPlayOnGain = resumeAutoPlayOnGain || target.isAutoPlayPlaying
				loseFocus()
			}

			AudioManager.AUDIOFOCUS_LOSS -> {
				resumeAutoPlayOnGain = false
				loseFocus()
			}
		}
	}

	private fun loseFocus() {
		if (target.isAutoPlayPlaying) target.pauseAutoPlay()
		target.silence()
	}
}
