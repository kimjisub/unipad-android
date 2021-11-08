package com.kimjisub.launchpad.manager.billing

import android.app.Activity
import android.util.Log
import androidx.lifecycle.LifecycleCoroutineScope
import com.android.billingclient.api.*
import com.android.billingclient.api.BillingFlowParams.ProrationMode.IMMEDIATE_WITH_TIME_PRORATION
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// https://jizard.tistory.com/263#google_vignette

class BillingModule(
	private val activity: Activity,
	private val lifeCycleScope: LifecycleCoroutineScope,
	private val callback: Callback,
) {
	// sku 들을 적어줍니다.
	private val inAppSkus = listOf<String>()
	private val subscriptSkus = listOf(Sku.PRO)


	private val skuDetailsList = ArrayList<SkuDetails>()

	// 구매관련 업데이트 수신
	private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
		when {
			billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null -> {
				// 제대로 구매 완료, 구매 확인 처리를 해야합니다. 3일 이내 구매확인하지 않으면 자동으로 환불됩니다.
				for (purchase in purchases) {
					confirmPurchase(purchase)
				}
			}
			else -> {
				// 구매 실패
				callback.onBillingFailure(billingResult.responseCode)
			}
		}
	}

	private var billingClient: BillingClient = BillingClient.newBuilder(activity)
		.setListener(purchasesUpdatedListener)
		.enablePendingPurchases()
		.build()

	fun load() {
		billingClient.startConnection(object : BillingClientStateListener {
			override fun onBillingSetupFinished(billingResult: BillingResult) {
				if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
					// 여기서부터 billingClient 활성화 됨
					callback.onBillingModulesIsReady()
					restorePurchase()
				} else {
					callback.onBillingFailure(billingResult.responseCode)
				}
			}

			override fun onBillingServiceDisconnected() {
				// GooglePlay와 연결이 끊어졌을때 재시도하는 로직이 들어갈 수 있음.
				Log.e("BillingModule", "Disconnected.")
			}
		})
	}

	fun release() {
		billingClient.endConnection()
	}

	/**
	 * 구매를 했지만 확인되지 않은 건에대해서 확인처리를 합니다.
	 * @param type BillingClient.SkuType.INAPP 또는 BillingClient.SkuType.SUBS
	 */
	fun onResume(type: String) {
		if (billingClient.isReady) {
			billingClient.queryPurchases(type).purchasesList?.let { purchaseList ->
				for (purchase in purchaseList) {
					if (!purchase.isAcknowledged && purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
						confirmPurchase(purchase)
					}
				}
			}
		}
	}

	/**
	 * 원하는 sku id를 가지고있는 상품 정보를 가져옵니다.
	 * @param sku sku 목록
	 * @param resultBlock sku 상품정보 콜백
	 */
	fun querySkuDetail(
		type: String = BillingClient.SkuType.INAPP,
		skus: List<String>,
		resultBlock: (List<SkuDetails>) -> Unit = {},
	) {
		SkuDetailsParams.newBuilder().apply {
			// 인앱, 정기결제 유형중에서 고름. (SkuType.INAPP, SkuType.SUBS)
			setSkusList(skus).setType(type)
			// 비동기적으로 상품정보를 가져옵니다.
			lifeCycleScope.launch(Dispatchers.IO) {
				val skuDetailResult = billingClient.querySkuDetails(build())
				withContext(Dispatchers.Main) {
					resultBlock(skuDetailResult.skuDetailsList ?: emptyList())
				}
			}
		}
	}

	fun findSkuDetails(sku: String): SkuDetails? {
		return skuDetailsList.find { it.sku == sku }
	}

	/**
	 * 구매 시작하기
	 * @param skuDetails 구매하고자하는 항목. querySkuDetail()을 통해 획득한 SkuDetail
	 * @param oldPurchase 이미 구독중일때, 현재 구독 구매 정보를 전달
	 */
	fun purchase(
		skuDetails: SkuDetails,
		oldPurchase: Purchase? = null,
	) {
		val flowParams = BillingFlowParams.newBuilder().apply {
			setSkuDetails(skuDetails)
			if (oldPurchase != null) {
				// # 구독을 위한 ProrationMode 문서: https://developer.android.com/reference/com/android/billingclient/api/BillingFlowParams.ProrationMode
				setReplaceSkusProrationMode(IMMEDIATE_WITH_TIME_PRORATION)
				setOldSku(oldPurchase.sku, oldPurchase.purchaseToken)
			}
		}.build()

		// 구매 절차를 시작, OK라면 제대로 된것입니다.
		val responseCode = billingClient.launchBillingFlow(activity, flowParams).responseCode
		if (responseCode != BillingClient.BillingResponseCode.OK) {
			callback.onBillingFailure(responseCode)
		}
		// 이후 부터는 purchasesUpdatedListener를 거치게 됩니다.
	}

	fun restorePurchase() {
		skuDetailsList.clear()
		querySkuDetail(BillingClient.SkuType.INAPP, inAppSkus) { inAppSkuDetailsList ->
			callback.onBillingInAppQuerySkuDetailResult(inAppSkuDetailsList)
			for (inAppSkuDetails in inAppSkuDetailsList) {
				skuDetailsList.add(inAppSkuDetails)
				checkPurchased(inAppSkuDetails.sku) { purchased ->
					callback.onBillingPurchaseUpdate(inAppSkuDetails, purchased)
				}
			}
		}
		querySkuDetail(
			BillingClient.SkuType.SUBS,
			subscriptSkus
		) { subsSkuDetailsList ->
			callback.onBillingSubsQuerySkuDetailResult(subsSkuDetailsList)
			for (subsSkuDetails in subsSkuDetailsList) {
				skuDetailsList.add(subsSkuDetails)
				checkSubscribed(subsSkuDetails.sku) { purchased ->
					callback.onBillingPurchaseUpdate(subsSkuDetails, purchased)
				}
			}
		}
	}


	/**
	 * 구매 여부 체크, 소비성 구매가 아닌 항목에 한정.
	 * @param sku
	 */
	fun checkPurchased(
		sku: String,
		resultBlock: (purchased: Boolean) -> Unit,
	) {
		billingClient.queryPurchases(BillingClient.SkuType.INAPP).purchasesList?.let { purchaseList ->
			for (purchase in purchaseList) {
				if (purchase.sku == sku && purchase.isPurchaseConfirmed()) {
					return resultBlock(true)
				}
			}
			return resultBlock(false)
		}
	}

	/**
	 * 구독 여부 체크
	 * @param sku
	 * @return 구독하지 않았다면 null을 반환합니다.
	 */

	private fun checkSubscribed(sku: String, resultBlock: (Boolean) -> Unit) {
		billingClient.queryPurchases(BillingClient.SkuType.SUBS).purchasesList?.let { purchaseList ->
			for (purchase in purchaseList) {
				if (purchase.sku == sku && purchase.isPurchaseConfirmed()) {
					return resultBlock(true)
				}
			}
			return resultBlock(false)
		}
	}


	fun checkPlan(resultBlock: (Purchase?) -> Unit) {
		billingClient.queryPurchases(BillingClient.SkuType.SUBS).purchasesList?.let { purchaseList ->
			for (purchase in purchaseList) {
				if (purchase.isPurchaseConfirmed()) {
					return resultBlock(purchase)
				}
			}
			return resultBlock(null)
		}
	}

	/**
	 * 구매 확인 처리
	 * @param purchase 확인처리할 아이템의 구매정보
	 */
	private fun confirmPurchase(purchase: Purchase) {
		when {
			// 소비성 구매는 consume 을 해주어야합니다.
			inAppSkus.contains(purchase.sku) -> {
				consumePurchase(purchase)
			}

			// 구매는 완료되었으나 확인이 되어있지 않다면 구매 확인 처리를 합니다.
			purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged -> {
				val ackPurchaseParams = AcknowledgePurchaseParams.newBuilder()
					.setPurchaseToken(purchase.purchaseToken)
				lifeCycleScope.launch(Dispatchers.IO) {
					val result = billingClient.acknowledgePurchase(ackPurchaseParams.build())
					withContext(Dispatchers.Main) {
						if (result.responseCode == BillingClient.BillingResponseCode.OK) {
							callback.onBillingSuccess(purchase)
							skuDetailsList.find { it.sku == purchase.sku }?.let {
								callback.onBillingPurchaseUpdate(it, purchase.isPurchaseConfirmed())
							}

						} else {
							callback.onBillingFailure(result.responseCode)
						}
					}
				}
			}
		}
	}

	private fun consumePurchase(purchase: Purchase) {
		val consumeParams = ConsumeParams.newBuilder()
			.setPurchaseToken(purchase.purchaseToken)
			.build()

		lifeCycleScope.launch(Dispatchers.IO) {
			val result = billingClient.consumePurchase(consumeParams)
			withContext(Dispatchers.Main) {
				if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
					callback.onBillingSuccess(purchase)
					skuDetailsList.find { it.sku == purchase.sku }?.let {
						callback.onBillingPurchaseUpdate(it, purchase.isPurchaseConfirmed())
					}
				}
			}
		}
	}

	// 구매 확인 검사 Extension
	private fun Purchase.isPurchaseConfirmed(): Boolean {
		return this.isAcknowledged && this.purchaseState == Purchase.PurchaseState.PURCHASED
	}

	interface Callback {
		fun onBillingModulesIsReady() {}
		fun onBillingInAppQuerySkuDetailResult(inAppSkuDetails: List<SkuDetails>) {}
		fun onBillingSubsQuerySkuDetailResult(subsSkuDetails: List<SkuDetails>) {}
		fun onBillingPurchaseUpdate(skuDetails: SkuDetails, purchased: Boolean) {}
		fun onBillingSuccess(purchase: Purchase) {}
		fun onBillingFailure(errorCode: Int) {}
	}
}