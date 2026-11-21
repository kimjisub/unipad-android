package com.kimjisub.launchpad

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

/** Requires the TalkBack service included in the Google APIs emulator image. */
class MidiSelectTalkBackTest {
    private val compose = createAndroidComposeRule<MidiSelectActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)
    private val automation get() = instrumentation.getUiAutomation(Configurator.getInstance().uiAutomationFlags)
    private fun str(id: Int) = compose.activity.getString(id)

    private val accessibility = object : TestRule {
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
                try {
                    AppLanguage.set("ko-KR")
                    val services = oldServices.takeUnless { it == "null" || it.isEmpty() }
                        ?.split(':').orEmpty().toMutableSet()
                    services.add("com.google.android.marvin.talkback/.TalkBackService")
                    device.executeShellCommand("settings put secure enabled_accessibility_services ${services.joinToString(":")}")
                    device.executeShellCommand("settings put secure accessibility_enabled 1")
                    val manager = requireNotNull(instrumentation.targetContext.getSystemService(AccessibilityManager::class.java))
                    val deadline = System.nanoTime() + 20_000_000_000L
                    while (manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                            .none { it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback" }) {
                        check(System.nanoTime() < deadline) { "TalkBack did not start" }
                        Thread.sleep(100)
                    }
                    base.evaluate()
                } finally {
                    restore("enabled_accessibility_services", oldServices)
                    restore("accessibility_enabled", oldEnabled)
                    AppLanguage.set(oldLanguage)
                    config.setUiAutomationFlags(oldFlags)
                    instrumentation.getUiAutomation(oldFlags)
                }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(Timeout.seconds(180)).around(accessibility).around(compose)

    @Test fun statusHelpQuestionsAndModelFollowTheReadingOrder() {
        val read = mutableListOf<String>()
        fun focus(text: String) {
            val node = automation.rootInActiveWindow.findAccessibilityNodeInfosByText(text)
                .firstOrNull { it.isClickable }
                ?: error("No focusable help entry: $text")
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) || node.isAccessibilityFocused)
        }
        fun next(): String {
            val event = automation.executeAndWaitForEvent({
                assertTrue(device.swipe(device.displayWidth / 2, device.displayHeight / 2,
                    device.displayWidth * 3 / 4, device.displayHeight / 2, 15))
            }, { it.eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED &&
                it.packageName?.toString() == compose.activity.packageName }, 10_000)
            return (event.source?.text?.toString() ?: event.text.joinToString(" ")).also { read.add(it) }
        }
        try {
            compose.waitForIdle()
            automation.waitForIdle(500, 10_000)
            val title = str(R.string.midi_help_title)
            val status = automation.rootInActiveWindow.findAccessibilityNodeInfosByText(
                str(R.string.midi_status_none)).firstOrNull()
                ?: automation.rootInActiveWindow.findAccessibilityNodeInfosByText(str(R.string.midi_status_disconnected)).first()
            assertTrue(status.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) || status.isAccessibilityFocused)
            assertTrue("Status must be followed by help: $read", next().contains(title))
            compose.onNodeWithText(title).performClick()
            compose.waitForIdle()
            automation.waitForIdle(500, 10_000)
            focus(title)
            for (id in listOf(R.string.midi_help_title, R.string.midi_help_close,
                R.string.midi_help_find, R.string.midi_help_find_body, R.string.midi_help_press,
                R.string.midi_help_lights, R.string.midi_help_wrong, R.string.midi_help_chain,
                R.string.midi_help_firmware, R.string.midi_help_crash, R.string.midi_help_web_link)) {
                assertTrue("Unexpected TalkBack order: $read", next().contains(str(id)))
            }
            val models = listOf(R.string.midi_lp_s, R.string.midi_lp_mk2, R.string.midi_lp_pro,
                R.string.midi_lp_pro_cfw, R.string.midi_lp_x, R.string.midi_lp_mini_mk3,
                R.string.midi_lp_mk3, R.string.midi_midi_fighter, R.string.midi_matrix,
                R.string.midi_master_keyboard, R.string.midi_lp_core_fw).map(::str)
            assertTrue("The selected model must follow help: $read", next() in models)
        } finally {
            assertTrue(TestCaptures.save(device, "midi/talkback-order-ko.txt") {
                it.writeText("TalkBack 초점 이동 기록\n\n" + read.joinToString("\n\n")); it.length() > 0
            })
        }
    }
}
