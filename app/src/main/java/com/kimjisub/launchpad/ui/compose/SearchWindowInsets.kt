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
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
