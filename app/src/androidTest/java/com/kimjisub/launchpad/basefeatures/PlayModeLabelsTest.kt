package com.kimjisub.launchpad.basefeatures

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.AppLanguage
import com.kimjisub.launchpad.DisplaySettings
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.ui.compose.PlayModeSelectorTags
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The play menu's mode names are longer in some languages ("Reproducción Automática"). Each name
 * must be drawn whole, never cut short or broken inside a word, on a small landscape phone with
 * double-sized text as well as on a default phone, and each mode must still be selectable.
 * Accessibility exposes the full text even when it is cut, so the drawn lines are read.
 */
@RunWith(AndroidJUnit4::class)
class PlayModeLabelsTest : PlaybackScreenTest() {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun spanishSmallLarge() = check("es", SMALL, 2f)
    @Test fun spanishDefault() = check("es", DEFAULT, 1f)
    @Test fun indonesianSmallLarge() = check("in", SMALL, 2f)
    @Test fun indonesianDefault() = check("in", DEFAULT, 1f)
    @Test fun portugueseSmallLarge() = check("pt-BR", SMALL, 2f)
    @Test fun portugueseDefault() = check("pt-BR", DEFAULT, 1f)

    private fun check(language: String, display: Pair<Pair<Int, Int>, Int>, scale: Float) {
        val oldLanguage = AppLanguage.get()
        val oldScale = screen.device.executeShellCommand("settings get system font_scale").trim()
        try {
            screen.device.executeShellCommand("settings put system font_scale $scale")
            AppLanguage.set(language)
            DisplaySettings.withPhoneDisplay(screen.device, display.first, display.second) {
                File(screen.pack(), "autoPlay").writeText("delay 500\ntouch 1 1\ndelay 60000\n")
                screen.openPlay()
                assertEquals(scale, screen.resumed().resources.configuration.fontScale, 0.01f)
                screen.options()
                screen.capture("play-mode-$language-${display.first.first}x${display.first.second}-${display.second}-$scale")
                MODES.forEach { (_, label) -> assertDrawnWhole(screen.text(label)) }
                MODES.forEach { (mode, label) ->
                    screen.chooseOption(label)
                    screen.await("${screen.text(label)} was not selected") { screen.onMain { screen.vm().playMode == mode } }
                }
            }
        } finally {
            screen.device.executeShellCommand(
                if (oldScale == "null") "settings delete system font_scale" else "settings put system font_scale $oldScale"
            )
            AppLanguage.set(oldLanguage)
        }
    }

    private fun assertDrawnWhole(label: String) {
        val node = compose.onNode(
            hasText(label) and hasAnyAncestor(hasTestTag(PlayModeSelectorTags.SELECTOR)),
            useUnmergedTree = true,
        ).performScrollTo()
        node.assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        println("Mode '$label': lines=${layout.lineCount}, size=${layout.size}")
        assertFalse("'$label' is cut short", layout.hasVisualOverflow)
        for (line in 0 until layout.lineCount) {
            val end = layout.getLineEnd(line)
            assertTrue("'$label' breaks inside a word at $end",
                end >= label.length || label[end].isWhitespace() || label[end - 1].isWhitespace())
        }
    }

    private companion object {
        val SMALL = (720 to 1440) to 320
        val DEFAULT = (1080 to 2400) to 420
        val MODES = listOf(
            PlayMode.GuidePlay to R.string.guidePlay,
            PlayMode.StepPractice to R.string.stepPractice,
            PlayMode.AutoPlay to R.string.autoPlay,
        )
    }
}
