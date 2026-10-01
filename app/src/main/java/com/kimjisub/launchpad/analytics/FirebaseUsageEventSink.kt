package com.kimjisub.launchpad.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.kimjisub.launchpad.tool.Log

/**
 * Hands events to the Firebase Analytics SDK the app already ships. It never changes the SDK's
 * collection settings, so a refusal already in effect stays in effect. `logEvent` only queues the
 * event on the SDK's own executor.
 */
class FirebaseUsageEventSink(private val context: Context) : UsageEventSink {
	private val firebase by lazy { FirebaseAnalytics.getInstance(context) }

	override fun log(name: String, parameters: Map<String, String>) {
		Log.log("$LOG_PREFIX $name $parameters")
		val bundle = Bundle()
		parameters.forEach { (key, value) -> bundle.putString(key, value) }
		firebase.logEvent(name, bundle)
	}

	companion object {
		const val LOG_PREFIX = "usage-firebase"
	}
}
