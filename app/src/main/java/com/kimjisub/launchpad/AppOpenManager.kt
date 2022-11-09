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

// https://developers.google.com/admob/android/app-open-ads

class AppOpenManager(
	val application: BaseApplication,
) : LifecycleObserver, Application.ActivityLifecycleCallbacks {
	private val AD_UNIT_ID = "ca-app-pub-1077445788578961/9421803373"
	private var appOpenAd: AppOpenAd? = null

	private var loadCallback: AppOpenAdLoadCallback? = null
	private var currentActivity: Activity? = null

	private var isShowingAd = false
	private var loadTime = 0L

	init {
		application.registerActivityLifecycleCallbacks(this)
		ProcessLifecycleOwner.get().lifecycle.addObserver(this)
	}

	private fun showAdsIfAvailable() {
		if (!isShowingAd && isAdAvailable()) {
			val fullScreenContentCallback = object : FullScreenContentCallback() {
				override fun onAdDismissedFullScreenContent() {
					appOpenAd = null
					isShowingAd = false
					fetchAd()
				}

				override fun onAdFailedToShowFullScreenContent(p0: AdError) {
				}

				override fun onAdShowedFullScreenContent() {
					isShowingAd = true
				}
			}

			appOpenAd?.fullScreenContentCallback = fullScreenContentCallback
			currentActivity?.let { appOpenAd?.show(it) }
		}
	}

	fun fetchAd() {
		if (isAdAvailable()) {
			return
		}

		loadCallback = object : AppOpenAdLoadCallback() {
			override fun onAdLoaded(ad: AppOpenAd) {
				appOpenAd = ad
				loadTime = Date().time
			}

			override fun onAdFailedToLoad(loadAdError: LoadAdError) {
				// Handle the error.
			}
		}
		val request = getAdRequest()
		AppOpenAd.load(
			application, AD_UNIT_ID, request,
			AppOpenAd.APP_OPEN_AD_ORIENTATION_PORTRAIT, loadCallback!!
		)
	}

	/** Creates and returns ad request.  */
	private fun getAdRequest(): AdRequest {
		return AdRequest.Builder().build()
	}

	private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
		val dateDifference = Date().time - loadTime
		val numMilliSecondsPerHour: Long = 3600000
		return dateDifference < numMilliSecondsPerHour * numHours
	}

	/** Utility method that checks if ad exists and can be shown.  */
	private fun isAdAvailable(): Boolean {
		return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
	}


	@OnLifecycleEvent(Lifecycle.Event.ON_START)
	fun onStart() {
		showAdsIfAvailable()
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