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
        PlayPalette.worstContrast(color, PlayPalette.optionPanelBackdrops(panel))

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
        return PlayPalette.worstContrast(extreme, PlayPalette.optionPanelFillBackdrops(panel, fillAlpha))
    }

    private val cardFills = listOf(PlayPalette.INFO_CARD_FILL_ALPHA to "info card", PlayPalette.PLAY_MODE_FILL_ALPHA to "play mode")

    @Test
    fun readableSkinsKeepTheirTextColours() {
        listOf(panel(0xFFFFFFFF) to PlayPalette.panelContentOnLight, panel(0xFF424242) to PlayPalette.panelContentOnDark, PlayPalette.panelBackground to PlayPalette.panelContentOnDark)
            .forEach { (panel, content) ->
                val text = PlayPalette.optionPanelText(panel)
                assertEquals("Primary text changed on $panel", content, text.primary)
                assertEquals("Secondary text changed on $panel", content.copy(alpha = PlayPalette.SECONDARY_TEXT_ALPHA), text.secondary)
                cardFills.forEach { (nominal, where) ->
                    val fill = PlayPalette.optionPanelFill(panel, nominal)
                    assertEquals("$where fill changed on $panel", nominal, fill.alpha)
                    assertEquals("$where text changed on $panel", PlayPalette.optionPanelText(panel, nominal), fill.text)
                }
            }
    }

    /**
     * On #808080 the info card and Play Mode fills pulled their text to 4.48 and 4.37 on screen. The
     * fills are thinned just enough that black text, in the panel text's direction, reaches 4.5:1.
     */
    @Test
    fun midGreyCardFillsThinJustEnoughForBodyContrast() {
        val panel = panel(0xFF808080)
        cardFills.forEach { (nominal, where) ->
            val fill = PlayPalette.optionPanelFill(panel, nominal)
            val backdrops = PlayPalette.optionPanelFillBackdrops(panel, fill.alpha)
            val oldPrimary = PlayPalette.worstContrast(PlayPalette.optionPanelText(panel, nominal).primary, PlayPalette.optionPanelFillBackdrops(panel, nominal))
            val primary = PlayPalette.worstContrast(fill.text.primary, backdrops)
            val secondary = PlayPalette.worstContrast(fill.text.secondary, backdrops)
            println("[#808080] %s fill %.4f -> %.4f, primary %.2f -> %.2f, secondary %.2f".format(where, nominal, fill.alpha, oldPrimary, primary, secondary))
            assertTrue("$where at its full fill is no longer below 4.5 on #808080 ($oldPrimary); revisit this test", oldPrimary < 4.5f)
            assertTrue("$where fill ${fill.alpha} is not thinned on #808080", fill.alpha < nominal)
            assertTrue("$where fill ${fill.alpha} vanished on #808080", fill.alpha > 0f)
            assertTrue("$where fill ${fill.alpha} is thinner than needed", bestPossible(panel, fill.alpha + FILL_TOLERANCE) < 4.5f)
            assertTrue("$where primary $primary on #808080", primary >= 4.5f)
            assertTrue("$where secondary $secondary on #808080", secondary >= 4.5f)
            assertTrue("$where text is not dark on #808080", isDark(fill.text.primary))
        }
    }

    @Test
    fun midGreySkinTextReachesBodyContrast() {
        val panel = panel(0xFF808080)
        val backdrops = PlayPalette.optionPanelBackdrops(panel)
        val oldPrimary = PlayPalette.worstContrast(PlayPalette.panelContentOn(panel), backdrops)
        val text = PlayPalette.optionPanelText(panel)
        val primary = PlayPalette.worstContrast(text.primary, backdrops)
        val secondary = PlayPalette.worstContrast(text.secondary, backdrops)
        println("[#808080] primary %.2f -> %.2f, secondary %.2f".format(oldPrimary, primary, secondary))
        assertTrue("Plain dark/white pick is no longer below 4.5 on #808080 ($oldPrimary); revisit this test", oldPrimary < 4.5f)
        assertTrue("Primary text $primary on #808080", primary >= 4.5f)
        assertTrue("Secondary text $secondary on #808080", secondary >= 4.5f)
    }

    /**
     * Text on the bare panels #767676–#7B7B7B cannot reach 4.5:1 over every backdrop; their cards keep
     * the full fills, and text gets black or white. From #797979 up that text is black, which their light
     * fill helps, so only the cards of #767676–#787878 fall short. Elsewhere the fills are thinned
     * until card text reaches 4.5:1. This pins the bands down so they do not grow unnoticed.
     */
    @Test
    fun knownLimitsGetTheMostReadableColour() {
        val short = mutableListOf<String>()
        (0x60..0xA0).forEach { g ->
            val panel = panel(0xFF000000 or (g * 0x010101L))
            (listOf(0f to "panel") + cardFills).forEach { (nominal, where) ->
                val fill = PlayPalette.optionPanelFill(panel, nominal)
                assertTrue("#%02X %s fill %.4f is thicker than %.4f".format(g, where, fill.alpha, nominal), fill.alpha <= nominal)
                val backdrops = PlayPalette.optionPanelFillBackdrops(panel, fill.alpha)
                val text = fill.text
                val best = bestPossible(panel, fill.alpha)
                val primary = PlayPalette.worstContrast(text.primary, backdrops)
                val secondary = PlayPalette.worstContrast(text.secondary, backdrops)
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
        fun shortGreys(where: String) = short.filter { " $where " in it }.map { it.substring(1, 3).toInt(16) }
        assertEquals("Panels that cannot reach 4.5 changed", (0x76..0x7B).toList(), shortGreys("panel"))
        cardFills.forEach { (_, where) ->
            assertEquals("Panels whose $where cannot reach 4.5 changed", (0x76..0x78).toList(), shortGreys(where))
        }
    }

    @Test
    fun readableSkinAccentIsPreserved() {
        val yellow = Color(0xFFFFEB3B)
        val backdrops = PlayPalette.optionPanelBackdrops(panel(0xFF424242))
        assertEquals(yellow, PlayPalette.readableOn(yellow, backdrops, PlayPalette.MIN_TEXT_CONTRAST))
    }

    companion object {
        /** Just over one thinning step of either fill; a fill this much thicker must fall short again. */
        private const val FILL_TOLERANCE = 0.005f
    }
}
