package com.kimjisub.launchpad.manager

import android.app.Activity
import com.anjlab.android.iab.v3.BillingProcessor
import com.kimjisub.launchpad.manager.Constant.BILLING


class DeprecatedBillingManager(internal var activity: Activity, handler: IBillingHandler) : BillingProcessor(
	activity,
	BILLING.DEVELOPERPAYLOAD,
	handler
) {
	val isPro: Boolean
		get() = isSubscribed(BILLING.PRO)


	fun subscribe(productId: String) {
		subscribe(activity, productId)
	}

	fun purchase(productId: String) {
		purchase(activity, productId)
	}

	fun subscribePro() {
		subscribe(BILLING.PRO)
	}
}