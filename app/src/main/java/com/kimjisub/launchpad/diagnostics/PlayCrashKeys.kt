package com.kimjisub.launchpad.diagnostics

import com.kimjisub.launchpad.analytics.ScreenLayout
import com.kimjisub.launchpad.tool.CrashContext

/**
 * Crash reports carry the play screen's window in the same coarse buckets as `play_start`, so a
 * crash in a large window can be told apart. The keys hold a value only while a play screen is
 * open: a crash anywhere else reports no window, not the last one played in.
 *
 * One instance serves the whole app, because a retried pack opens its new play screen before the
 * old one is cleared up. The old one's [screenClosed] then finds the keys taken over and leaves them.
 */
class PlayCrashKeys(private val crashContext: CrashContext) {
	private var openScreen: Any? = null
	private var written: ScreenLayout? = null

	/** A null layout (a moment of undefined size) keeps the one known before. */
	fun screenLayoutChanged(screen: Any, layout: ScreenLayout?) {
		if (layout == null) return
		openScreen = screen
		if (layout == written) return
		written = layout
		layout.parameters.forEach { (name, value) -> crashContext.setKey(KEY_PREFIX + name, value) }
	}

	fun screenClosed(screen: Any) {
		if (screen !== openScreen) return
		openScreen = null
		written?.parameters?.keys?.forEach { crashContext.setKey(KEY_PREFIX + it, EMPTY) }
		written = null
	}

	companion object {
		const val KEY_PREFIX = "play_"

		// Crashlytics on Android has no call that removes a key; an empty value is the closest.
		const val EMPTY = ""
	}
}
