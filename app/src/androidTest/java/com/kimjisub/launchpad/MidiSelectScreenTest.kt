package com.kimjisub.launchpad

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.activity.MidiSelectActivity
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.driver.LaunchpadMiniMK3
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
                val korean = description.methodName == "koreanSettingsAreReadable"
                val original = AppLanguage.get()
                if (korean) AppLanguage.set("ko-KR")
                try { base.evaluate() } finally { if (korean) AppLanguage.set(original) }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(Timeout.seconds(180)).around(language).around(compose)
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun str(id: Int) = compose.activity.getString(id)
    private fun button(id: Int) = compose.onNode(hasText(str(id)) and hasClickAction())
    private fun capture(name: String) {
        compose.waitForIdle()
        device.executeShellCommand("screencap -p /sdcard/Download/midi-$name.png")
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
