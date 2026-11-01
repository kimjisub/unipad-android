package com.kimjisub.launchpad.diagnostics

internal data class SystemExit(
	val reason: Int,
	val status: Int = 0,
	val description: String? = null,
	val importance: Int = 100,
	val timestamp: Long = 0,
	val pssKb: Long = 0,
	val rssKb: Long = 0,
	val pid: Int = 0,
)

internal data class SystemExitBatch(val reports: List<SystemExit>, val checkpoint: Long)

/** The screen the app left when it last went to the background; see [BackgroundScreenRecorder]. */
internal data class BackgroundScreen(val screen: String, val packOpen: Boolean, val since: Long, val pid: Int)

internal object SystemExitPolicy {
	// ApplicationExitInfo values, kept independent of Android for JVM tests.
	const val SIGNALED = 2
	const val LOW_MEMORY = 3
	const val INITIALIZATION_FAILURE = 7
	const val EXCESSIVE_RESOURCE_USAGE = 9
	const val OTHER = 13
	const val FREEZER = 14
	private const val SIGKILL = 9
	private const val IMPORTANCE_PERCEPTIBLE = 230
	private const val MAX_REPORTS = 5

	fun shouldReport(exit: SystemExit, lmkSupported: Boolean): Boolean = when (exit.reason) {
		LOW_MEMORY -> exit.importance <= IMPORTANCE_PERCEPTIBLE
		SIGNALED -> !lmkSupported && exit.status == SIGKILL &&
			exit.importance <= IMPORTANCE_PERCEPTIBLE
		OTHER -> exit.description?.contains("MemoryLimiter") == true
		INITIALIZATION_FAILURE, EXCESSIVE_RESOURCE_USAGE, FREEZER -> true
		else -> false
	}

	/**
	 * Only the process that left the [record]ed screen can be described by it: a later process started
	 * without a screen, such as for an incoming message, has none. Neither can an app killed in front.
	 */
	fun backgroundScreenAt(exit: SystemExit, record: BackgroundScreen?): BackgroundScreen? =
		record?.takeIf { exit.pid == it.pid && exit.importance > IMPORTANCE_PERCEPTIBLE && exit.timestamp >= it.since }

	fun select(history: List<SystemExit>, lastProcessed: Long?, now: Long, lmkSupported: Boolean): SystemExitBatch {
		// Establish a baseline on upgrade rather than reporting old exits retroactively.
		if (lastProcessed == null) return SystemExitBatch(emptyList(), now)
		val newer = history.filter { it.timestamp > lastProcessed }
		return SystemExitBatch(
			newer.filter { shouldReport(it, lmkSupported) }
				.sortedByDescending { it.timestamp }.take(MAX_REPORTS),
			newer.maxOfOrNull { it.timestamp } ?: lastProcessed,
		)
	}
}
