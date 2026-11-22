package com.kimjisub.launchpad

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.kimjisub.launchpad.activity.MidiSelectActivity
import com.kimjisub.launchpad.activity.VirtualMidiPicker
import com.kimjisub.launchpad.midi.AndroidVirtualMidiBackend
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.VirtualMidiConnection.Failure
import com.kimjisub.launchpad.midi.VirtualMidiConnection.State
import com.kimjisub.launchpad.midi.VirtualMidiFixture
import com.kimjisub.launchpad.midi.controller.MidiController
import java.util.Collections
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * The "apps on this phone" tab against the test-only MIDI services running in their own processes,
 * through Android's MIDI service: the list, the picks, opening only the chosen ports, failures and
 * a service that stops. English, except tests whose names start with "korean".
 */
class MidiAppConnectionTest {
	private val compose = createAndroidComposeRule<MidiSelectActivity>()
	private val language = object : TestRule {
		override fun apply(base: Statement, description: Description) = object : Statement() {
			override fun evaluate() {
				val original = AppLanguage.get()
				AppLanguage.set(if (description.methodName.startsWith("korean")) "ko-KR" else "en")
				try { base.evaluate() } finally { AppLanguage.set(original) }
			}
		}
	}
	@get:Rule val rules: RuleChain = RuleChain.outerRule(Timeout.seconds(180)).around(language).around(compose)

	private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
	private val fixture = VirtualMidiFixture(context)
	private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
	// The Compose rule runs the main dispatcher on its own clock: main-thread work goes through it.
	private val picker get() = compose.runOnIdle { VirtualMidiPicker.shared(context) }
	private fun ui() = picker.ui.value
	private fun str(id: Int, vararg args: Any) = compose.activity.getString(id, *args)
	private fun button(id: Int) = compose.onNode(hasText(str(id)) and hasClickAction())
	private fun text(value: String) = compose.onNodeWithText(value, useUnmergedTree = true)
	private fun read() = fixture.call("read")
	/** Scrolls to the node when it sits in a scrolling column; the wide layout keeps the main button fixed. */
	private fun SemanticsNodeInteraction.reach(): SemanticsNodeInteraction =
		try { performScrollTo() } catch (_: AssertionError) { this }
	private fun capture(name: String) {
		compose.waitForIdle()
		assertTrue(TestCaptures.screenshot(device, "midi/apps-$name.png"))
	}
	private fun awaitUi(message: String, condition: (VirtualMidiPicker.Ui) -> Boolean) {
		try { compose.waitUntil(15_000) { condition(ui()) } }
		catch (error: Throwable) { throw AssertionError("$message: ${ui()}", error) }
		compose.waitForIdle()
	}

	@After fun disconnect() {
		val current = picker
		compose.runOnIdle { current.cancelSearch(); current.cancelConnect(); current.disconnect() }
		compose.waitUntil(15_000) { MidiConnection.transportGate.acquireUsb()?.let { it.close(); true } == true }
		compose.runOnIdle { MidiConnection.controller = null }
	}

	private fun openAppsTab() {
		compose.onNodeWithTag("midiTabApps").performClick()
		awaitUi("The list was not read") { !it.scanning && it.devices.isNotEmpty() }
	}

	/** The row of the selected test service; its twin from the same app is told apart by a number. */
	private fun pickFixture() {
		val id = fixture.info(".MidiFixtureService").id
		val twins = ui().devices.filter { it.name == "Virtual MIDI fixture" }
		assertEquals("Both test services share a name and must be numbered", 2, twins.size)
		text("Virtual MIDI fixture · #${twins.indexOfFirst { it.id == id } + 1}").reach().performClick()
		awaitUi("The fixture was not selected") { it.selected?.id == id }
	}

	private fun connectFixture() {
		openAppsTab()
		pickFixture()
		text("Port 2 · Pads one").reach().performClick()
		text("Port 1 · Lights zero").reach().performClick()
		button(R.string.midi_apps_connect).reach().performClick()
		awaitUi("The fixture did not connect") { it.connection == State.Connected(lights = true) }
	}

	@Test fun opensOnUsbAndConnectsOnlyThePortsChosenInTheAppsTab() {
		button(android.R.string.ok).assertIsDisplayed()
		compose.onNodeWithTag("midiTabUsb").assertIsSelected().assertHeightIsAtLeast(48.dp)
		compose.onNodeWithTag("midiTabApps").assertHeightIsAtLeast(48.dp)
		assertEquals("The screen must not open a service before the tab is chosen", 0, read().getInt("subscribers1"))
		openAppsTab()
		capture("list")
		assertTrue("Opening the tab must not connect", ui().connection == State.Idle && MidiConnection.connectedDevice == null)
		pickFixture()
		text(str(R.string.midi_apps_input_direction)).assertIsDisplayed()
		text(str(R.string.midi_apps_lights_direction)).reach().assertIsDisplayed()
		button(R.string.midi_apps_connect).reach().assertIsNotEnabled()
		text(str(R.string.midi_apps_model_recommended, "Matrix")).reach().assertIsDisplayed()
		text("Port 2 · Pads one").reach().performClick()
		button(R.string.midi_apps_connect).assertIsNotEnabled()
		text("Port 1 · Lights zero").reach().performClick()
		compose.onNode(hasText("Port 1 · Lights zero") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
			.assertIsSelected().assertHeightIsAtLeast(48.dp)
		capture("settings")
		button(R.string.midi_apps_connect).assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
		awaitUi("The fixture did not connect") { it.connection == State.Connected(lights = true) }
		text(str(R.string.midi_apps_connected, "Virtual MIDI fixture")).reach().assertIsDisplayed()
		text(str(R.string.midi_apps_connected_badge)).reach().assertIsDisplayed()
		capture("connected")

		fixture.await("The chosen play port was not subscribed") { read().getInt("subscribers1") == 1 }
		assertEquals("A port not chosen must not be subscribed", 0, read().getInt("subscribers0"))
		assertTrue(read().getBoolean("open0"))
		assertFalse("A light port not chosen must not be opened", read().getBoolean("open1"))
		val other = context.contentResolver.call(Uri.parse("content://com.kimjisub.launchpad.dev.test.othermidi"), "read", "unselected", Bundle())!!
		assertTrue("The twin service must never be opened", other.getBoolean("absent"))

		val pads = Collections.synchronizedList(mutableListOf<List<Int>>())
		compose.runOnIdle {
			MidiConnection.controller = object : MidiController() {
				override fun onAttach() { }
				override fun onDetach() { }
				override fun onPadTouch(x: Int, y: Int, upDown: Boolean, velocity: Int) { pads.add(listOf(x, y, if (upDown) 1 else 0)) }
				override fun onChainTouch(c: Int, upDown: Boolean) { }
				override fun onFunctionKeyTouch(f: Int, upDown: Boolean) { }
				override fun onUnknownEvent(cmd: Int, sig: Int, note: Int, velocity: Int) { }
			}
		}
		fixture.send(byteArrayOf(0x91.toByte(), 36, 100), port = 0)
		fixture.send(0x91, 36, 100, 0x81, 36, 64)
		compose.waitUntil(10_000) { pads.size == 2 }
		assertEquals("Only the chosen play port reaches UniPad", listOf(listOf(7, 0, 1), listOf(7, 0, 0)), pads.toList())
		fixture.call("clear")
		compose.runOnIdle { MidiConnection.driver.sendPadLed(7, 0, 100) }
		fixture.await("The light did not reach the chosen light port") { read().getByteArray("lights0")!!.size >= 3 }
		assertEquals(listOf<Byte>(0x91.toByte(), 36, 100), read().getByteArray("lights0")!!.take(3))
		assertTrue(read().getByteArray("lights1")!!.isEmpty())

		compose.onNodeWithTag("midiTabUsb").performClick()
		text(str(R.string.midi_usb_virtual_active, "Virtual MIDI fixture")).reach().assertIsDisplayed()
		capture("usb-tab-while-app-connected")
		assertTrue("Changing tabs keeps the connection", ui().connected)
		compose.onNodeWithTag("midiTabApps").performClick()
		compose.waitForIdle()
		button(R.string.midi_apps_disconnect).reach().performClick()
		awaitUi("Disconnect did not finish") { it.connection == State.Idle }
		assertNull(MidiConnection.connectedDevice)
		fixture.await("The chosen play port stayed subscribed") { read().getInt("subscribers1") == 0 }
	}

	@Test fun aNewScreenInstanceKeepsTheConnection() {
		connectFixture()
		val before = compose.activity
		compose.activityRule.scenario.recreate()
		compose.waitUntil(10_000) { compose.activity !== before }
		assertTrue("Leaving the screen must keep the app connection", ui().connected)
		assertEquals("Virtual MIDI fixture", MidiConnection.connectedDevice?.name)
		compose.onNodeWithTag("midiTabApps").performClick()
		text(str(R.string.midi_apps_connected, "Virtual MIDI fixture")).reach().assertIsDisplayed()
	}

	@Test fun aServiceThatStopsIsShownAsLostAndIsPickedAgainAfterASearch() {
		connectFixture()
		fixture.call("exit")
		awaitUi("The stopped service was not shown as lost") { it.lost }
		assertNull(MidiConnection.connectedDevice)
		text(str(R.string.midi_apps_lost_detail, "Virtual MIDI fixture")).reach().assertIsDisplayed()
		text(str(R.string.midi_apps_missing)).reach().assertIsDisplayed()
		capture("lost")
		compose.onAllNodes(hasText(str(R.string.midi_apps_search)) and hasClickAction()).onFirst().reach().performClick()
		awaitUi("The search did not finish") { !it.scanning }
		Thread.sleep(3_000)
		compose.waitForIdle()
		assertFalse("A device found again must not connect by itself", ui().connected)
		assertNull(MidiConnection.connectedDevice)
		assertNull("A new pick is needed after the search", ui().selected)
		button(R.string.midi_apps_connect).reach().assertIsNotEnabled()
		capture("searched-again")
	}

	@Test fun aDeviceWithoutLightPortsConnectsForInputOnly() {
		openAppsTab()
		text("Input-only MIDI fixture").reach().performClick()
		text(str(R.string.midi_apps_lights_none)).reach().assertIsDisplayed()
		compose.onNode(hasText(str(R.string.midi_apps_lights_off)) and hasClickAction()).assertDoesNotExist()
		button(R.string.midi_apps_connect).reach().assertIsEnabled().performClick()
		awaitUi("The input-only service did not connect") { it.connection == State.Connected(lights = false) }
		text(str(R.string.midi_apps_status_lights_off)).reach().assertIsDisplayed()
		capture("input-only")
	}

	@Test fun aBusyLightPortOffersConnectingWithoutLights() {
		openAppsTab()
		val id = fixture.info(".MidiFixtureService").id
		val blocker = runBlocking { withTimeout(10_000) { AndroidVirtualMidiBackend(context).open(id) } }!!
		val occupied = blocker.openLightPort(0)!!
		try {
			pickFixture()
			text("Port 2 · Pads one").reach().performClick()
			text("Port 1 · Lights zero").reach().performClick()
			button(R.string.midi_apps_connect).reach().performClick()
			awaitUi("A busy light port must not report connected") { it.connection == State.LightUnavailable }
			assertNull(MidiConnection.connectedDevice)
			text(str(R.string.midi_apps_lights_failed)).reach().assertIsDisplayed()
			button(R.string.midi_apps_connect_cancel).assertIsDisplayed()
			capture("lights-busy")
			button(R.string.midi_apps_without_lights).reach().performClick()
			awaitUi("Connecting without lights failed") { it.connection == State.Connected(lights = false) }
		} finally { occupied.close(); blocker.close() }
	}

	@Test fun aUsbConnectionBlocksConnectingAnApp() {
		openAppsTab()
		pickFixture()
		text("Port 2 · Pads one").reach().performClick()
		text("Port 1 · Lights zero").reach().performClick()
		val usb = MidiConnection.transportGate.acquireUsb()!!
		try {
			button(R.string.midi_apps_connect).reach().performClick()
			awaitUi("A held USB connection must refuse the app") { it.connection == State.Failed(Failure.USB_BUSY) }
			text(str(R.string.midi_apps_failed_usb)).reach().assertIsDisplayed()
			assertNull(MidiConnection.connectedDevice)
			capture("usb-busy")
		} finally { usb.close() }
		button(R.string.midi_apps_retry).reach().performClick()
		awaitUi("Connecting again after the USB device left failed") { it.connected }
	}

	@Test fun koreanNarrowWindowAndLargeTextKeepEveryChoiceReadableAndReachable() {
		val originalScale = device.executeShellCommand("settings get system font_scale").trim()
		try {
			device.executeShellCommand("settings put system font_scale 2.0")
			compose.waitUntil(10_000) { compose.activity.resources.configuration.fontScale >= 1.99f }
			compose.activityRule.scenario.onActivity { activity ->
				val density = activity.resources.displayMetrics.density
				activity.window.setLayout((480 * density).toInt(), (360 * density).toInt())
			}
			compose.waitForIdle()
			openAppsTab()
			capture("ko-narrow-large-list")
			pickFixture()
			for (label in listOf(str(R.string.midi_apps_input_direction), "포트 2 · Pads one", str(R.string.midi_apps_lights_direction),
				"포트 1 · Lights zero", str(R.string.midi_apps_lights_off))) {
				val node = text(label).reach()
				val layouts = mutableListOf<TextLayoutResult>()
				node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
				val layout = layouts.single()
				assertFalse("Text is clipped: $label size=${layout.size} paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height} lines=${layout.lineCount} constraints=${layout.layoutInput.constraints}",
					layout.hasVisualOverflow)
			}
			text("포트 2 · Pads one").reach().performClick()
			text("포트 1 · Lights zero").reach().performClick()
			capture("ko-narrow-large-settings")
			button(R.string.midi_apps_connect).reach().assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
			awaitUi("The fixture did not connect from the narrow window") { it.connected }
			text(str(R.string.midi_apps_connected, "Virtual MIDI fixture")).reach().assertIsDisplayed()
			capture("ko-narrow-large-connected")
		} finally {
			device.executeShellCommand("settings put system font_scale $originalScale")
			compose.activityRule.scenario.onActivity { it.window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
		}
	}

	@Test fun koreanHelpExplainsSplitScreenAndItsLimit() {
		openAppsTab()
		button(R.string.midi_help_title).reach().performClick()
		text(str(R.string.midi_help_apps_play_body)).reach().assertIsDisplayed()
		capture("ko-help")
		device.pressBack()
		compose.waitForIdle()
		button(R.string.midi_help_close).assertDoesNotExist()
		assertFalse(compose.activity.isFinishing)
	}

	/** A window narrower than the two-pane layout (a phone's split-screen half) shows the help in one column. */
	@Test fun koreanHelpOpensAndClosesInANarrowWindow() {
		try {
			compose.activityRule.scenario.onActivity { activity ->
				val density = activity.resources.displayMetrics.density
				activity.window.setLayout((480 * density).toInt(), (360 * density).toInt())
			}
			compose.waitForIdle()
			openAppsTab()
			button(R.string.midi_help_title).reach().performClick()
			text(str(R.string.midi_help_apps_play_body)).reach().assertIsDisplayed()
			capture("ko-narrow-help")
			button(R.string.midi_help_close).reach().performClick()
			compose.waitForIdle()
			button(R.string.midi_help_close).assertDoesNotExist()
			button(R.string.midi_apps_search).reach().assertIsDisplayed()
			assertFalse(compose.activity.isFinishing)
		} finally {
			compose.activityRule.scenario.onActivity { it.window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
		}
	}
}
