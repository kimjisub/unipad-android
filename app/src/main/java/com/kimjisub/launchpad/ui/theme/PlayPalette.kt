package com.kimjisub.launchpad.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Centralized Play screen UI tokens. Previously these hex values were scattered across
 * PlayActivity composables (OptionPanel, ChromeColumn, mode buttons). Consolidating them
 * keeps the color story consistent and avoids CLAUDE.md's "no hardcoded colors" guidance
 * being violated at the view layer.
 *
 * These are UI chrome tokens, distinct from per-unipack ThemeResources which remain
 * user-customizable via theme packs.
 */
object PlayPalette {
	/** Primary accent for the Play screen (progress, active state highlights). */
	val accent = Color(0xFFE8A44A)

	/** Destructive action tint (Quit icon, error states). */
	val danger = Color(0xFFFF6B6B)

	/** Slide-in option panel background. */
	val panelBackground = Color(0xF0161E2B)

	/** Option panel text/icon colour on light skin backgrounds (the built-in skin's option_window is white). */
	val panelContentOnLight = Background1

	/** Option panel text/icon colour on dark skin backgrounds. */
	val panelContentOnDark = White

	/**
	 * Picks whichever panel content colour contrasts more with [background], so the option panel
	 * stays readable whatever option_window colour a skin supplies. Alpha is ignored: the panel is
	 * drawn nearly opaque.
	 */
	fun panelContentOn(background: Color): Color {
		val opaque = background.copy(alpha = 1f)
		return if (contrastRatio(panelContentOnLight, opaque) >= contrastRatio(panelContentOnDark, opaque))
			panelContentOnLight
		else
			panelContentOnDark
	}

	/** WCAG 2 contrast ratio between two opaque colours (1..21). */
	fun contrastRatio(a: Color, b: Color): Float {
		val la = a.luminance()
		val lb = b.luminance()
		return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
	}

	// Play mode distinctive colors. Each mode owns a hue so the segmented control
	// communicates mode identity without relying on text alone.
	val modeAutoPlay = Color(0xFFE8A44A)
	val modeGuidePlay = Color(0xFF4FC3F7)
	val modeStepPractice = Color(0xFF66BB6A)
}
