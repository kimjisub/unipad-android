package com.kimjisub.launchpad

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import java.util.*


class AppOpenManager(
	val application: BaseApplication,
) : LifecycleObserver, Application.ActivityLifecycleCallbacks {

	private var currentActivity: Activity? = null


	init {
		application.registerActivityLifecycleCallbacks(this)
		ProcessLifecycleOwner.get().lifecycle.addObserver(this)
	}


	// Activity

	override fun onActivityCreated(activity: Activity, p1: Bundle?) {
	}

	override fun onActivityStarted(activity: Activity) {
		currentActivity = activity
	}

	override fun onActivityResumed(activity: Activity) {
		currentActivity = activity
	}

	override fun onActivityPaused(activity: Activity) {
	}

	override fun onActivityStopped(activity: Activity) {
	}

	override fun onActivitySaveInstanceState(activity: Activity, p1: Bundle) {
	}

	override fun onActivityDestroyed(activity: Activity) {
		currentActivity = null
	}
}