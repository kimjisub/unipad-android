package com.kimjisub.launchpad.ui.compose

import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Keeps search panels full height while delivering keyboard insets to their scrollable lists. */
fun configureSearchWindowInsets(window: Window) {
	WindowCompat.setDecorFitsSystemWindows(window, false)
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
		// Before API 30, FLAG_FULLSCREEN prevents adjustResize from delivering IME insets.
		// Hide the status bar through system UI visibility instead, without that window flag.
		@Suppress("DEPRECATION") // Only needed on versions before the replacement insets API.
		window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
		val controller = WindowCompat.getInsetsController(window, window.decorView)
		controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
		controller.hide(WindowInsetsCompat.Type.statusBars())
		// Another activity, the document picker or a dialog can clear legacy fullscreen visibility.
		// Focus also returns after a dialog closes without the activity being resumed again.
		window.decorView.viewTreeObserver.addOnWindowFocusChangeListener { hasFocus ->
			if (hasFocus) controller.hide(WindowInsetsCompat.Type.statusBars())
		}
	}
}
