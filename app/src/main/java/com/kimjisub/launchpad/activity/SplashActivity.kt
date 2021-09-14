package com.kimjisub.launchpad.activity

import android.Manifest.permission
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.SkuDetails
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.color
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.databinding.ActivitySplashBinding
import com.kimjisub.launchpad.manager.billing.BillingModule
import com.kimjisub.launchpad.manager.billing.Sku
import com.kimjisub.manager.Log
import splitties.activities.start

class SplashActivity : BaseActivity(), BillingModule.Callback {
	private lateinit var b: ActivitySplashBinding
	private lateinit var bm: BillingModule

	private var isPro = false
		set(value) {
			field = value
			b.version.setTextColor(
				ContextCompat.getColor(
					this,
					if (field) color.orange else color.white
				)
			)
		}

	// Timer
	internal var handler = Handler(Looper.getMainLooper())
	internal var runnable = Runnable {
		finish()
		start<MainActivity>()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivitySplashBinding.inflate(layoutInflater)
		setContentView(b.root)

		bm = BillingModule(this, lifecycleScope, this)


		b.version.text = BuildConfig.VERSION_NAME

		TedPermission.with(this)
			.setPermissionListener(object : PermissionListener {
				override fun onPermissionGranted() {
					handler.postDelayed(runnable, 2000)
				}

				override fun onPermissionDenied(deniedPermissions: List<String?>?) {
					finish()
				}
			})
			.setRationaleMessage(string.permissionRequire)
			.setDeniedMessage(string.permissionDenied)
			.setPermissions(
				permission.READ_EXTERNAL_STORAGE,
				permission.WRITE_EXTERNAL_STORAGE
			)
			.check()

		logAdsId()
	}

	override fun onBillingPurchaseUpdate(skuDetails: SkuDetails, purchased: Boolean) {
		Log.billing("onPurchaseUpdate: ${skuDetails.sku} - $purchased")
		if (skuDetails.type == BillingClient.SkuType.SUBS)
			when (skuDetails.sku) {
				Sku.PRO -> {
					isPro = purchased
				}
			}
	}

	override fun onStop() {
		super.onStop()
		handler.removeCallbacks(runnable)
		finish()
	}

	override fun onDestroy() {
		super.onDestroy()
		bm.release()
	}
}