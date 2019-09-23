package com.kimjisub.launchpad.manager

import android.app.Activity
import com.anjlab.android.iab.v3.BillingProcessor
import com.anjlab.android.iab.v3.BillingProcessor.IBillingHandler
import com.anjlab.android.iab.v3.TransactionDetails
import com.kimjisub.launchpad.manager.Constant.BILLING

class BillingManager(internal var activity: Activity) {
	private var billingProcessor: BillingProcessor
	private var refreshDone = false

	var purchaseRemoveAds = false
	var purchaseProTools = false

	// ============================================================================================= Constructor

	constructor(activity: Activity, billingEventListener: BillingEventListener?) : this(activity) {
		this.billingEventListener = billingEventListener
	}


	// ============================================================================================= Getter


	val showAds: Boolean
		get() = purchaseRemoveAds

	val unlockProTools: Boolean
		get() = purchaseProTools

	// ============================================================================================= Cycle


	fun refresh() {
		if (billingProcessor.isInitialized) {
			billingProcessor.loadOwnedPurchasesFromGoogle()
			purchaseRemoveAds = billingProcessor.isSubscribed(BILLING.REMOVE_ADS)
			purchaseProTools = billingProcessor.isSubscribed(BILLING.PRO_TOOLS)
			refreshDone = true
			_onRefresh()
		}
	}

	fun release() {
		if (billingProcessor.isInitialized) billingProcessor.release()
	}

	val isAvailable: Boolean
		get() = BillingProcessor.isIabServiceAvailable(activity)

	val isOneTimePurchaseSupported: Boolean
		get() = billingProcessor.isOneTimePurchaseSupported

	val isSubscriptionUpdateSupported: Boolean
		get() = billingProcessor.isSubscriptionUpdateSupported

	fun restorePurchases() {
		billingProcessor.loadOwnedPurchasesFromGoogle()
	}

	// =============================================================================================


	fun subscribe(productId: String?) {
		billingProcessor.subscribe(activity, productId)
	}

	fun purchase(productId: String?) {
		billingProcessor.purchase(activity, productId)
	}

	// =============================================================================================


	fun subscribe_removeAds() {
		subscribe(BILLING.REMOVE_ADS)
	}

	fun subscribe_proTools() {
		subscribe(BILLING.PRO_TOOLS)
	}

	// ============================================================================================= BillingEventListener


	internal var billingEventListener: BillingEventListener? = null

	interface BillingEventListener {
		fun onProductPurchased(productId: String, details: TransactionDetails?)
		fun onPurchaseHistoryRestored()
		fun onBillingError(errorCode: Int, error: Throwable?)
		fun onBillingInitialized()
		fun onRefresh()
	}

	internal fun _onProductPurchased(productId: String, details: TransactionDetails?) {
		if (billingEventListener != null) billingEventListener!!.onProductPurchased(productId, details)
	}

	internal fun _onPurchaseHistoryRestored() {
		if (billingEventListener != null) billingEventListener!!.onPurchaseHistoryRestored()
	}

	internal fun _onBillingError(errorCode: Int, error: Throwable?) {
		if (billingEventListener != null) billingEventListener!!.onBillingError(errorCode, error)
	}

	internal fun _onBillingInitialized() {
		if (billingEventListener != null) billingEventListener!!.onBillingInitialized()
	}

	internal fun _onRefresh() {
		if (billingEventListener != null) billingEventListener!!.onRefresh()
	}


	init {
		billingProcessor = BillingProcessor(
			activity,
			BILLING.DEVELOPERPAYLOAD,
			object : IBillingHandler {
				override fun onProductPurchased(
					productId: String,
					details: TransactionDetails?
				) {
					refresh()
					_onProductPurchased(productId, details)
				}

				override fun onPurchaseHistoryRestored() {
					_onPurchaseHistoryRestored()
				}

				override fun onBillingError(errorCode: Int, error: Throwable?) {
					_onBillingError(errorCode, error)
				}

				override fun onBillingInitialized() {
					refresh()
					_onBillingInitialized()
				}
			})
		billingProcessor.initialize()
	}
}