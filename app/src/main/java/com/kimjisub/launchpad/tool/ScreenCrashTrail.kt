package com.kimjisub.launchpad.tool

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.google.firebase.crashlytics.FirebaseCrashlytics

/** The context attached to the next crash report. */
interface CrashContext {
	fun setKey(key: String, value: String)
	fun log(message: String)
}

object FirebaseCrashContext : CrashContext {
	override fun setKey(key: String, value: String) = FirebaseCrashlytics.getInstance().setCustomKey(key, value)
	override fun log(message: String) = FirebaseCrashlytics.getInstance().log(message)
}

/**
 * Records the screen in front and the recent screen changes in crash reports. A crash without
 * any app frame, such as one inside Android's drawing thread or the native audio engine, then
 * still shows which screen was open and which one was closing.
 */
class ScreenCrashTrail(private val crashContext: CrashContext) : Application.ActivityLifecycleCallbacks {
	companion object {
		const val KEY_SCREEN = "screen"
	}

	override fun onActivityResumed(activity: Activity) {
		val screen = activity.screenName()
		crashContext.setKey(KEY_SCREEN, screen)
		crashContext.log("$screen resumed")
	}

	override fun onActivityPaused(activity: Activity) = crashContext.log("${activity.screenName()} paused")

	override fun onActivityDestroyed(activity: Activity) = crashContext.log("${activity.screenName()} destroyed")

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
	override fun onActivityStarted(activity: Activity) {}
	override fun onActivityStopped(activity: Activity) {}
	override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

	// Activities keep their names in release builds because the manifest declares them.
	private fun Activity.screenName(): String = javaClass.simpleName
}
