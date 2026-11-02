package com.kimjisub.launchpad.manager

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Whether any app screen is started, including beside another app in split screen. */
class AppVisibility : Application.ActivityLifecycleCallbacks {
	private var started = 0
	// Work without an activity can run until the first visibility event, as before.
	private val isVisible = MutableStateFlow(true)
	val visible = isVisible.asStateFlow()

	override fun onActivityStarted(activity: Activity) {
		started++
		isVisible.value = true
	}

	override fun onActivityStopped(activity: Activity) {
		started--
		if (started == 0 && !activity.isChangingConfigurations) isVisible.value = false
	}

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
	override fun onActivityResumed(activity: Activity) {}
	override fun onActivityPaused(activity: Activity) {}
	override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
	override fun onActivityDestroyed(activity: Activity) {}

	companion object {
		val process = AppVisibility()
	}
}
