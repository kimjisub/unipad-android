package com.kimjisub.launchpad.activity

import android.Manifest.permission
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.color
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.databinding.ActivitySplashBinding
import splitties.activities.start

class SplashActivity : BaseActivity() {
	private lateinit var b: ActivitySplashBinding

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

		bm.load()


		b.version.text = BuildConfig.VERSION_NAME

		handler.postDelayed(runnable, 2000)

		/*TedPermission.with(this)
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
			.check()*/
	}

	override fun onProStatusUpdated(isPro: Boolean) {
		this.isPro = isPro
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