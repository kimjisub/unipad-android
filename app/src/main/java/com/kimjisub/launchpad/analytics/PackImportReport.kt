package com.kimjisub.launchpad.analytics

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The outcome of one pack import attempt (a picked file, a store download or a share code). The
 * importer, the downloader and the screen that started them may all learn the ending; only the first
 * report is sent, so a success followed by a cancelled scope, or a cancel followed by a failure, is
 * not counted twice.
 */
class PackImportReport internal constructor(
	private val source: PackImportSource,
	private val log: (String, Map<String, String>) -> Unit,
) {
	private val reported = AtomicBoolean(false)

	/** The pack is installed in the workspace and its files were read. */
	fun succeeded() = report(UsageResult.SUCCESS)

	fun cancelled() = report(UsageResult.CANCELLED)

	fun failed(errorType: UsageErrorType) = report(UsageResult.FAILURE, errorType)

	/** A cancelled coroutine is a cancellation, not a failure. */
	fun failed(error: Throwable, stage: FailureStage = FailureStage.FILE) {
		if (error is CancellationException) cancelled() else failed(UsageErrorType.classify(error, stage))
	}

	private fun report(result: UsageResult, errorType: UsageErrorType? = null) {
		if (!reported.compareAndSet(false, true)) return
		val parameters = mutableMapOf(
			UsageParam.RESULT to result.value,
			UsageParam.IMPORT_SOURCE to source.value,
		)
		errorType?.let { parameters[UsageParam.ERROR_TYPE] = it.value }
		log(UsageEvent.PACK_IMPORT, parameters)
	}
}
