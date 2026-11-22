package com.kimjisub.launchpad

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.kimjisub.launchpad.activity.MidiSelectActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** The same test can run unchanged on the previous screen to capture its missing guidance. */
class MidiConnectionGuidanceTest {
    private val compose = createAndroidComposeRule<MidiSelectActivity>()
    private var korean = false
    private val language = object : TestRule {
        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                val previous = AppLanguage.get()
                korean = description.methodName.startsWith("korean")
                AppLanguage.set(if (korean) "ko-KR" else "en")
                try { base.evaluate() } finally { AppLanguage.set(previous) }
            }
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(language).around(compose)

    @Test fun englishConnectionGuidance() = checkGuidance()
    @Test fun koreanConnectionGuidance() = checkGuidance()

    private fun checkGuidance() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val language = if (korean) "ko" else "en"
        compose.waitForIdle()
        assertTrue(TestCaptures.screenshot(device, "guidance/selection-$language.png"))
        compose.onNodeWithText(if (korean) "연결이 안 되나요?" else "Not connecting?").performClick()
        compose.onNodeWithText(if (korean) "도움말 닫기" else "Close help").assertIsDisplayed()
        compose.onNodeWithText(if (korean) "런치패드를 찾지 못해요" else "The app can't find the Launchpad").assertIsDisplayed()
        assertTrue(TestCaptures.screenshot(device, "guidance/help-$language.png"))
    }
}
