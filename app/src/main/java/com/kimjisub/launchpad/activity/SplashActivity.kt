package com.kimjisub.launchpad.activity

import android.Manifest.permission
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.gun0912.tedpermission.PermissionListener
import com.gun0912.tedpermission.TedPermission
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.databinding.ActivitySplashBinding
import splitties.activities.start

class SplashActivity : BaseActivity() {
	private lateinit var b: ActivitySplashBinding


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

		b.version.text = BuildConfig.VERSION_NAME

		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
			TedPermission.with(this)
				.setPermissionListener(object : PermissionListener {
					override fun onPermissionGranted() {
						handler.postDelayed(runnable, 2000)
					}

					override fun onPermissionDenied(deniedPermissions: List<String?>?) {
						handler.postDelayed(runnable, 2000)
					}
				})
				.setRationaleMessage(string.permissionRequire)
				.setDeniedMessage(string.permissionDenied)
				.setPermissions(
					permission.READ_EXTERNAL_STORAGE,
					permission.WRITE_EXTERNAL_STORAGE
				)
				.check()
		} else {
			handler.postDelayed(runnable, 2000)
		}
	}

	override fun onStop() {
		super.onStop()
		handler.removeCallbacks(runnable)
		finish()
	}

	override fun onDestroy() {
		super.onDestroy()
	}
}