package com.kimjisub.launchpad

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import android.app.Instrumentation
import android.content.Intent
import android.content.ActivityNotFoundException
import android.hardware.usb.UsbManager
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import android.view.ViewGroup
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.activity.MidiSelectActivity
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.driver.LaunchpadMiniMK3
import com.kimjisub.launchpad.midi.driver.LaunchpadS
import com.kimjisub.launchpad.midi.driver.DriverRef
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** Run at 2400 x 1080, density 420, font scales 1 and 2, in English and Korean. */
class MidiSelectScreenTest {
    private val compose = createAndroidComposeRule<MidiSelectActivity>()
    // Choose the locale before launching the Activity. Changing it mid-test can leave
    // Espresso waiting forever for the old Activity's locale/configuration callback.
    private val language = object : TestRule {
        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                val korean = description.methodName.startsWith("korean")
                val original = AppLanguage.get()
                AppLanguage.set(if (korean) "ko-KR" else "en")
                try { base.evaluate() } finally { AppLanguage.set(original) }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(Timeout.seconds(180)).around(language).around(compose)
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun str(id: Int) = compose.activity.getString(id)
    private fun button(id: Int) = compose.onNode(hasText(str(id)) and hasClickAction())
    private fun capture(name: String) {
        compose.waitForIdle()
        assertTrue(TestCaptures.screenshot(device, "midi/midi-$name.png"))
    }

    @Test fun helpReplacesModelsAndFirstBackClosesOnlyHelp() {
        compose.onNodeWithText("Not connecting?").performClick()
        compose.onNodeWithText("Close help").assertIsDisplayed()
        compose.onNodeWithText("The app can't find the Launchpad").assertIsDisplayed()
        device.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Close help").assertDoesNotExist()
        assertTrue(!compose.activity.isFinishing)
        button(android.R.string.ok).assertIsDisplayed()
    }

    @Test fun helpKeepsExpandedQuestionAcrossRecreationAndBackground() {
        button(R.string.midi_help_title).performClick()
        button(R.string.midi_help_firmware).performScrollTo().performClick()
        button(R.string.midi_help_firmware_link).performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        button(R.string.midi_help_firmware_link).performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        button(R.string.midi_help_firmware_link).performScrollTo().assertIsDisplayed()
        capture("help-restored-en")
    }

    @Test fun decodedPadUpdatesStatusAndManualChoiceDoesNotClaimFirmware() {
        val pad = LaunchpadS()
        val output = object : DriverRef.OnSendSignalListener {
            override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) = Unit
            override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) = Unit
        }
        var transport: AutoCloseable? = null
        compose.runOnIdle {
            val activity = compose.activity
            MidiConnection.initConnection(Intent(), activity.getSystemService(UsbManager::class.java), activity)
            transport = MidiConnection.attachTransport("Virtual Mini", pad, output)
        }
        try {
            compose.waitUntil(5000) {
                compose.onAllNodes(hasText("Connected: Virtual Mini", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(str(R.string.midi_source_auto)).performScrollTo().assertIsDisplayed()
            compose.runOnIdle { pad.getSignal(15, 0xf8, 0, 0) }
            compose.onNodeWithText(str(R.string.midi_status_input)).assertDoesNotExist()
            compose.runOnIdle { pad.getSignal(9, 0x90, 0, 127) }
            compose.waitUntil(5000) {
                compose.onAllNodes(hasText(str(R.string.midi_status_input), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            capture("input-en")
            button(R.string.midi_lp_pro_cfw).performScrollTo().performClick()
            compose.waitUntil(5000) {
                compose.onAllNodes(hasText(str(R.string.midi_source_manual))).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(str(R.string.midi_source_manual)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Custom firmware: Launchpad Open").assertDoesNotExist()
            compose.runOnIdle { transport!!.close() }
            transport = null
            compose.waitUntil(5000) {
                compose.onAllNodes(hasText(str(R.string.midi_status_disconnected), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle { transport = MidiConnection.attachTransport("Virtual Mini", pad, output) }
            compose.waitUntil(5000) {
                compose.onAllNodes(hasText(str(R.string.midi_source_auto))).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(!MidiConnection.connectionStatus().manuallyChosen)
            compose.onNodeWithText(str(R.string.midi_status_input)).assertDoesNotExist()
        } finally { compose.runOnIdle { transport?.close() } }
    }

    @Test fun failedGuideLaunchKeepsTheExpandedHelp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var attempted = false
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action == Intent.ACTION_VIEW && intent.dataString == "https://unipad.io/docs/guide#custom-firmware") {
                    attempted = true
                    throw ActivityNotFoundException("No browser for this test")
                }
                return null
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            button(R.string.midi_help_title).performClick()
            button(R.string.midi_help_firmware).performScrollTo().performClick()
            button(R.string.midi_help_firmware_link).performScrollTo().performClick()
            assertTrue("The approved firmware address was not opened", attempted)
            button(R.string.midi_help_firmware_link).assertIsDisplayed()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            button(R.string.midi_help_firmware_link).assertIsDisplayed()
            capture("link-failed-en")
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun koreanHelpShowsApprovedCopy() {
        button(R.string.midi_help_title).performClick()
        compose.onNodeWithText("런치패드를 찾지 못해요").assertIsDisplayed()
        capture("help-ko")
        button(R.string.midi_help_firmware).performScrollTo().performClick()
        button(R.string.midi_help_firmware_link).performScrollTo().assertIsDisplayed()
        capture("firmware-ko")
    }

    @Test fun koreanSmallWindowAndLargeTextKeepEveryAnswerReadable() {
        val originalScale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("settings put system font_scale 2.0")
            compose.waitUntil(10000) { compose.activity.resources.configuration.fontScale >= 1.99f }
            compose.activityRule.scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                activity.window.setLayout((640 * density).toInt(), (360 * density).toInt())
            }
            compose.waitForIdle()
            capture("small-large-selection-ko")
            button(R.string.midi_help_title).performScrollTo().performClick()
            val questions = listOf(
                R.string.midi_help_find to R.string.midi_help_find_body,
                R.string.midi_help_press to R.string.midi_help_press_body,
                R.string.midi_help_lights to R.string.midi_help_lights_body,
                R.string.midi_help_wrong to R.string.midi_help_wrong_body,
                R.string.midi_help_chain to R.string.midi_help_chain_body,
                R.string.midi_help_firmware to R.string.midi_help_firmware_body,
                R.string.midi_help_crash to R.string.midi_help_crash_body,
            )
            questions.forEachIndexed { index, (question, body) ->
                if (compose.onAllNodes(hasText(str(body)), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) {
                    button(question).performScrollTo().performClick()
                }
                val node = compose.onNodeWithText(str(body), useUnmergedTree = true).performScrollTo()
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertTrue("Help text is clipped: $index", !layout.hasVisualOverflow)
                val text = str(body)
                for (line in 0 until layout.lineCount) {
                    val end = layout.getLineEnd(line, visibleEnd = true)
                    assertTrue("Korean word splits in answer $index at $end",
                        end == text.length || text[end].isWhitespace() || (end > 0 && text[end - 1].isWhitespace()))
                }
                capture("small-large-help-ko-$index")
            }
            button(android.R.string.ok).assertIsDisplayed()
        } finally {
            device.executeShellCommand("settings put system font_scale $originalScale")
            compose.activityRule.scenario.onActivity { it.window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        }
    }

    @Test fun confirmationStaysVisibleWithoutHelp() {
        button(android.R.string.ok).assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        compose.onNodeWithText("Connection and light help").assertDoesNotExist()
        compose.onNodeWithText("연결·불빛 도움말").assertDoesNotExist()
        capture("selection-buttons")
        val activity = compose.activity
        button(android.R.string.ok).performClick()
        compose.waitForIdle()
        assertTrue(activity.isFinishing)
    }

    @Test fun selectedModelSurvivesRecreationAndBackground() {
        val original = MidiConnection.driver
        try {
            button(R.string.midi_lp_mini_mk3).performScrollTo().performClick()
            val chosen = MidiConnection.driver
            assertTrue(chosen is LaunchpadMiniMK3)
            compose.activityRule.scenario.recreate()
            assertSame(chosen, MidiConnection.driver)
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            assertSame(chosen, MidiConnection.driver)
            button(R.string.midi_lp_mini_mk3).performScrollTo().performClick()
            assertSame("reselecting must keep the driver", chosen, MidiConnection.driver)
            capture("selection-restored")
        } finally { compose.runOnIdle { MidiConnection.driver = original } }
    }

    @Test fun koreanSettingsAreReadable() {
        for (label in listOf("두 장치 사용", "둘째 장치 좌우 반전", "좌우 바꾸기")) {
            repeat(6) {
                val node = device.findObject(By.text(label))
                if (node == null || node.visibleBounds.isEmpty) {
                    device.findObjects(By.scrollable(true)).minBy { it.visibleBounds.left }
                        .scroll(Direction.DOWN, 0.6f)
                }
            }
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        button(android.R.string.ok).assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        capture("selection-korean-settings")
    }
}
