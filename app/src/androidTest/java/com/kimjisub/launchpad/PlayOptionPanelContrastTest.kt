package com.kimjisub.launchpad

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
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

    private fun isDark(color: Color) = color.luminance() < 0.5f

    /** Black or white, whichever the panel's own text leans to; text on a fill keeps that direction. */
    private fun bestPossible(panel: Color, fillAlpha: Float): Float {
        val extreme = if (isDark(PlayPalette.optionPanelText(panel).primary)) Color.Black else Color.White
        return worstContrast(extreme, fillBackdrops(panel, fillAlpha))
    }

    private fun fillBackdrops(panel: Color, fillAlpha: Float): List<Color> {
        val fill = PlayPalette.panelContentOn(panel).copy(alpha = fillAlpha)
        return PlayPalette.optionPanelBackdrops(panel).map { fill.compositeOver(it) }
    }

    private fun worstContrast(color: Color, backdrops: List<Color>): Float =
        backdrops.minOf { PlayPalette.contrastRatio(color.compositeOver(it), it) }

    @Test
    fun readableSkinsKeepTheirTextColours() {
        listOf(panel(0xFFFFFFFF) to PlayPalette.panelContentOnLight, panel(0xFF424242) to PlayPalette.panelContentOnDark, PlayPalette.panelBackground to PlayPalette.panelContentOnDark)
            .forEach { (panel, content) ->
                val text = PlayPalette.optionPanelText(panel)
                assertEquals("Primary text changed on $panel", content, text.primary)
                assertEquals("Secondary text changed on $panel", content.copy(alpha = PlayPalette.SECONDARY_TEXT_ALPHA), text.secondary)
            }
    }

    @Test
    fun midGreySkinTextReachesBodyContrast() {
        val panel = panel(0xFF808080)
        val backdrops = PlayPalette.optionPanelBackdrops(panel)
        val oldPrimary = worstContrast(PlayPalette.panelContentOn(panel), backdrops)
        val text = PlayPalette.optionPanelText(panel)
        val primary = worstContrast(text.primary, backdrops)
        val secondary = worstContrast(text.secondary, backdrops)
        println("[#808080] primary %.2f -> %.2f, secondary %.2f".format(oldPrimary, primary, secondary))
        assertTrue("Plain dark/white pick is no longer below 4.5 on #808080 ($oldPrimary); revisit this test", oldPrimary < 4.5f)
        assertTrue("Primary text $primary on #808080", primary >= 4.5f)
        assertTrue("Secondary text $secondary on #808080", secondary >= 4.5f)
    }

    /**
     * Without changing the background, text on the info card and Play Mode fills of greys around
     * #6A6A6A–#838383 (#808080 among them), and any text on panels #767676–#7B7B7B, cannot reach 4.5:1
     * over every backdrop. Those get black
     * or white; this pins down exactly which panels fall short so the limit does not grow unnoticed.
     */
    @Test
    fun knownLimitsGetTheMostReadableColour() {
        val short = mutableListOf<String>()
        (0x60..0xA0).forEach { g ->
            val panel = panel(0xFF000000 or (g * 0x010101L))
            listOf(0f to "panel", PlayPalette.INFO_CARD_FILL_ALPHA to "info card", PlayPalette.PLAY_MODE_FILL_ALPHA to "play mode").forEach { (fillAlpha, where) ->
                val backdrops = fillBackdrops(panel, fillAlpha)
                val text = PlayPalette.optionPanelText(panel, fillAlpha)
                val best = bestPossible(panel, fillAlpha)
                val primary = worstContrast(text.primary, backdrops)
                val secondary = worstContrast(text.secondary, backdrops)
                assertEquals(
                    "#%02X %s text leans the other way from the panel text".format(g, where),
                    isDark(PlayPalette.optionPanelText(panel).primary),
                    isDark(text.primary.compositeOver(backdrops.first())),
                )
                if (best >= 4.5f) {
                    assertTrue("#%02X %s primary %.2f".format(g, where, primary), primary >= 4.5f)
                    assertTrue("#%02X %s secondary %.2f".format(g, where, secondary), secondary >= 4.5f)
                } else {
                    short += "#%02X %s %.2f".format(g, where, best)
                    assertEquals("#%02X %s primary is not the best colour".format(g, where), best, primary, 0.01f)
                    assertEquals("#%02X %s secondary is not the best colour".format(g, where), best, secondary, 0.01f)
                }
            }
        }
        println("Known below 4.5 (best possible): ${short.joinToString()}")
        val panelsShort = short.filter { " panel " in it }.map { it.substring(1, 3).toInt(16) }
        assertEquals("Panels that cannot reach 4.5 changed", (0x76..0x7B).toList(), panelsShort)
        assertTrue("#808080 info card is expected to fall short", short.any { it.startsWith("#80 info card") })
        assertTrue("#808080 play mode is expected to fall short", short.any { it.startsWith("#80 play mode") })
    }

    @Test
    fun readableSkinAccentIsPreserved() {
        val yellow = Color(0xFFFFEB3B)
        val backdrops = PlayPalette.optionPanelBackdrops(panel(0xFF424242))
        assertEquals(yellow, PlayPalette.readableOn(yellow, backdrops, PlayPalette.MIN_TEXT_CONTRAST))
    }
}
