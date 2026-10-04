package com.kimjisub.launchpad.analytics

import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.tool.Log
import android.util.Log as AndroidLog

/**
 * Where a build sends its usage events. A release build always hands them to Firebase. A debug build
 * keeps them on the device, so runs on emulators and test devices add nothing to the real numbers.
 * Checking that the server receives the events is a separate, explicit run of a debug build:
 * `adb shell setprop log.tag.UniPadUsageToFirebase DEBUG`, then restart the app.
 *
 * Only the events of [UsageEvent] are routed here. The Firebase SDK's own collection settings, and
 * what it collects by itself, are the same in both builds and are not read or changed.
 */
object UsageEventRouting {
	/** At most 23 characters, the limit of `Log.isLoggable` before API 26. */
	const val SERVER_CHECK_LOG_TAG = "UniPadUsageToFirebase"

	fun sinkForThisBuild(firebase: () -> UsageEventSink): UsageEventSink = sinkFor(
		debugBuild = BuildConfig.DEBUG,
		serverCheckRequested = { AndroidLog.isLoggable(SERVER_CHECK_LOG_TAG, AndroidLog.DEBUG) },
		firebase = firebase,
	)

	/** [firebase] is only invoked when its sink is the one chosen, so a local run never creates it. */
	fun sinkFor(debugBuild: Boolean, serverCheckRequested: () -> Boolean, firebase: () -> UsageEventSink): UsageEventSink = when {
		!debugBuild -> firebase()
		serverCheckRequested() -> firebase().also { Log.log("usage events of this debug run go to Firebase (server check)") }
		else -> LocalUsageEventSink().also { Log.log("usage events of this debug run stay on the device") }
	}
}
