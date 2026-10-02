package com.kimjisub.launchpad.analytics

/** Where finished usage events go. Tests hand in a recording one; the app's is chosen by [UsageEventRouting]. */
fun interface UsageEventSink {
	fun log(name: String, parameters: Map<String, String>)
}
