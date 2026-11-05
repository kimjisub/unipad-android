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
	const val ANR = 6
	const val INITIALIZATION_FAILURE = 7
	const val EXCESSIVE_RESOURCE_USAGE = 9
	const val OTHER = 13
	const val FREEZER = 14
	private const val SIGKILL = 9
	private const val IMPORTANCE_VISIBLE = 200
	private const val IMPORTANCE_PERCEPTIBLE = 230
	private const val MAX_REPORTS = 5
	private const val MAX_DESCRIPTION = 1024
	// An intent's data can name a file or a link the user opened; only its scheme helps explain a timeout.
	// Intent.toString() follows the data with another " name=" field, "(has ...)" or the closing brace.
	private val intentData = Regex("""\bdat=([A-Za-z][A-Za-z0-9+.-]*:)?.*?(?=\s+\w+=|\s*\(has |\s*}|$)""")

	fun shouldReport(exit: SystemExit, lmkSupported: Boolean): Boolean = when (exit.reason) {
		LOW_MEMORY -> exit.importance <= IMPORTANCE_PERCEPTIBLE
		SIGNALED -> !lmkSupported && exit.status == SIGKILL &&
			exit.importance <= IMPORTANCE_PERCEPTIBLE
		OTHER -> exit.description?.contains("MemoryLimiter") == true
		ANR, INITIALIZATION_FAILURE, EXCESSIVE_RESOURCE_USAGE, FREEZER -> true
		else -> false
	}

	/** The system's own words for the exit, such as what an ANR waited for, without intent data. */
	fun description(exit: SystemExit): String = exit.description.orEmpty()
		.replace(intentData) { "dat=${it.groupValues[1]}<redacted>" }
		.take(MAX_DESCRIPTION)

	/**
	 * Only the process that left the [record]ed screen can be described by it: a later process started
	 * without a screen, such as for an incoming message, has none. Neither can an app killed while on screen;
	 * a perceptible app is no longer on screen, so it keeps the screen it left.
	 */
	fun backgroundScreenAt(exit: SystemExit, record: BackgroundScreen?): BackgroundScreen? =
		record?.takeIf { exit.pid == it.pid && exit.importance > IMPORTANCE_VISIBLE && exit.timestamp >= it.since }

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
