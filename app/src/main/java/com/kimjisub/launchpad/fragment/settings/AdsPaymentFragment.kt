package com.kimjisub.launchpad.fragment.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.billing.BillingModule
import com.kimjisub.launchpad.manager.billing.Sku
import com.kimjisub.launchpad.tool.Log
import splitties.toast.toast

class AdsPaymentFragment : PreferenceFragmentCompat(), BillingModule.Callback {
	private lateinit var bm: BillingModule

	private val unipadProPreference: CheckBoxPreference by lazy { findPreference("unipad_pro")!! }
	private val restoreBillingPreference: Preference by lazy { findPreference("restore_billing")!! }

	private var isPro = false
		set(value) {
			field = value
			unipadProPreference.isChecked = value
		}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.preferences_ads_payment, rootKey)

		bm = BillingModule(requireActivity(), lifecycleScope, this)
		bm.load()

		unipadProPreference.setOnPreferenceChangeListener { preference, _ ->
			if(!isPro){
				bm.findSkuDetails(Sku.PRO)?.let {
					bm.purchase(it)
				} ?: also {
					toast("상품을 찾을 수 없습니다.")
				}
			}else{
				val context = requireContext()
				val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0);
				val packageName = packageInfo.packageName
				val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/account/subscriptions?sku=${Sku.PRO}&package=${packageName}"));
				startActivity(browserIntent);
			}
			false
		}

		restoreBillingPreference.setOnPreferenceClickListener {
			bm.restorePurchase()
			false
		}
	}

	// Billing Cycle

	override fun onBillingPurchaseUpdate(skuDetails: SkuDetails, purchased: Boolean) {
		Log.billing("onPurchaseUpdate: ${skuDetails.sku} - $purchased")

		if (skuDetails.type == BillingClient.SkuType.SUBS)
			when (skuDetails.sku) {
				Sku.PRO -> {
					isPro = purchased
				}
			}
	}

	override fun onBillingFailure(errorCode: Int) {
		when (errorCode) {

			// todo string.xml
			BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
				toast("이미 구입한 상품입니다.")
			}
			BillingClient.BillingResponseCode.USER_CANCELED -> {
				toast("구매를 취소하셨습니다.")
			}
			BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
				toast("Google Billing 서비스를 사용할 수 없습니다.")
			}
			BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
				toast("Google Billing Developer Error")
			}
			else -> {
				toast("error: $errorCode")
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()
		bm.release()
	}
}