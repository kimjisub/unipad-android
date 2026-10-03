package com.kimjisub.launchpad

import android.content.Context
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.MidiSelectActivity
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.midi.driver.LaunchpadMiniMK3
import org.junit.Assert.assertEquals
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
class MidiConnectionHelpTest {
    private val compose = createAndroidComposeRule<MidiSelectActivity>()
    // Choose the locale before launching the Activity. Changing it mid-test can leave
    // Espresso waiting forever for the old Activity's locale/configuration callback.
    private val language = object : TestRule {
        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                val korean = description.methodName == "koreanLabelsAndHelpAreReadable"
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val original = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    instrumentation.targetContext.getSystemService(LocaleManager::class.java)
                        .applicationLocales.toLanguageTags()
                } else AppCompatDelegate.getApplicationLocales().toLanguageTags()
                fun setLocales(tags: String) = instrumentation.runOnMainSync {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        instrumentation.targetContext.getSystemService(LocaleManager::class.java)
                            .applicationLocales = LocaleList.forLanguageTags(tags)
                    } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
                }
                if (korean) setLocales("ko-KR")
                try { base.evaluate() } finally { if (korean) setLocales(original) }
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

    @Test fun requiredButtonsStayVisible() {
        button(android.R.string.ok).assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        button(R.string.midi_help_title).assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        capture("buttons")
        button(R.string.midi_help_title).performClick()
        button(R.string.midi_help_close).assertIsDisplayed().assertHeightIsAtLeast(40.dp)
        capture("help")
        compose.onNodeWithText(str(R.string.midi_help_crash_body)).performScrollTo().assertIsDisplayed()
        button(R.string.midi_help_close).assertIsDisplayed().performClick()
        val activity = compose.activity
        button(android.R.string.ok).performClick()
        compose.waitForIdle()
        assertEquals(true, activity.isFinishing)
    }

    @Test fun readingHelpDoesNotChangeDriversSettingsOrSendCommands() {
        val driver = MidiConnection.driver
        val sessions = MidiConnection.connectedSessions
        val flags = listOf(MidiConnection.dualPadModeEnabled, MidiConnection.reflectedModeEnabled, MidiConnection.reflectedSwapSides)
        val prefs = compose.activity.getSharedPreferences("midi_connection_prefs", Context.MODE_PRIVATE)
        val saved = prefs.all.toMap()
        var commands = 0
        driver.setOnSendSignalListener(object : DriverRef.OnSendSignalListener {
            override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) { commands++ }
            override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) { commands++ }
        })
        try {
            button(R.string.midi_help_title).performClick()
            compose.onNodeWithText(str(R.string.midi_help_android)).performScrollTo().assertIsDisplayed()
            device.pressBack()
            compose.onNodeWithText(str(R.string.midi_help_close)).assertDoesNotExist()
            button(R.string.midi_help_title).assertIsDisplayed()
            button(R.string.midi_help_title).performClick()
            button(R.string.midi_help_close).assertIsDisplayed()
            device.waitForIdle()
            device.pressKeyCode(android.view.KeyEvent.KEYCODE_ESCAPE)
            compose.onNodeWithText(str(R.string.midi_help_close)).assertDoesNotExist()
            button(R.string.midi_help_title).assertIsDisplayed()
            assertSame(driver, MidiConnection.driver)
            assertEquals(sessions, MidiConnection.connectedSessions)
            assertEquals(flags, listOf(MidiConnection.dualPadModeEnabled, MidiConnection.reflectedModeEnabled, MidiConnection.reflectedSwapSides))
            assertEquals(saved, prefs.all)
            assertEquals(0, commands)
        } finally { driver.setOnSendSignalListener(null) }
    }

    @Test fun miniHelpRequiresExplicitSelectionAndSurvivesRecreationAndBackground() {
        val original = MidiConnection.driver
        try {
            compose.runOnIdle { MidiConnection.driver = LaunchpadMiniMK3() }
            compose.activityRule.scenario.recreate()
            button(R.string.midi_help_title).performClick()
            compose.onNodeWithText(str(R.string.midi_help_mini)).assertDoesNotExist()
            compose.onNodeWithText(str(R.string.midi_help_manufacturer)).assertDoesNotExist()
            button(R.string.midi_help_close).performClick()
            button(R.string.midi_lp_mini_mk3).performScrollTo().performClick()
            button(R.string.midi_help_title).performClick()
            compose.onNodeWithText(str(R.string.midi_help_mini)).performScrollTo().assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            button(R.string.midi_help_close).assertIsDisplayed()
            compose.onNodeWithText(str(R.string.midi_help_mini)).performScrollTo().assertIsDisplayed()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            button(R.string.midi_help_close).assertIsDisplayed()
            compose.onNodeWithText(str(R.string.midi_help_manufacturer)).performScrollTo().assertIsDisplayed()
            capture("mini")
            device.pressBack()
            compose.onNodeWithText(str(R.string.midi_help_close)).assertDoesNotExist()
            button(R.string.midi_help_title).assertIsDisplayed()
        } finally { compose.runOnIdle { MidiConnection.driver = original } }
    }

    @Test fun koreanLabelsAndHelpAreReadable() {
        compose.onNodeWithText("연결·불빛 도움말").assertIsDisplayed()
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
        capture("korean-buttons")
        button(R.string.midi_help_title).performClick()
        compose.onNodeWithText("도움말 닫기").assertIsDisplayed()
        capture("korean-help")
        compose.onNodeWithText(str(R.string.midi_help_crash_body)).performScrollTo().assertIsDisplayed()
        button(R.string.midi_help_close).performClick()
    }

    @Test fun manufacturerLinkReturnsToOpenHelp() {
        val original = MidiConnection.driver
        val chromeWasDisabled = device.executeShellCommand("pm list packages -d com.android.chrome")
            .contains("package:com.android.chrome")
        try {
            if (chromeWasDisabled) device.executeShellCommand("pm enable --user 0 com.android.chrome")
            button(R.string.midi_lp_mini_mk3).performScrollTo().performClick()
            button(R.string.midi_help_title).performClick()
            button(R.string.midi_help_manufacturer).performScrollTo().performClick()
            assertTrue("No external activity opened", device.wait(Until.gone(By.pkg(compose.activity.packageName)), 10_000L))
            device.pressBack()
            val returned = By.pkg(compose.activity.packageName).text(str(R.string.midi_help_close))
            if (!device.wait(Until.hasObject(returned), 10_000L)) device.pressBack()
            assertTrue("Did not return to the open help", device.wait(Until.hasObject(returned), 10_000L))
            compose.waitForIdle()
            button(R.string.midi_help_close).assertIsDisplayed()
            compose.onNodeWithText(str(R.string.midi_help_mini)).performScrollTo().assertIsDisplayed()
        } finally {
            if (chromeWasDisabled) device.executeShellCommand("pm disable-user --user 0 com.android.chrome")
            compose.runOnIdle { MidiConnection.driver = original }
        }
    }

    @Test fun missingBrowserKeepsHelpOpen() {
        val original = MidiConnection.driver
        val handlers = device.executeShellCommand(
            "cmd package query-activities --brief -a android.intent.action.VIEW -d https://userguides.novationmusic.com"
        ).lines().mapNotNull { Regex("""^\s+([\w.]+)/\S+$""").find(it)?.groupValues?.get(1) }.distinct()
        try {
            handlers.forEach { device.executeShellCommand("pm disable-user --user 0 $it") }
            button(R.string.midi_lp_mini_mk3).performScrollTo().performClick()
            button(R.string.midi_help_title).performClick()
            button(R.string.midi_help_manufacturer).performScrollTo().performClick()
            compose.onNodeWithText(str(R.string.midi_help_link_failed)).performScrollTo().assertIsDisplayed()
            button(R.string.midi_help_close).assertIsDisplayed()
            capture("no-browser")
        } finally {
            handlers.forEach { device.executeShellCommand("pm enable --user 0 $it") }
            compose.runOnIdle { MidiConnection.driver = original }
        }
    }
}
