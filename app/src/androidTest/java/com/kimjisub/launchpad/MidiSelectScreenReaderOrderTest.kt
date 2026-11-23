package com.kimjisub.launchpad

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
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
 * The order TalkBack steps through the Korean selection screen, before and after the help opens:
 * status, help entry, the help (title, close, every question, web guide), then the selected model.
 *
 * Compose links the items in reading order only while a screen reader runs, so TalkBack (in the
 * Google APIs emulator image) is enabled for the test; the order is read from those links with
 * TalkBack's rules ([ScreenReaderOrder]). Its speech is not heard.
 */
class MidiSelectScreenReaderOrderTest {
    private val compose = createAndroidComposeRule<MidiSelectActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    // A connection without FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES would switch TalkBack off; UiDevice
    // reconnects with Configurator's flags, so they carry it too.
    private val automation get() = instrumentation.getUiAutomation(Configurator.getInstance().uiAutomationFlags)
    private fun str(id: Int) = compose.activity.getString(id)

    private val talkBackInKorean = object : TestRule {
        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                val config = Configurator.getInstance()
                val oldFlags = config.uiAutomationFlags
                config.setUiAutomationFlags(oldFlags or UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
                val oldServices = device.executeShellCommand("settings get secure enabled_accessibility_services").trim()
                val oldEnabled = device.executeShellCommand("settings get secure accessibility_enabled").trim()
                val oldLanguage = AppLanguage.get()
                fun restore(key: String, value: String) {
                    device.executeShellCommand(if (value == "null") "settings delete secure $key" else "settings put secure $key $value")
                }
                // On a fresh image TalkBack's first start asks for notifications over the app.
                val grantNotifications = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !device.executeShellCommand("dumpsys package $TALKBACK").contains("$NOTIFICATIONS: granted=true")
                try {
                    if (grantNotifications) device.executeShellCommand("pm grant $TALKBACK $NOTIFICATIONS")
                    AppLanguage.set("ko-KR")
                    val services = oldServices.takeUnless { it == "null" || it.isEmpty() }
                        ?.split(':').orEmpty().toMutableSet()
                    services.add("$TALKBACK/.TalkBackService")
                    device.executeShellCommand("settings put secure enabled_accessibility_services ${services.joinToString(":")}")
                    device.executeShellCommand("settings put secure accessibility_enabled 1")
                    val manager = requireNotNull(instrumentation.targetContext.getSystemService(AccessibilityManager::class.java))
                    val deadline = SystemClock.uptimeMillis() + 20_000L
                    while (manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                            .none { it.resolveInfo.serviceInfo.packageName == TALKBACK }) {
                        check(SystemClock.uptimeMillis() < deadline) { "TalkBack did not start" }
                        SystemClock.sleep(100)
                    }
                    base.evaluate()
                } finally {
                    restore("enabled_accessibility_services", oldServices)
                    restore("accessibility_enabled", oldEnabled)
                    if (grantNotifications) {
                        device.executeShellCommand("pm revoke $TALKBACK $NOTIFICATIONS")
                        device.executeShellCommand("pm clear-permission-flags $TALKBACK $NOTIFICATIONS user-set user-fixed")
                    }
                    AppLanguage.set(oldLanguage)
                    config.setUiAutomationFlags(oldFlags)
                    instrumentation.getUiAutomation(oldFlags)
                }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(Timeout.seconds(180)).around(talkBackInKorean).around(compose)

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
        val confirm = Expected("\"${str(android.R.string.ok)}\" (action)") { it.name == str(android.R.string.ok) && it.actionable }
        val entry = saying(R.string.midi_help_title, actionable = true)
        val log = StringBuilder("TalkBack 켠 상태의 화면 읽기 순서 기록 (한국어)\n")
        try {
            log.append("\n[도움말 닫힘]\n").append(awaitOrder("closed", listOf(status, entry, model, confirm)).joinToString("\n"))

            compose.onNode(hasText(str(R.string.midi_help_title)) and hasClickAction()).performClick()
            compose.waitForIdle()
            // Items scrolled out of the help are not on screen, so read the top and then the end of it.
            val top = listOf(R.string.midi_help_title, R.string.midi_help_close, R.string.midi_help_find,
                R.string.midi_help_find_body, R.string.midi_help_press, R.string.midi_help_lights).map { saying(it) }
            log.append("\n\n[도움말 열림, 맨 위]\n")
                .append(awaitOrder("open top", listOf(status, entry) + top + listOf(model, confirm)).joinToString("\n"))
            compose.onNodeWithText(str(R.string.midi_help_wrong)).performScrollTo()
            compose.waitForIdle()
            val middle = listOf(R.string.midi_help_lights, R.string.midi_help_wrong).map { saying(it) }
            log.append("\n\n[도움말 열림, 가운데]\n")
                .append(awaitOrder("open middle", listOf(status, entry) + middle + listOf(model, confirm)).joinToString("\n"))
            compose.onNodeWithText(str(R.string.midi_help_web_link)).performScrollTo()
            compose.waitForIdle()
            val end = listOf(R.string.midi_help_chain, R.string.midi_help_firmware, R.string.midi_help_crash,
                R.string.midi_help_web_link).map { saying(it) }
            log.append("\n\n[도움말 열림, 맨 아래]\n")
                .append(awaitOrder("open end", listOf(status, entry) + end + listOf(model, confirm)).joinToString("\n"))
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
            stops = ScreenReaderOrder.read(automation = automation)
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

private const val TALKBACK = "com.google.android.marvin.talkback"
private const val NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
