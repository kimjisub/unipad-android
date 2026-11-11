package com.kimjisub.launchpad.tool

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger

/**
 * Saves the current autoplay and its single previous version as one recoverable transaction.
 * Until the journal is committed, reopening rolls both files back. All originals and replacement
 * files are flushed before either public file changes. Call only from a background dispatcher.
 */
class AutoPlayFileReplacer(
	private val write: (File, String) -> Unit = ::writeToDisk,
	private val copy: (File, File) -> Unit = ::copyToDisk,
	private val move: (File, File) -> Unit = ::rename,
) {
	fun replace(autoPlayFile: File, content: String, validate: () -> Unit = {}) = synchronized(lock) {
		recover(autoPlayFile)
		transact(autoPlayFile, { next -> write(next, content) }, validate)
	}

	fun restore(autoPlayFile: File) = restore(autoPlayFile) {}

	fun restore(autoPlayFile: File, validate: () -> Unit) = synchronized(lock) {
		recover(autoPlayFile)
		val backup = restoreSource(autoPlayFile) ?: throw IOException("No previous autoplay")
		transact(autoPlayFile, { next -> copy(backup, next) }, validate)
	}

	fun hasBackup(autoPlayFile: File): Boolean = synchronized(lock) {
		recover(autoPlayFile)
		restoreSource(autoPlayFile) != null
	}

	private fun restoreSource(autoPlay: File): File? {
		val previous = backupFile(autoPlay)
		if (previous.exists()) return previous.takeIf { it.isFile }
		// Older releases used this fixed prefix even when the pack used another file casing.
		val prefix = "autoPlay_"
		return autoPlay.absoluteFile.parentFile?.listFiles().orEmpty().asSequence()
			.filter { it.name.startsWith(prefix) && it.isFile && it.length() > 0 }
			.mapNotNull { file ->
				val match = legacyBackupName.matchEntire(file.name.removePrefix(prefix))
					?: return@mapNotNull null
				val timestamp = (1..6).joinToString("") { decimalDigits(match.groupValues[it]) }.toLong()
				val sequence = decimalDigits(match.groupValues[7].ifEmpty { "1" }).toBigInteger()
				LegacyBackup(file, timestamp, sequence)
			}
			// Renaming in older releases preserved the original modification time. Only the
			// parsed name orders mappings correctly, including names using different digit sets.
			.minWithOrNull(compareBy<LegacyBackup> { it.timestamp }.thenBy { it.sequence }.thenBy { it.file.name })
			?.file
	}

	private data class LegacyBackup(val file: File, val timestamp: Long, val sequence: BigInteger)

	/** Must run before parsing a pack after an interrupted save or restore. Safe to repeat. */
	fun recover(autoPlayFile: File) = synchronized(lock) {
		val journal = sibling(autoPlayFile, JOURNAL)
		if (journal.exists()) rollback(autoPlayFile, journal)
		clean(sibling(autoPlayFile, PREPARING))
		clean(sibling(autoPlayFile, COMMITTED))
	}

	private fun transact(autoPlay: File, newContent: (File) -> Unit, validate: () -> Unit) {
		val preparing = sibling(autoPlay, PREPARING)
		val journal = sibling(autoPlay, JOURNAL)
		val committed = sibling(autoPlay, COMMITTED)
		val backup = backupFile(autoPlay)
		if (!preparing.mkdir()) throw IOException("Cannot prepare autoplay save")
		try {
			write(File(preparing, "version"), "1")
			copy(autoPlay, File(preparing, "original"))
			if (backup.exists()) {
				copy(backup, File(preparing, "previous"))
				write(File(preparing, "had-previous"), "1")
			}
			newContent(File(preparing, "next"))
			copy(autoPlay, File(preparing, "next-previous"))
			move(preparing, journal)
			move(File(journal, "next-previous"), backup)
			move(File(journal, "next"), autoPlay)
			// Re-read the saved file before committing. A failure also restores the old backup.
			validate()
			move(journal, committed)
		} catch (e: Exception) {
			try {
				if (journal.exists()) rollback(autoPlay, journal)
				clean(preparing)
			} catch (recovery: Exception) {
				e.addSuppressed(recovery) // Keep the journal for recovery on the next open.
			}
			throw e
		}
		// Once committed, cleanup failure must not turn a successful save into a failure.
		runCatching { committed.deleteRecursively() }
	}

	private fun rollback(autoPlay: File, journal: File) {
		checkJournal(journal)
		val backup = backupFile(autoPlay)
		if (File(journal, "had-previous").exists()) {
			val previous = File(journal, "previous")
			if (previous.exists()) rename(previous, backup)
		} else if (backup.exists() && !backup.delete()) {
			throw IOException("Cannot recover previous autoplay")
		}
		val original = File(journal, "original")
		// Rename consumes the rollback file. A restart midway through recovery skips files already
		// restored and resumes the remaining renames, without allocating more disk space.
		if (original.exists()) rename(original, autoPlay)
		rename(journal, sibling(autoPlay, COMMITTED))
		clean(sibling(autoPlay, COMMITTED))
	}

	private fun clean(folder: File) {
		if (!folder.exists()) return
		if (!folder.isDirectory) throw IOException("Invalid autoplay staging directory")
		if (!folder.deleteRecursively()) throw IOException("Cannot clear autoplay transaction")
	}

	private fun checkJournal(folder: File) {
		if (!folder.isDirectory || File(folder, "version").readText() != "1") {
			throw IOException("Invalid autoplay transaction")
		}
	}

	companion object {
		private val lock = Any()
		private val legacyBackupName = Regex(
			"""(\p{Nd}{4})_(\p{Nd}{2})_(\p{Nd}{2})-(\p{Nd}{2})_(\p{Nd}{2})_(\p{Nd}{2})(?:-(\p{Nd}+))?""",
		)

		private fun decimalDigits(value: String) = value.codePoints().toArray()
			.joinToString("") { Character.digit(it, 10).toString() }
		const val TEMP_SUFFIX = ".tmp"
		private const val PREPARING = ".preparing"
		private const val JOURNAL = ".transaction"
		private const val COMMITTED = ".committed"

		private fun sibling(file: File, suffix: String) = File(file.absoluteFile.parentFile, ".${file.name}$suffix")
		private fun backupFile(file: File) = File(file.absoluteFile.parentFile, file.name + "_previous")

		private fun rename(from: File, to: File) {
			if (!from.renameTo(to)) throw IOException("Cannot replace ${to.name}")
		}

		private fun writeToDisk(file: File, content: String) {
			FileOutputStream(file).use {
				it.write(content.toByteArray(Charsets.UTF_8))
				it.fd.sync()
			}
		}

		internal fun copyToDisk(from: File, to: File) {
			val partial = File(to.path + TEMP_SUFFIX)
			try {
				from.inputStream().use { input ->
					FileOutputStream(partial).use {
						input.copyTo(it)
						it.fd.sync()
					}
				}
				rename(partial, to)
			} catch (e: Exception) {
				partial.delete()
				throw e
			}
		}
	}
}
