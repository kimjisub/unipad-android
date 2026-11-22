package com.kimjisub.launchpad

import android.os.SystemClock
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.kimjisub.launchpad.activity.MidiSelectActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * The order a screen reader steps through the Korean selection screen, before and after the help opens:
 * status, help entry, the help (title, close, every question, web guide), then the selected model.
 */
class MidiSelectScreenReaderOrderTest {
    private val compose = createAndroidComposeRule<MidiSelectActivity>()
    private val korean = object : TestRule {
        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                val original = AppLanguage.get()
                AppLanguage.set("ko-KR")
                try { base.evaluate() } finally { AppLanguage.set(original) }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(Timeout.seconds(180)).around(korean).around(compose)
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun str(id: Int) = compose.activity.getString(id)

    private class Expected(val label: String, val matches: (ScreenReaderOrder.Stop) -> Boolean)

    private fun saying(id: Int, actionable: Boolean = false) =
        Expected("\"${str(id)}\"${if (actionable) " (action)" else ""}") { it.name.contains(str(id)) && (!actionable || it.actionable) }

    @Test fun statusHelpQuestionsAndModelFollowTheReadingOrder() {
        val status = Expected("status") {
            it.name.contains(str(R.string.midi_status_none)) || it.name.contains(str(R.string.midi_status_disconnected))
        }
        val models = listOf(R.string.midi_lp_s, R.string.midi_lp_mk2, R.string.midi_lp_pro,
            R.string.midi_lp_pro_cfw, R.string.midi_lp_x, R.string.midi_lp_mini_mk3,
            R.string.midi_lp_mk3, R.string.midi_midi_fighter, R.string.midi_matrix,
            R.string.midi_master_keyboard, R.string.midi_lp_core_fw).map(::str)
        val model = Expected("selected model") { it.name in models }
        val entry = saying(R.string.midi_help_title, actionable = true)
        val log = StringBuilder("화면 읽기 순서 기록 (한국어)\n")
        try {
            log.append("\n[도움말 닫힘]\n").append(awaitOrder("closed", listOf(status, entry, model)).joinToString("\n"))

            compose.onNode(hasText(str(R.string.midi_help_title)) and hasClickAction()).performClick()
            compose.waitForIdle()
            val help = listOf(R.string.midi_help_title, R.string.midi_help_close, R.string.midi_help_find,
                R.string.midi_help_find_body, R.string.midi_help_press, R.string.midi_help_lights,
                R.string.midi_help_wrong, R.string.midi_help_chain, R.string.midi_help_firmware,
                R.string.midi_help_crash, R.string.midi_help_web_link).map { saying(it) }
            log.append("\n\n[도움말 열림]\n").append(awaitOrder("open", listOf(status, entry) + help + model).joinToString("\n"))
        } finally {
            assertTrue(TestCaptures.save(device, "midi/screen-reader-order-ko.txt") { it.writeText(log.toString()); it.length() > 0 })
        }
    }

    /** Reads until [expected] appears in order (other items may sit between), as the screen may still be settling. */
    private fun awaitOrder(state: String, expected: List<Expected>): List<ScreenReaderOrder.Stop> {
        val deadline = SystemClock.uptimeMillis() + 10_000L
        var stops: List<ScreenReaderOrder.Stop>
        var problem: String?
        do {
            stops = ScreenReaderOrder.read()
            var from = 0
            problem = expected.firstNotNullOfOrNull { want ->
                val at = (from until stops.size).firstOrNull { want.matches(stops[it]) }
                if (at == null) "${want.label} missing after item $from" else { from = at + 1; null }
            } ?: stops.firstOrNull { it.actionable && it.name.isBlank() }?.let { "a control has no name: $it" }
            if (problem == null) return stops
            SystemClock.sleep(200)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("$state: $problem\n${stops.joinToString("\n")}")
    }
}
