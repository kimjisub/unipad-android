package com.kimjisub.launchpad.analytics

/**
 * How the play screen's window sat on the display, in coarse buckets only: the exact size never
 * leaves the device. On Android 16 and later a large screen may ignore the landscape lock, so this
 * tells how many people really play in portrait or in a smaller window.
 */
data class ScreenLayout(
	val orientation: ScreenOrientation,
	val shortSide: ScreenShortSide,
	val window: WindowMode,
) {
	val parameters: Map<String, String>
		get() = mapOf(
			UsageParam.ORIENTATION to orientation.value,
			UsageParam.SCREEN_SHORT_SIDE to shortSide.value,
			UsageParam.WINDOW_MODE to window.value,
		)

	companion object {
		/**
		 * [widthDp] and [heightDp] are the window's size as the configuration reports it. A square
		 * window is portrait, as Android itself decides. Returns null while the size is undefined (0).
		 */
		fun of(widthDp: Int, heightDp: Int, multiWindow: Boolean): ScreenLayout? {
			if (widthDp <= 0 || heightDp <= 0) return null
			return ScreenLayout(
				orientation = if (widthDp > heightDp) ScreenOrientation.LANDSCAPE else ScreenOrientation.PORTRAIT,
				shortSide = ScreenShortSide.of(minOf(widthDp, heightDp)),
				window = if (multiWindow) WindowMode.MULTI_WINDOW else WindowMode.FULL_SCREEN,
			)
		}
	}
}

enum class ScreenOrientation(val value: String) {
	LANDSCAPE("landscape"),
	PORTRAIT("portrait"),
}

/** The window's short side, at the same 600dp and 840dp bounds as Android's window size classes. */
enum class ScreenShortSide(val value: String) {
	COMPACT("lt_600dp"),
	MEDIUM("600dp_839dp"),
	EXPANDED("840dp_plus");

	companion object {
		fun of(shortSideDp: Int): ScreenShortSide = when {
			shortSideDp < 600 -> COMPACT
			shortSideDp < 840 -> MEDIUM
			else -> EXPANDED
		}
	}
}

/** Full screen, or sharing the display in split screen or a free-form window. */
enum class WindowMode(val value: String) {
	FULL_SCREEN("full_screen"),
	MULTI_WINDOW("multi_window"),
}
