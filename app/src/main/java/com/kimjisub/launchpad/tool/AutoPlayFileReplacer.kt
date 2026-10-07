package com.kimjisub.launchpad.tool

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Replaces a pack's autoPlay file so that it is never missing or half written: the new content is
 * written in full to a file beside it and renamed over it, and the old content is kept as
 * `autoPlay_<time>`. When the replacement fails the folder is left as it was.
 */
class AutoPlayFileReplacer(
	private val now: () -> Date = ::Date,
	private val write: (File, String) -> Unit = ::writeToDisk,
	private val copy: (File, File) -> Unit = { from, to -> from.copyTo(to) },
) {
	fun replace(autoPlayFile: File, content: String) {
		val folder = autoPlayFile.absoluteFile.parentFile
			?: throw IOException("${autoPlayFile.path} has no folder")
		val temp = File(folder, autoPlayFile.name + TEMP_SUFFIX)
		// The name is free, so a backup found here after a failure is this run's own, possibly partial.
		val backup = freeBackupFile(folder)
		try {
			write(temp, content)
			copy(autoPlayFile, backup)
			// rename(2) swaps the file in one step, replacing the old one.
			if (!temp.renameTo(autoPlayFile)) throw IOException("Could not replace ${autoPlayFile.path}")
		} catch (e: Exception) {
			temp.delete()
			backup.delete()
			throw e
		}
	}

	/** `autoPlay_<time>`, or `autoPlay_<time>-2`, `-3`… when mappings finish within the same second. */
	private fun freeBackupFile(folder: File): File {
		val base = BACKUP_PREFIX + SimpleDateFormat(BACKUP_TIME_FORMAT, Locale.US).format(now())
		return generateSequence(1) { it + 1 }
			.map { File(folder, if (it == 1) base else "$base-$it") }
			.first { !it.exists() }
	}

	companion object {
		const val TEMP_SUFFIX = ".tmp"
		private const val BACKUP_PREFIX = "autoPlay_"
		private const val BACKUP_TIME_FORMAT = "yyyy_MM_dd-HH_mm_ss"

		/** Writes and flushes to the storage, so a power loss after the rename cannot leave it empty. */
		private fun writeToDisk(file: File, content: String) {
			FileOutputStream(file).use {
				it.write(content.toByteArray())
				it.fd.sync()
			}
		}
	}
}
