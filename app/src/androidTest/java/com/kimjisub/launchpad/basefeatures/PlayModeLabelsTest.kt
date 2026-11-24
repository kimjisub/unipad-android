package com.kimjisub.launchpad.basefeatures

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.AppLanguage
import com.kimjisub.launchpad.DisplaySettings
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.PlayActivity
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
    @Test fun englishDefault() = check("en", DEFAULT, 1f)
    @Test fun koreanDefault() = check("ko", DEFAULT, 1f)

    private fun check(language: String, display: Pair<Pair<Int, Int>, Int>, scale: Float) {
        val oldLanguage = AppLanguage.get()
        val oldScale = screen.device.executeShellCommand("settings get system font_scale").trim()
        try {
            screen.device.executeShellCommand("settings put system font_scale $scale")
            AppLanguage.set(language)
            DisplaySettings.withPhoneDisplay(screen.device, display.first, display.second) {
                File(screen.pack(), "autoPlay").writeText("delay 500\ntouch 1 1\ndelay 60000\n")
                // Async loading and layout need Compose frames, which the sleeping screen helper cannot advance.
                screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
                compose.waitUntil(timeoutMillis = 10000) {
                    screen.onMain { !screen.vm().unipackLoading && !screen.vm().soundLoadingActive && screen.vm().uiLoaded }
                }
                assertEquals(scale, screen.resumed().resources.configuration.fontScale, 0.01f)
                openOptions()
                val captureName = "play-mode-$language-${display.first.first}x${display.first.second}-${display.second}-$scale"
                val keepOneRow = scale == 1f && language in listOf("en", "ko")
                screen.capture(captureName)
                assertLabelsAndTargets(keepOneRow)
                MODES.forEach { (mode, label) ->
                    compose.onNode(hasText(screen.text(label)) and hasAnyAncestor(hasTestTag(PlayModeSelectorTags.SELECTOR)))
                        .performScrollTo().performClick()
                    compose.waitUntil(timeoutMillis = 10000) {
                        screen.onMain { screen.vm().playMode == mode && !screen.vm().isOptionWindowVisible }
                    }
                    openOptions()
                    assertLabelsAndTargets(keepOneRow)
                    if (language == "es" && scale == 2f && mode == PlayMode.AutoPlay) {
                        screen.capture("$captureName-selected-autoplay")
                    }
                }
            }
        } finally {
            screen.device.executeShellCommand(
                if (oldScale == "null") "settings delete system font_scale" else "settings put system font_scale $oldScale"
            )
            AppLanguage.set(oldLanguage)
        }
    }

    private fun openOptions() {
        compose.onNodeWithContentDescription(screen.text(R.string.menu)).performClick()
        compose.waitForIdle()
        compose.onNode(hasTestTag(PlayModeSelectorTags.SELECTOR)).assertIsDisplayed()
    }

    private fun assertLabelsAndTargets(keepOneRow: Boolean) {
        val targets = MODES.map { (_, resource) ->
            val label = screen.text(resource)
            assertDrawnWhole(label)
            val target = compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(PlayModeSelectorTags.SELECTOR)))
                .performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val density = screen.onMain { screen.resumed().resources.displayMetrics.density }
            println("Mode '$label' displayed option height: ${target.height / density}dp")
            assertTrue("'$label' displayed option height ${target.height / density}dp is below the previous 44dp option",
                target.height >= BASELINE_OPTION_HEIGHT_DP * density - 1)
            target
        }
        if (keepOneRow) {
            targets.forEach { target ->
                assertEquals("Short default mode names must keep their side-by-side layout",
                    targets.first().top, target.top, 1f)
            }
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
        println("Mode '$label': lines=${layout.lineCount}, size=${layout.size}, " +
            "paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, overflow=${layout.hasVisualOverflow}")
        assertFalse("'$label' is cut short", layout.multiParagraph.didExceedMaxLines)
        for (line in 0 until layout.lineCount) {
            assertFalse("'$label' is ellipsized", layout.isLineEllipsized(line))
            // Text layout sizes round to whole pixels; allow that rounding at the glyph boundaries.
            assertTrue("'$label' is clipped vertically", layout.getLineBottom(line) <= layout.size.height + 1)
            assertTrue("'$label' is clipped horizontally",
                layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1)
            val end = layout.getLineEnd(line)
            assertTrue("'$label' breaks inside a word at $end",
                end >= label.length || label[end].isWhitespace() || label[end - 1].isWhitespace())
        }
    }

    private companion object {
        const val BASELINE_OPTION_HEIGHT_DP = 44f
        val SMALL = (720 to 1440) to 320
        val DEFAULT = (1080 to 2400) to 420
        val MODES = listOf(
            PlayMode.GuidePlay to R.string.guidePlay,
            PlayMode.StepPractice to R.string.stepPractice,
            PlayMode.AutoPlay to R.string.autoPlay,
        )
    }
}
