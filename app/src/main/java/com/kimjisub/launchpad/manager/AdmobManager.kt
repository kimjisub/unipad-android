package com.kimjisub.launchpad.manager

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.kimjisub.launchpad.tool.Log
import org.koin.android.ext.android.inject

class AdmobManager(val activity: Activity) {
	val context: Context = activity.baseContext

	val p: PreferenceManager by activity.inject()


	private fun adsCooltime(callback: () -> Unit) {
		if (checkAdsCooltime()) {
			updateAdsCooltime()

			callback()

		} else
			Log.admob("ads skip: cooltime")
	}

	private fun checkAdsCooltime(): Boolean {
		val prevTime = p.prevAdsShowTime
		val currTime = System.currentTimeMillis()
		return currTime < prevTime || currTime - prevTime >= Constant.ADS.COOLTIME
	}

	private fun updateAdsCooltime() {
		val currTime = System.currentTimeMillis()
		p.prevAdsShowTime = currTime
	}

	fun loadAds(unitId: String, callback: ((interstitialAd: InterstitialAd?) -> Unit)) {
		val adRequest = AdRequest.Builder().build()
		InterstitialAd.load(context, unitId, adRequest, object : InterstitialAdLoadCallback() {
			override fun onAdFailedToLoad(adError: LoadAdError) {
				Log.admob("Ads load fail: ${adError.message}")
				callback(null)
			}

			override fun onAdLoaded(interstitialAd: InterstitialAd) {
				Log.admob("Ads loaded.")
				callback(interstitialAd)
			}
		})
	}

	fun loadRewardedAd(unitId: String, callback: ((rewardedAd: RewardedAd?) -> Unit)) {
		val adRequest: AdRequest = AdRequest.Builder().build()
		RewardedAd.load(context, unitId,
			adRequest, object : RewardedAdLoadCallback() {
				override fun onAdFailedToLoad(loadAdError: LoadAdError) {
					callback(null)
				}

				override fun onAdLoaded(rewardedAd: RewardedAd) {
					callback(rewardedAd)
				}
			})
	}

	private fun showAds(interstitialAd: InterstitialAd, callback: (() -> Unit)? = null) {
		Log.admob("Ads showed")
		interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
			override fun onAdDismissedFullScreenContent() {
				super.onAdDismissedFullScreenContent()
				callback?.invoke()
			}

			override fun onAdFailedToShowFullScreenContent(p0: AdError) {
				super.onAdFailedToShowFullScreenContent(p0)
				callback?.invoke()
			}
		}
		interstitialAd.show(activity)
	}

	fun showRewardedAd(
		rewardedAd: RewardedAd,
		rewardedCallback: ((type: String, amount: Int) -> Unit)? = null,
	) {
		rewardedAd.show(activity) { rewardItem ->
			val rewardType = rewardItem.type
			val rewardAmount = rewardItem.amount
			Log.admob("Reward: $rewardType $rewardAmount")

			when (rewardType) {
				Constant.ADS.DOWNLOAD_COUPON_TYPE -> {
					p.downloadCouponCount += rewardAmount
				}
				Constant.ADS.PLAY_COUPON_TYPE -> {
					p.playCouponCount += rewardAmount
				}
			}

			rewardedCallback?.invoke(rewardType, rewardAmount)
		}
	}

	fun showAdsWithCooltime(interstitialAd: InterstitialAd?, callback: (() -> Unit)? = null) {
		Log.admob("Ads show")
		if (interstitialAd != null) {
			adsCooltime {
				showAds(interstitialAd, callback)
			}
		} else
			Log.admob("Ads not loaded")
	}

	fun immediatelyAds(unitId: String) {
		loadAds(unitId) {
			showAdsWithCooltime(it)
		}
	}
}