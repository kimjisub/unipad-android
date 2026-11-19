package com.kimjisub.launchpad

import android.content.Intent
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kimjisub.launchpad.activity.MidiSelectActivity
import com.kimjisub.launchpad.midi.MidiConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Constrain the real activity window, including its panels and padding, as in split screen. */
class NarrowWindowTextTest : BaseUITest() {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun packHistoryAtFoldSplitWidth() = checkHistory(546)
    @Test fun packHistoryAtTabletSplitWidth() = checkHistory(640)
    @Test fun packHistoryAtFullWidth() = checkHistory(null)
    @Test fun midiNamesAtFoldSplitWidth() = checkMidi(546)
    @Test fun midiNamesAtTabletSplitWidth() = checkMidi(640)
    @Test fun midiNamesAtFullWidth() = checkMidi(null)

    private fun checkHistory(widthDp: Int?) = withEnglish {
        val dao = com.kimjisub.launchpad.db.AppDatabase.getInstance(context).unipackDAO()
        dao.delete(TestUniPack.FOLDER_NAME)
        fun date(year: Int) = Date.from(LocalDate.of(year, 11, 19)
            .atStartOfDay(ZoneId.systemDefault()).toInstant())
        dao.insert(com.kimjisub.launchpad.db.ent.Unipack(
            TestUniPack.FOLDER_NAME, false, 123456789, date(2026), date(2025),
        ))
        launchToMainScreen()
        constrainWindow(widthDp)
        compose.waitUntil(5000L) {
            compose.onAllNodes(hasTestTag("main_pack_${TestUniPack.FOLDER_NAME}")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasTestTag("main_pack_${TestUniPack.FOLDER_NAME}")).performClick()
        compose.onNode(hasTestTag("main_detail_${TestUniPack.FOLDER_NAME}")).assertIsDisplayed()
        compose.waitUntil(10000L) {
            compose.onAllNodes(hasText("19/11/2026"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        takeScreenshot("narrow_history_${widthDp ?: "full"}")
        for (value in listOf("19/11/2026", "19/11/2025", "123456789")) {
            val layout = layout(compose.onNode(hasText(value), useUnmergedTree = true))
            assertEquals("Numeric value wraps: $value at ${widthDp}dp", 1, layout.lineCount)
            assertFalse("Numeric value is clipped: $value at ${widthDp}dp", layout.hasVisualOverflow)
        }
    }

    private fun checkMidi(widthDp: Int?) = withEnglish {
        val original = MidiConnection.driver
        try {
            launchApp(Intent(context, MidiSelectActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            constrainWindow(widthDp)
            takeScreenshot("narrow_midi_${widthDp ?: "full"}")
            for (name in listOf("Launchpad S", "Launchpad MK2", "Launchpad PRO")) {
                val card = compose.onNode(hasText(name) and hasAnyAncestor(hasClickAction()), useUnmergedTree = true)
                val result = layout(card)
                for (line in 0 until result.lineCount) {
                    val end = result.getLineEnd(line, visibleEnd = true)
                    assertTrue("Device name splits a word: $name at ${widthDp}dp, line $line ends at $end",
                        result.isLineEllipsized(line) || end == name.length || name[end].isWhitespace() ||
                            (end > 0 && name[end - 1].isWhitespace()))
                }
                card.performClick()
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { MidiConnection.driver = original }
        }
    }

    private fun layout(node: SemanticsNodeInteraction): TextLayoutResult {
        node.assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single().also { println("Text '${it.layoutInput.text}': ${it.size}, lines=${it.lineCount}, overflow=${it.hasVisualOverflow}") }
    }

    private fun constrainWindow(widthDp: Int?) {
        if (widthDp == null) {
            compose.waitForIdle()
            return
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            val density = activity.resources.displayMetrics.density
            activity.window.setLayout((widthDp * density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT)
        }
        assertTrue("Requested window width was not applied: ${widthDp}dp", waitUntil(5000L) {
            var matches = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
                matches = activity.window.decorView.width == (widthDp * activity.resources.displayMetrics.density).toInt()
            }
            matches
        })
        compose.waitForIdle()
    }

    private fun withEnglish(block: () -> Unit) {
        val language = AppLanguage.get()
        try {
            AppLanguage.set("en")
            block()
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .toList().forEach { it.finish() }
            }
            AppLanguage.set(language)
        }
    }
}
