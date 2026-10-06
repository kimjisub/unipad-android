package com.kimjisub.launchpad.tool

import android.view.MotionEvent
import android.view.Window

/**
 * Applies [PrimaryButtonFilter] to a window that is not an activity's, such as a dialog's: a dialog
 * is a separate window, so the activity's own filter never sees its touches.
 * Everything else goes to the window's original callback.
 */
class PrimaryButtonWindowCallback private constructor(val base: Window.Callback) : Window.Callback by base {
	private val filter = PrimaryButtonFilter()

	override fun dispatchTouchEvent(event: MotionEvent): Boolean {
		if (filter.shouldDrop(event)) return true
		return base.dispatchTouchEvent(event)
	}

	companion object {
		fun install(window: Window) {
			val current = window.callback ?: return
			if (current !is PrimaryButtonWindowCallback) window.callback = PrimaryButtonWindowCallback(current)
		}

		fun uninstall(window: Window) {
			(window.callback as? PrimaryButtonWindowCallback)?.let { window.callback = it.base }
		}
	}
}
