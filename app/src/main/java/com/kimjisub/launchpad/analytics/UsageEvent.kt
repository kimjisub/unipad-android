package com.kimjisub.launchpad.analytics

import com.kimjisub.launchpad.tool.UniPackDownloader
import com.kimjisub.launchpad.tool.UniPackImporter
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.UnknownHostException
import java.util.zip.ZipException
import javax.net.ssl.SSLException
import net.lingala.zip4j.exception.ZipException as Zip4jException

/** Existing cross-platform events plus the Android first-human-input event. */
object UsageEvent {
	const val PACK_IMPORT = "pack_import"
	const val PACK_LOAD = "pack_load"
	const val PLAY_START = "play_start"
	const val PLAY_FIRST_INPUT = "play_first_input"
	const val PLAY_END = "play_end"
}

/** The only parameter keys an event may carry. Every value comes from an enum below. */
object UsageParam {
	const val RESULT = "result"
	const val IMPORT_SOURCE = "import_source"
	const val ERROR_TYPE = "error_type"
	const val DURATION_BUCKET = "duration_bucket"
	const val TRIGGER = "trigger"
	const val ORIENTATION = "orientation"
	const val SCREEN_SHORT_SIDE = "screen_short_side"
	const val WINDOW_MODE = "window_mode"

	val allowed: Set<String> = setOf(
		RESULT, IMPORT_SOURCE, ERROR_TYPE, DURATION_BUCKET, TRIGGER,
		ORIENTATION, SCREEN_SHORT_SIDE, WINDOW_MODE,
	)
}

enum class UsageResult(val value: String) {
	SUCCESS("success"),
	FAILURE("failure"),
	CANCELLED("cancelled"),
}

/** Where a pack came from. iOS also has `open_in`; Android has no entry point that opens a file from outside the app. */
enum class PackImportSource(val value: String) {
	FILE("file"),
	STORE("store"),
	CODE("code"),
}

/**
 * Who made the first sound of a play session: the person, on the screen or a MIDI controller, or auto
 * play replaying the pack's sequence. Choosing a mode is neither; step practice and guide play only
 * show where to press. One `play_start` per visit stands for the earlier standard's per-press
 * `pad_press` ([PAD]) and `autoplay_start` ([AUTOPLAY]).
 */
enum class PlayTrigger(val value: String) {
	PAD("pad"),
	AUTOPLAY("autoplay"),
}

/** Which step of a pack import an error came from; the same I/O error means different things in each. */
enum class FailureStage {
	/** Looking up a share code or downloading a pack. */
	NETWORK,

	/** Reading, writing or unpacking files on the device. */
	FILE,
}

enum class UsageErrorType(val value: String) {
	INVALID_PACK("invalid_pack"),
	CORRUPT_ARCHIVE("corrupt_archive"),
	FILE_ACCESS("file_access"),
	STORAGE("storage"),
	NOT_FOUND("not_found"),
	SERVER("server"),
	NETWORK("network"),
	SOUND_ENGINE("sound_engine"),
	UNKNOWN("unknown");

	companion object {
		fun fromHttpStatus(status: Int): UsageErrorType = if (status == 404) NOT_FOUND else SERVER

		/**
		 * Maps a thrown value to a category; its message and type name never leave the device. [stage] is
		 * where the error surfaced: an I/O error is a network failure while fetching, and the same error
		 * is a file problem when it comes from a picked file or the device's storage.
		 */
		fun classify(error: Throwable, stage: FailureStage = FailureStage.FILE): UsageErrorType = when (error) {
			is UniPackImporter.UniPackCriticalErrorException,
			is UniPackDownloader.UniPackCriticalErrorException -> INVALID_PACK
			is UniPackDownloader.HttpStatusException -> fromHttpStatus(error.status)
			is ZipException, is Zip4jException -> classifyUnpackFailure(error)
			is UnknownHostException, is SocketException, is SSLException, is InterruptedIOException -> NETWORK
			is SecurityException -> FILE_ACCESS
			is IOException -> when {
				stage == FailureStage.NETWORK -> NETWORK
				error is FileNotFoundException -> FILE_ACCESS
				error.isOutOfSpace() -> STORAGE
				else -> FILE_ACCESS
			}
			else -> UNKNOWN
		}

		/** zip4j wraps every error of an unpack in its own exception; only an archive that is itself unreadable is corrupt. */
		private fun classifyUnpackFailure(error: Throwable): UsageErrorType {
			val causes = error.causes().toList()
			return when {
				causes.any { it.isNoSpaceMessage() } -> STORAGE
				causes.any { it is FileNotFoundException || it is SecurityException } -> FILE_ACCESS
				error.isZip4jFolderNotCreated() -> FILE_ACCESS
				else -> CORRUPT_ARCHIVE
			}
		}

		/**
		 * zip4j 2.11.6 reports a folder it could not create in the target only by these messages,
		 * with no cause, before any entry data is read. A full disk fails the same way and cannot be
		 * told apart here, so this is a write-target failure, not [STORAGE]. A zip4j upgrade that
		 * rewords them is caught by the read-only unpack test.
		 */
		private fun Throwable.isZip4jFolderNotCreated(): Boolean =
			this is Zip4jException && cause == null && type == Zip4jException.Type.UNKNOWN &&
				ZIP4J_FOLDER_NOT_CREATED.any { message?.startsWith(it) == true }

		private val ZIP4J_FOLDER_NOT_CREATED = listOf("Could not create directory: ", "Unable to create parent directories: ")

		private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }.take(MAX_CAUSE_DEPTH)

		private fun Throwable.isNoSpaceMessage(): Boolean = message?.contains(NO_SPACE_MESSAGE) == true

		private fun Throwable.isOutOfSpace(): Boolean = causes().any { it.isNoSpaceMessage() }

		private const val NO_SPACE_MESSAGE = "No space left on device"
		private const val MAX_CAUSE_DEPTH = 5
	}
}

/** Coarse elapsed-time buckets; the exact duration is never sent. Same bounds as iOS. */
object DurationBucket {
	private const val NANOS_PER_SECOND = 1_000_000_000L

	private val bounds = listOf(
		1L to "lt_1s",
		3L to "1s_3s",
		10L to "3s_10s",
		30L to "10s_30s",
		120L to "30s_2m",
		600L to "2m_10m",
		1800L to "10m_30m",
	)

	fun label(elapsedNanos: Long): String =
		bounds.firstOrNull { elapsedNanos < it.first * NANOS_PER_SECOND }?.second ?: "30m_plus"
}
