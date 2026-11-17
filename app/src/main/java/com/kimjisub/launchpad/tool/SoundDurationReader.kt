package com.kimjisub.launchpad.tool

import android.media.MediaFormat
import com.kimjisub.launchpad.audio.openAudioPacketSource
import java.io.File

/** Reads how long sound files play. Each read owns and closes its audio source. */
interface SoundDurationReader : AutoCloseable {
	/** The length in milliseconds, or null when the file cannot be read. */
	fun durationMs(file: File): Double?
}

class AudioTrackDurationReader : SoundDurationReader {
	override fun durationMs(file: File): Double? = try {
		openAudioPacketSource(file)?.use { source ->
			val format = source.format
			if (format.containsKey(MediaFormat.KEY_DURATION)) {
				format.getLong(MediaFormat.KEY_DURATION).takeIf { it >= 0 }?.div(1000.0)
			} else null
		}
	} catch (e: Exception) {
		Log.err("Could not read the length of ${file.name}", e)
		null
	}

	// No player or other state survives a read, including a failed one.
	override fun close() = Unit
}
