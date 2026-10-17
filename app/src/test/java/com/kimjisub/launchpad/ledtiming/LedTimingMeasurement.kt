package com.kimjisub.launchpad.ledtiming

import java.lang.management.ManagementFactory

/** One run set against the pack's intent (T1 − T0) and against what LedRunner handed over. */
class LedTimingMeasurement(val name: String, val timeline: IntendedTimeline, val recording: Recording) {
	val packToDriver = LedTimingResult.compare(timeline.commands, recording.toDriver)
	val runnerToDriver = LedTimingResult.compare(recording.handedOver, recording.toDriver)
	val packToRunner = LedTimingResult.compare(timeline.commands, recording.handedOver)

	/** Every command LedRunner handed over reached the driver, in the same order, and nothing else did. */
	val deliveredAsHandedOver: Boolean
		get() = recording.handedOver.map { it.target to it.code } == recording.toDriver.map { it.target to it.code }

	/**
	 * The pack's commands against everything that reached the driver, including what came after the run
	 * gave up. Only commands still due then can be expected, so extra ones after it are not a loss.
	 */
	val packToDriverWithLate: LedTimingResult by lazy {
		LedTimingResult.compare(timeline.commands, recording.toDriver + recording.toDriverLate)
	}

	/**
	 * Why the pack's commands did not all reach the driver in the pack's order in time, or null when
	 * they did. A loss (reordered, missing or extra commands) is named before anything else. Commands
	 * left after the last one that arrived on their LED are lag only when the run gave up with
	 * animations still playing and every one of them then arrived late, in order; otherwise they were
	 * lost.
	 */
	fun packToDriverFailure(): String? {
		val pack = packToDriver
		val gaveUpAfter = recording.gaveUpAfterReleaseMs
		val withLate = packToDriverWithLate
		val verdict = when {
			pack.reordered > 0 || pack.missing > 0 || pack.extra > 0 ->
				"commands lost: reordered ${pack.reordered}, missing ${pack.missing}, extra ${pack.extra}" +
					if (pack.unreached > 0) ", and the last ${pack.unreached} did not arrive in time" else ""
			pack.unreached == 0 -> return null
			gaveUpAfter == null -> "commands lost: the last ${pack.unreached} never reached the driver, though every animation ended"
			withLate.reordered == 0 && withLate.missing == 0 && withLate.unreached == 0 ->
				"the last ${pack.unreached} commands did not reach the driver within $gaveUpAfter ms of the final release " +
					"but all arrived in the next ${recording.lateForMs} ms: processing lag, nothing missing"
			else ->
				"commands lost: the last ${pack.unreached} did not reach the driver within $gaveUpAfter ms of the final release, " +
					"and ${recording.lateForMs} ms later still reordered ${withLate.reordered}, missing ${withLate.missing}, " +
					"never arrived ${withLate.unreached}"
		}
		return "$verdict\n${pack.differences.joinToString("\n")}"
	}

	fun describe(): String = buildString {
		val pack = packToDriver
		appendLine("LED timing: $name")
		appendLine("  pack: ${pack.expected} commands over ${timeline.lastT0} ms, " +
			"${"%.0f".format(timeline.averagePerSecond())}/s average, ${timeline.peakPerSecond()}/s at peak" +
			if (timeline.delaylessEndlessAnimations > 0) ", ${timeline.delaylessEndlessAnimations} endless animations without delay" else "")
		appendLine("  LedRunner -> driver: ${runnerToDriver.expected} handed over, ${runnerToDriver.arrived} arrived, " +
			"same order ${deliveredAsHandedOver}, ms median ${ms(runnerToDriver.median)} p95 ${ms(runnerToDriver.p95)} max ${ms(runnerToDriver.max)}")
		appendLine("  pack -> driver: matched ${pack.matched}, reordered ${pack.reordered}, missing ${pack.missing}, " +
			"unreached ${pack.unreached}, extra ${pack.extra}, " +
			"covered ${recording.covered}, overtaken by more than ${LedTimingResult.OVERTAKE_MS} ms ${pack.overtaken}, " +
			"release repaints ${recording.releaseRepaints}" +
			(recording.gaveUpAfterReleaseMs?.let {
				", gave up $it ms after the final release with animations playing, " +
					"then ${recording.toDriverLate.size} more arrived in ${recording.lateForMs} ms " +
					"(missing ${packToDriverWithLate.missing}, never arrived ${packToDriverWithLate.unreached})"
			} ?: ""))
		appendLine("  pack -> LedRunner handover ms: median ${ms(packToRunner.median)}, p95 ${ms(packToRunner.p95)}, max ${ms(packToRunner.max)}")
		appendLine("  T1-T0 ms: median ${ms(pack.median)}, p95 ${ms(pack.p95)}, max ${ms(pack.max)}, " +
			"build-up ${ms(pack.buildUpPerMinute())} ms/min, median by tenth ${pack.medianByTenth().joinToString(" ") { ms(it) }}")
		val pressDelays = recording.pressTimes.zip(timeline.pressTimes) { t1, t0 -> t1 - t0 }.sorted()
		if (pressDelays.isNotEmpty()) {
			appendLine("  autoplay press reaching main, ms after intended: median ${ms(percentileOf(pressDelays, 50.0))}, " +
				"p95 ${ms(percentileOf(pressDelays, 95.0))}, max ${ms(pressDelays.last())} " +
				"(${recording.pressTimes.size} of ${timeline.pressTimes.size} presses)")
		}
		appendLine("  ${environment()}")
		for (difference in runnerToDriver.differences) appendLine("  LedRunner -> driver differs: $difference")
		for (difference in pack.differences) appendLine("  pack -> driver differs: $difference")
	}

	companion object {
		/** The machine a run's latency belongs to. */
		fun environment(): String {
			val os = ManagementFactory.getOperatingSystemMXBean()
			return "JDK ${System.getProperty("java.version")} (${System.getProperty("java.vm.name")}), " +
				"${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}, " +
				"${Runtime.getRuntime().availableProcessors()} cores, load average ${"%.1f".format(os.systemLoadAverage)}"
		}

		private fun ms(value: Double) = "%.1f".format(value)
	}
}
