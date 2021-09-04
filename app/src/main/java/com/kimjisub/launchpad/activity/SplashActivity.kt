package com.kimjisub.launchpad.activity

import android.Manifest.permission
import android.os.Bundle
import android.os.Handler
import androidx.core.content.ContextCompat
import com.anjlab.android.iab.v3.BillingProcessor
import com.anjlab.android.iab.v3.TransactionDetails
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.color
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.databinding.ActivitySplashBinding
import com.kimjisub.launchpad.manager.BillingManager
import splitties.activities.start

class SplashActivity : BaseActivity() {
	private lateinit var b: ActivitySplashBinding
	internal lateinit var bm: BillingManager

	// Timer
	var startTime: Long? = null
	internal var handler = Handler()
	internal var runnable = Runnable {
		finish()
		start<MainActivity>()
	}

	val orange: Int by lazy { ContextCompat.getColor(this, color.orange) }

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivitySplashBinding.inflate(layoutInflater)
		setContentView(b.root)
		startTime = System.currentTimeMillis()

		b.version.text = BuildConfig.VERSION_NAME

		bm = BillingManager(this, object : BillingProcessor.IBillingHandler {
			override fun onProductPurchased(productId: String, details: TransactionDetails?) {
				//updateBilling()
			}

			override fun onPurchaseHistoryRestored() {}
			override fun onBillingError(errorCode: Int, error: Throwable?) {}
			override fun onBillingInitialized() {
				if (bm.isPro)
					b.version.setTextColor(orange)
			}
		})
		bm.initialize()

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

	override fun onDestroy() {
		super.onDestroy()
		bm.release()
	}
}