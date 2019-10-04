package com.kimjisub.launchpad.activity

import android.Manifest.permission
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.core.content.ContextCompat
import com.anjlab.android.iab.v3.TransactionDetails
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.manager.BillingManager
import com.kimjisub.launchpad.manager.BillingManager.BillingEventListener
import kotlinx.android.synthetic.main.activity_splash.*
import org.jetbrains.anko.startActivity

class SplashActivity : BaseActivity() {
	internal var billingManager: BillingManager? = null
	// Timer
	var startTime: Long? = null
	internal var handler = Handler()
	internal var runnable = Runnable {
		finish()
		startActivity<MainActivity>()
	}

	val orange: Int by lazy { ContextCompat.getColor(this, color.orange) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(layout.activity_splash)
		startTime = System.currentTimeMillis()

		TV_version.text = BuildConfig.VERSION_NAME

		val billingEventListener = object : BillingEventListener {
			override fun onProductPurchased(productId: String, details: TransactionDetails?) {}
			override fun onPurchaseHistoryRestored() {}
			override fun onBillingError(errorCode: Int, error: Throwable?) {}
			override fun onBillingInitialized() {}
			override fun onRefresh() {
				if (billingManager!!.purchaseRemoveAds || billingManager!!.purchaseProTools)
					TV_version.setTextColor(orange)
			}
		}
		billingManager = BillingManager(this@SplashActivity, billingEventListener)

		TedPermission.with(this)
			.setPermissionListener(object : PermissionListener {
				override fun onPermissionGranted() {
					val endTime = System.currentTimeMillis()
					val durTime = endTime - startTime!!
					handler.postDelayed(runnable, 2000 - durTime)
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
	}

	override fun onStop() {
		super.onStop()
		handler.removeCallbacks(runnable)
		finish()
	}
}