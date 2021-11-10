package com.kimjisub.launchpad.fragment

import androidx.fragment.app.Fragment
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import com.kimjisub.launchpad.manager.billing.BillingModule
import com.kimjisub.launchpad.manager.billing.Sku
import com.kimjisub.launchpad.tool.Log
import org.koin.android.ext.android.inject

open class BaseFragment : Fragment(), BillingModule.Callback {
	val p: PreferenceManager by inject()
	val ws: WorkspaceManager by inject()

	//val ads by lazy { AdmobManager(this) }
	//val bm by lazy { BillingModule(this, lifecycleScope, this) }


	override fun onBillingPurchaseUpdate(skuDetails: SkuDetails, purchased: Boolean) {
		Log.billing("onPurchaseUpdate: ${skuDetails.sku} - $purchased")
		if (skuDetails.type == BillingClient.SkuType.SUBS)
			when (skuDetails.sku) {
				Sku.PRO -> {
					onProStatusUpdated(purchased)
				}
			}
	}

	open fun onProStatusUpdated(isPro: Boolean) {

	}

}