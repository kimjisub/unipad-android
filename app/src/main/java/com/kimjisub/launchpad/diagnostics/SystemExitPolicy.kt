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
	private const val INTENT_START = "Intent {"
	// An intent prints its fields in a fixed order (act, cat, dat, typ, id, flg, pkg, cmp, ...). Its data, type and
	// identifier are free text that can name a file or a link the user opened, and may contain anything that looks
	// like a later field, so only the fields before the first free-text one are kept, and only while their
	// characters are limited to names. Android's regex engine rejects a bare brace or bracket, so they are escaped.
	private val sensitiveStart = Regex("""Intent \{|\bdat=""")
	private val nameField =
		Regex("""(act|pkg)=[A-Za-z0-9_.]+|cat=\[[A-Za-z0-9_.,]+\]|flg=0x[0-9a-fA-F]+|cmp=[A-Za-z0-9_.]+/[A-Za-z0-9_.${'$'}]+""")

	fun shouldReport(exit: SystemExit, lmkSupported: Boolean): Boolean = when (exit.reason) {
		LOW_MEMORY -> exit.importance <= IMPORTANCE_PERCEPTIBLE
		SIGNALED -> !lmkSupported && exit.status == SIGKILL &&
			exit.importance <= IMPORTANCE_PERCEPTIBLE
		OTHER -> exit.description?.contains("MemoryLimiter") == true
		ANR, INITIALIZATION_FAILURE, EXCESSIVE_RESOURCE_USAGE, FREEZER -> true
		else -> false
	}

	/**
	 * The system's own words for the exit without intent data. For an ANR they name what the system waited
	 * for, after how it ended the process: "bg anr: Broadcast of Intent { ... }",
	 * "user request after error: Input dispatching timed out (...)".
	 */
	fun description(exit: SystemExit): String = redact(exit.description.orEmpty())

	private fun redact(text: String): String {
		val start = sensitiveStart.find(text)?.range?.first ?: return text.take(MAX_DESCRIPTION)
		val redacted = if (text.startsWith(INTENT_START, start)) {
			val fields = text.substring(start + INTENT_START.length).trimStart(' ').split(' ')
			val kept = fields.takeWhile { nameField.matches(it) }
			val shown = if (fields.drop(kept.size) == listOf("}")) kept else kept + "<redacted>"
			(listOf(INTENT_START) + shown + "}").joinToString(" ")
		} else {
			"dat=<redacted>"
		}
		return (text.substring(0, start) + redacted).take(MAX_DESCRIPTION)
	}

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
		// ANRs have their own allowance so that a run of them cannot crowd out memory exits, and vice versa.
		val reports = newer.filter { shouldReport(it, lmkSupported) }.partition { it.reason == ANR }.toList()
			.flatMap { group -> group.sortedByDescending { it.timestamp }.take(MAX_REPORTS) }
		return SystemExitBatch(
			reports.sortedByDescending { it.timestamp },
			newer.maxOfOrNull { it.timestamp } ?: lastProcessed,
		)
	}
}
