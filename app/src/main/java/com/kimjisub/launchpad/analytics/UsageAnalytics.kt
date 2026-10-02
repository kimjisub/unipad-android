package com.kimjisub.launchpad.analytics

import com.kimjisub.launchpad.tool.Log

/**
 * The one place usage events leave the app. Callers describe what happened with the enums in
 * [UsageEvent]; nothing here accepts free text, so a pack title, path, URL or error message
 * cannot end up in an event.
 */
class UsageAnalytics(private val sink: UsageEventSink) {

	/** One import attempt. Its first outcome is the only one recorded, however many layers report it. */
	fun packImport(source: PackImportSource) = PackImportReport(source, ::log)

	/** One visit to the play screen, from reading the pack to leaving. */
	fun newPlaySession(nanoTime: () -> Long = System::nanoTime) = PlaySessionTracker(nanoTime, ::log)

	/** Analytics must never break the feature that reports to it, so a failing sink is only logged. */
	private fun log(name: String, parameters: Map<String, String>) {
		val allowed = parameters.filterKeys { it in UsageParam.allowed }
		try {
			sink.log(name, allowed)
		} catch (e: Exception) {
			Log.err("Usage analytics event dropped: $name", e)
		}
	}
}
