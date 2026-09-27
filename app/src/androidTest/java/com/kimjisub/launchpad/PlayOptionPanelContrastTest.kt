package com.kimjisub.launchpad

import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.manager.DefaultThemeResources
import com.kimjisub.launchpad.ui.theme.PlayPalette
import org.junit.Assert.assertEquals
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
}
