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
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.MidiSelectActivity
import com.kimjisub.launchpad.activity.MidiSelectScreen
import com.kimjisub.launchpad.midi.MidiConnectionStatus
import com.kimjisub.launchpad.ui.theme.UniPadTheme
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.driver.LaunchpadMiniMK3
import com.kimjisub.launchpad.midi.driver.LaunchpadS
import com.kimjisub.launchpad.midi.driver.LaunchpadPROCFW
import com.kimjisub.launchpad.midi.driver.MasterKeyboard
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

    @Test fun decodedPadUpdatesStatusAndAPickOnAVirtualPadClaimsNothing() {
        val pad = LaunchpadS()
        val output = object : DriverRef.OnSendSignalListener {
            override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) = Unit
            override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) = Unit
        }
        var transport: AutoCloseable? = null
        compose.runOnIdle {
            val activity = compose.activity
            MidiConnection.initConnection(Intent(), requireNotNull(activity.getSystemService(UsbManager::class.java)), activity)
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
            // A virtual connection keeps its own layout: the pick neither applies nor claims firmware.
            button(R.string.midi_lp_pro_cfw).performScrollTo().performClick()
            compose.waitForIdle()
            compose.onNodeWithText(str(R.string.midi_source_auto)).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(str(R.string.midi_source_manual)).assertDoesNotExist()
            compose.onNodeWithText("Custom firmware: Launchpad Open").assertDoesNotExist()
            compose.runOnIdle { transport!!.close() }
            transport = null
            // Wait for the status panel itself: the top banner also says "Disconnected", and the source
            // stays "Recognised automatically" throughout, so neither proves the screen read the change.
            compose.waitUntil(5000) {
                compose.onAllNodes(hasText(str(R.string.midi_status_disconnected_detail), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.runOnIdle { transport = MidiConnection.attachTransport("Virtual Mini", pad, output) }
            compose.waitUntil(5000) {
                compose.onAllNodes(hasText(str(R.string.midi_status_wait), substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(str(R.string.midi_source_auto)).performScrollTo().assertIsDisplayed()
            assertTrue(!MidiConnection.connectionStatus().manuallyChosen)
            compose.onNodeWithText(str(R.string.midi_status_input)).assertDoesNotExist()
        } finally { compose.runOnIdle { transport?.close() } }
    }

    @Test fun koreanUnknownDeviceAndDetectedFirmwareShowObservedFacts() {
        val output = object : DriverRef.OnSendSignalListener {
            override fun onSend(cmd: Byte, sig: Byte, note: Byte, velocity: Byte) = Unit
            override fun onSendRaw(messages: List<ByteArray>, cableNumber: Int) = Unit
        }
        var transport: AutoCloseable? = null
        fun attach(name: String, driver: DriverRef) {
            compose.runOnIdle {
                transport?.close()
                transport = MidiConnection.attachTransport(name, driver, output)
            }
            compose.waitUntil(5000) {
                compose.onAllNodes(hasText("연결됨: $name", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        }
        compose.runOnIdle {
            val activity = compose.activity
            MidiConnection.initConnection(Intent(), requireNotNull(activity.getSystemService(UsbManager::class.java)), activity)
        }
        try {
            attach("USB Piano 42", MasterKeyboard())
            compose.onNodeWithText(str(R.string.midi_status_unknown)).assertIsDisplayed()
            capture("unknown-device-ko")
            button(R.string.midi_help_title).performClick()
            compose.onNodeWithText(str(R.string.midi_help_press_body)).assertExists()
            device.pressBack()
            compose.waitForIdle()
            attach("Launchpad Open", LaunchpadPROCFW())
            compose.onNodeWithText(str(R.string.midi_status_firmware).format("Launchpad Open")).assertIsDisplayed()
            compose.onNodeWithText(str(R.string.midi_status_unknown)).assertDoesNotExist()
            capture("detected-firmware-ko")
            button(R.string.midi_help_title).performClick()
            button(R.string.midi_help_firmware_link).performScrollTo().assertIsDisplayed()
            capture("detected-firmware-help-ko")
        } finally { compose.runOnIdle { transport?.close() } }
    }

    @Test fun browserReturnKeepsTheExpandedHelp() {
        val page = "https://unipad.io/docs/guide#custom-firmware"
        val appPackage = compose.activity.packageName
        val browsers = device.executeShellCommand("cmd package query-activities --brief -a android.intent.action.VIEW -d $page")
            .lines().map { it.trim().substringBefore('/') }
            .filter { it.contains('.') && !it.contains(' ') }.distinct()
        assertTrue("The emulator must have a browser", browsers.isNotEmpty())
        fun resumed(packages: List<String>) = device.executeShellCommand("dumpsys activity activities")
            .lineSequence().filter { it.contains("topResumedActivity=") || it.contains("mResumedActivity:") }
            .any { line -> packages.any { line.contains(" $it/") } }
        fun awaitResumed(packages: List<String>): Boolean {
            val end = System.nanoTime() + 20_000_000_000L
            while (System.nanoTime() < end) {
                if (resumed(packages)) return true
                Thread.sleep(100)
            }
            return false
        }
        try {
            browsers.forEach { device.executeShellCommand("am force-stop $it") }
            button(R.string.midi_help_title).performClick()
            button(R.string.midi_help_firmware).performScrollTo().performClick()
            button(R.string.midi_help_firmware_link).performScrollTo().assertIsDisplayed()
            // A device click avoids waiting for Compose frames in the background activity.
            requireNotNull(device.findObject(By.text(str(R.string.midi_help_firmware_link)))).click()
            assertTrue("The guide did not open in a browser", awaitResumed(browsers))
            repeat(4) {
                if (!resumed(listOf(appPackage))) {
                    device.pressBack()
                    device.wait(Until.hasObject(By.pkg(appPackage)), 2000)
                }
            }
            assertTrue("Back did not return to UniPad", awaitResumed(listOf(appPackage)))
            button(R.string.midi_help_firmware_link).assertIsDisplayed()
            capture("browser-return-en")
        } finally {
            browsers.forEach { device.executeShellCommand("am force-stop $it") }
        }
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

    @Test fun unknownDeviceChosenByHandIsShownAsTheChoice() = checkUnknownDeviceChosenByHand("en")

    @Test fun koreanUnknownDeviceChosenByHandIsShownAsTheChoice() = checkUnknownDeviceChosenByHand("ko")

    /**
     * An emulator cannot attach a USB device, and a virtual connection keeps its own layout, so
     * this draws the real screen with the statuses a USB connection reports at each step
     * (MidiConnectionLifecycleTest drives those through the USB path).
     */
    private fun checkUnknownDeviceChosenByHand(language: String) {
        val unknown = MidiConnectionStatus(7, "USB Piano 42", MidiConnectionStatus.Phase.WAITING, modelUnknown = true)
        val keyboard = 9
        val status = mutableStateOf(unknown)
        val selected = mutableIntStateOf(keyboard)
        fun host() = compose.runOnUiThread {
            compose.activity.setContent {
                UniPadTheme {
                    MidiSelectScreen(
                        status = status.value, onAllow = {}, onBrowse = {}, selectedIndex = selected.intValue,
                        dualPadModeEnabled = false, reflectedModeEnabled = false, reflectedSwapSides = false,
                        connectedSessions = emptyList(), selectedSessionId = null, onSessionTargetSelect = {},
                        onDeviceSelect = { index ->
                            selected.intValue = index
                            status.value = status.value.copy(manuallyChosen = true, modelUnknown = index == keyboard)
                        },
                        onDualPadModeChange = {}, onReflectedModeChange = {}, onReflectedSwapSidesChange = {}, onClose = {},
                    )
                }
            }
        }
        fun assertKeyboardState() {
            compose.onNodeWithText(str(R.string.midi_status_unknown)).assertIsDisplayed()
            compose.onNodeWithText(str(R.string.midi_status_unknown_detail)).assertIsDisplayed()
            compose.onNodeWithText(str(R.string.midi_source_auto)).assertDoesNotExist()
            compose.onNodeWithText(str(R.string.midi_source_manual)).assertDoesNotExist()
        }
        host()
        compose.onNodeWithText(str(R.string.midi_status_connected).format("USB Piano 42")).assertIsDisplayed()
        assertKeyboardState()
        capture("unknown-$language")

        button(R.string.midi_lp_mini_mk3).performScrollTo().performClick()
        fun assertChosenState() {
            compose.onNodeWithText(str(R.string.midi_status_connected).format("USB Piano 42")).assertIsDisplayed()
            compose.onNodeWithText(str(R.string.midi_status_wait)).assertIsDisplayed()
            compose.onNodeWithText(str(R.string.midi_status_unknown)).assertDoesNotExist()
            compose.onNodeWithText(str(R.string.midi_status_unknown_detail)).assertDoesNotExist()
            compose.onNodeWithText(str(R.string.midi_status_firmware).format("CoreFW")).assertDoesNotExist()
            compose.onNodeWithText(str(R.string.midi_source_manual)).assertIsDisplayed()
        }
        assertChosenState()
        capture("unknown-chosen-$language")

        // Opening the screen again reads the same connection.
        compose.activityRule.scenario.recreate()
        host()
        assertChosenState()
        capture("unknown-chosen-reopened-$language")

        // Picking the keyboard again returns to it for this connection.
        button(R.string.midi_master_keyboard).performScrollTo().performClick()
        assertKeyboardState()

        // Plugging it in again starts from detection.
        compose.runOnIdle { status.value = unknown }
        assertKeyboardState()
        capture("unknown-replugged-$language")
    }
}
