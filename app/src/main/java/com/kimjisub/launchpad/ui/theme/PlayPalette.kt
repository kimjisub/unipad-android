package com.kimjisub.launchpad.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
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

	/** WCAG AA contrast for body text. */
	const val MIN_TEXT_CONTRAST = 4.5f

	/** WCAG AA contrast for icons and marks that stand for a control. */
	const val MIN_GRAPHIC_CONTRAST = 3f

	/** Dims the play screen behind the open option panel. */
	val optionScrim = Color.Black.copy(alpha = 0.5f)

	/**
	 * What the (slightly translucent) option panel can look like on screen: [panelBackground] over the
	 * scrim, over the darkest and the lightest play screen a skin can put behind it.
	 */
	fun optionPanelBackdrops(panelBackground: Color): List<Color> =
		listOf(Color.Black, Color.White).map { panelBackground.compositeOver(optionScrim.compositeOver(it)) }

	/**
	 * Keeps [color] when it already reaches [minRatio] on every one of [backgrounds]. Otherwise moves it
	 * toward [towards], or else black or white, whichever the backgrounds contrast with more, only as far
	 * as needed. Only lightness changes, so a skin's accent or the red Quit tint keeps its hue.
	 */
	fun readableOn(color: Color, backgrounds: List<Color>, minRatio: Float, towards: Color? = null): Color {
		fun worstContrast(candidate: Color) = backgrounds.minOf { contrastRatio(candidate.compositeOver(it), it) }
		if (worstContrast(color) >= minRatio) return color

		val target = towards ?: listOf(Color.Black, Color.White).maxBy(::worstContrast)
		val drawn = color.compositeOver(backgrounds.first())
		return (1..READABLE_STEPS)
			.map { lerp(drawn, target, it.toFloat() / READABLE_STEPS) }
			.firstOrNull { worstContrast(it) >= minRatio }
			?: target
	}

	private const val READABLE_STEPS = 50

	data class OptionPanelText(val primary: Color, val secondary: Color)

	const val SECONDARY_TEXT_ALPHA = 0.65f

	const val INFO_CARD_FILL_ALPHA = 0.06f

	const val PLAY_MODE_FILL_ALPHA = 0.08f

	/**
	 * Text colours for the option panel, or for a [fillAlpha] tint of the content colour laid on it.
	 * Picking between the dark and the white content colour is not enough on mid greys (#808080 left
	 * both below 4.5:1), so each is pulled towards black or white until it reads on every backdrop.
	 * Text on a fill keeps the panel text's direction so one menu never mixes dark and light text.
	 * Where no colour gets there (panels #767676–#7B7B7B, and the fills on greys around
	 * #6A6A6A–#838383 such as #808080) the result is black or white.
	 */
	fun optionPanelText(panelBackground: Color, fillAlpha: Float = 0f): OptionPanelText {
		val content = panelContentOn(panelBackground)
		val panelBackdrops = optionPanelBackdrops(panelBackground)
		val onPanel = readableOn(content, panelBackdrops, MIN_TEXT_CONTRAST)
		val towards = if (onPanel.luminance() < 0.5f) Color.Black else Color.White
		val backdrops = panelBackdrops.map { content.copy(alpha = fillAlpha).compositeOver(it) }
		val primary = readableOn(onPanel, backdrops, MIN_TEXT_CONTRAST, towards)
		val secondary = readableOn(primary.copy(alpha = SECONDARY_TEXT_ALPHA), backdrops, MIN_TEXT_CONTRAST, towards)
		return OptionPanelText(primary, secondary)
	}

	// Play mode distinctive colors. Each mode owns a hue so the segmented control
	// communicates mode identity without relying on text alone.
	val modeAutoPlay = Color(0xFFE8A44A)
	val modeGuidePlay = Color(0xFF4FC3F7)
	val modeStepPractice = Color(0xFF66BB6A)
}
