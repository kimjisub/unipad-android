package com.kimjisub.launchpad.tool

import android.media.MediaPlayer
import java.io.File

/** Reads how long sound files play. Close it to free the player it holds. */
interface SoundDurationReader : AutoCloseable {
	/** The length in milliseconds, or null when the file cannot be read. */
	fun durationMs(file: File): Int?
}

class MediaPlayerDurationReader : SoundDurationReader {
	private val player = MediaPlayer()

	override fun durationMs(file: File): Int? = try {
		player.reset()
		player.setDataSource(file.path)
		player.prepare()
		player.duration.takeIf { it >= 0 }
	} catch (e: Exception) {
		Log.err("Could not read the length of ${file.name}", e)
		player.reset()
		null
	}

	override fun close() = player.release()
}
