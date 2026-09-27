package com.kimjisub.launchpad

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.manager.DefaultThemeResources
import com.kimjisub.launchpad.ui.theme.PlayPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Play option panel takes its background from the skin's option_window colour. The built-in
 * skin ships white there, so fixed white text made the menu unreadable. The text colour must follow
 * the background and keep at least WCAG AA body contrast (4.5:1).
 */
@RunWith(AndroidJUnit4::class)
class PlayOptionPanelContrastTest {

    private fun assertReadable(background: Color) {
        val content = PlayPalette.panelContentOn(background)
        val ratio = PlayPalette.contrastRatio(content, background.copy(alpha = 1f))
        assertTrue("Contrast $ratio on $background is below 4.5", ratio >= 4.5f)
    }

    @Test
    fun defaultSkinPanelUsesDarkReadableText() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val optionWindow = requireNotNull(DefaultThemeResources(context, fullLoad = true).optionWindow)
        val background = Color(optionWindow)

        assertEquals(PlayPalette.panelContentOnLight, PlayPalette.panelContentOn(background))
        assertReadable(background)
    }

    @Test
    fun fallbackDarkPanelKeepsWhiteText() {
        assertEquals(PlayPalette.panelContentOnDark, PlayPalette.panelContentOn(PlayPalette.panelBackground))
        assertReadable(PlayPalette.panelBackground)
    }

    @Test
    fun skinPanelColoursStayReadable() {
        listOf(
            Color(0xFFFFFFFF),
            Color(0xFFF1F1F1),
            Color(0xFF424242),
            Color(0xFF000000),
            Color(0xFFFF9800),
        ).forEach(::assertReadable)
    }

    private val defaultAccent: Color
        get() = Color(InstrumentationRegistry.getInstrumentation().targetContext.getColor(R.color.option_window_checkbox))

    /** Panel colour as PlayActivity draws it (option_window at 94 % opacity). */
    private fun panel(optionWindow: Long) = Color(optionWindow).copy(alpha = 0.94f)

    private fun worstContrast(color: Color, panel: Color): Float =
        PlayPalette.optionPanelBackdrops(panel).minOf { PlayPalette.contrastRatio(color.compositeOver(it), it) }

    private fun assertMarksReadable(panel: Color) {
        val backdrops = PlayPalette.optionPanelBackdrops(panel)
        val label = PlayPalette.readableOn(defaultAccent, backdrops, PlayPalette.MIN_TEXT_CONTRAST)
        val arrow = PlayPalette.readableOn(defaultAccent.copy(alpha = 0.7f), backdrops, PlayPalette.MIN_GRAPHIC_CONTRAST)
        val quit = PlayPalette.readableOn(PlayPalette.danger, backdrops, PlayPalette.MIN_GRAPHIC_CONTRAST)
        assertTrue("Auto Mapping label ${worstContrast(label, panel)} on $panel", worstContrast(label, panel) >= 4.5f)
        assertTrue("Auto Mapping arrow ${worstContrast(arrow, panel)} on $panel", worstContrast(arrow, panel) >= 3f)
        assertTrue("Quit icon ${worstContrast(quit, panel)} on $panel", worstContrast(quit, panel) >= 3f)
        assertTrue("Quit icon lost its red on $panel: $quit", quit.red > quit.green + 0.2f && quit.red > quit.blue + 0.2f)
    }

    @Test
    fun defaultSkinKeepsItsAccentAndDarkensOnlyQuit() {
        val backdrops = PlayPalette.optionPanelBackdrops(panel(0xFFFFFFFF))
        assertEquals(defaultAccent, PlayPalette.readableOn(defaultAccent, backdrops, PlayPalette.MIN_TEXT_CONTRAST))
        assertEquals(
            defaultAccent.copy(alpha = 0.7f),
            PlayPalette.readableOn(defaultAccent.copy(alpha = 0.7f), backdrops, PlayPalette.MIN_GRAPHIC_CONTRAST),
        )
        assertNotEquals(PlayPalette.danger, PlayPalette.readableOn(PlayPalette.danger, backdrops, PlayPalette.MIN_GRAPHIC_CONTRAST))
        assertMarksReadable(panel(0xFFFFFFFF))
    }

    @Test
    fun darkSkinLiftsDefaultAccentAndKeepsQuit() {
        val backdrops = PlayPalette.optionPanelBackdrops(panel(0xFF424242))
        assertNotEquals(defaultAccent, PlayPalette.readableOn(defaultAccent, backdrops, PlayPalette.MIN_TEXT_CONTRAST))
        assertEquals(PlayPalette.danger, PlayPalette.readableOn(PlayPalette.danger, backdrops, PlayPalette.MIN_GRAPHIC_CONTRAST))
        assertMarksReadable(panel(0xFF424242))
    }

    @Test
    fun midGreySkinMarksStayReadable() {
        listOf(0xFF808080, 0xFF4283E6, 0xFF9E9E9E, 0xFF616161).forEach { assertMarksReadable(panel(it)) }
    }

    @Test
    fun readableSkinAccentIsPreserved() {
        val yellow = Color(0xFFFFEB3B)
        val backdrops = PlayPalette.optionPanelBackdrops(panel(0xFF424242))
        assertEquals(yellow, PlayPalette.readableOn(yellow, backdrops, PlayPalette.MIN_TEXT_CONTRAST))
    }
}
