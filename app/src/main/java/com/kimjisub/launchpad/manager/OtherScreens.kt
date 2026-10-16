package com.kimjisub.launchpad.manager

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Whether any screen of this app other than [home] is still alive, including one that is still
 * closing behind [home] (a play screen tears its audio down after the list is back in front).
 * A screen created before [install] is picked up by its next lifecycle event.
 */
class OtherScreens private constructor(
	private val home: Class<out Activity>,
) : Application.ActivityLifecycleCallbacks {
	private val alive = mutableSetOf<Activity>()
	var anyAlive by mutableStateOf(false)
		private set

	private fun add(activity: Activity) {
		if (home.isInstance(activity)) return
		if (alive.add(activity)) anyAlive = true
	}

	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = add(activity)
	override fun onActivityStarted(activity: Activity) = add(activity)
	override fun onActivityResumed(activity: Activity) = add(activity)
	override fun onActivityPaused(activity: Activity) = add(activity)
	override fun onActivityStopped(activity: Activity) = add(activity)
	override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

	override fun onActivityDestroyed(activity: Activity) {
		if (alive.remove(activity)) anyAlive = alive.isNotEmpty()
	}

	companion object {
		private var instance: OtherScreens? = null

		fun install(application: Application, home: Class<out Activity>): OtherScreens =
			instance ?: OtherScreens(home).also {
				application.registerActivityLifecycleCallbacks(it)
				instance = it
			}
	}
}
