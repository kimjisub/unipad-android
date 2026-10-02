package com.kimjisub.launchpad.analytics

import com.kimjisub.launchpad.tool.Log

/**
 * Keeps events on the device: each one becomes a logcat line and nothing else. It holds no reference
 * to the Firebase SDK, so an event handed to it cannot reach a server.
 */
class LocalUsageEventSink : UsageEventSink {
	override fun log(name: String, parameters: Map<String, String>) {
		Log.log("$LOG_PREFIX $name $parameters")
	}

	companion object {
		const val LOG_PREFIX = "usage-local"
	}
}
