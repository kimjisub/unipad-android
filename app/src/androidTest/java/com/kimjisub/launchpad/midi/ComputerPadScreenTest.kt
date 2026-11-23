package com.kimjisub.launchpad.midi

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.activity.ComputerPadActivity
import com.kimjisub.launchpad.activity.ComputerPadTags
import com.kimjisub.launchpad.activity.SettingsActivity
import com.kimjisub.launchpad.activity.cellDescription
import com.kimjisub.launchpad.ui.compose.PadGridGeometry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The computer pad screen against real Android MIDI ports of a test-only virtual peer, which
 * echoes every press back as a light. This is not USB testing: a computer is never involved.
 */
@RunWith(AndroidJUnit4::class)
class ComputerPadScreenTest {
	@get:Rule val compose = createEmptyComposeRule()

	private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
	private lateinit var manager: MidiManager
	private lateinit var info: MidiDeviceInfo
	private lateinit var observer: MidiDevice
	private lateinit var observed: AutoCloseable
	private val wire = Collections.synchronizedList(mutableListOf<List<Int>>())
	private val partial = mutableListOf<Int>()
	private val originalFactory = ComputerPadActivity.connectionFactory
	private val created = mutableListOf<UsbPeripheralConnection>()
	private var scenario: ActivityScenario<ComputerPadActivity>? = null

	@Suppress("DEPRECATION")
	@Before fun setup() {
		manager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
		awaitTrue { manager.devices.any { it.properties.getString(MidiDeviceInfo.PROPERTY_NAME) == PEER } }
		info = manager.devices.first { it.properties.getString(MidiDeviceInfo.PROPERTY_NAME) == PEER }
		val ready = CountDownLatch(1)
		var device: MidiDevice? = null
		manager.openDevice(info, { device = it; ready.countDown() }, Handler(Looper.getMainLooper()))
		assertTrue(ready.await(15, TimeUnit.SECONDS))
		observer = device!!
		observed = AndroidMidiPorts(observer).output(0, ::record)!!
		observer.openInputPort(1)!!.use {
			it.send(byteArrayOf(0), 0, 1)
			awaitTrue { wire.contains(listOf(0xb0, 111, 0)) }
		}
		wire.clear()
	}

	@After fun cleanup() {
		scenario?.close()
		ComputerPadActivity.connectionFactory = originalFactory
		awaitTrue { created.all { it.state.value !is UsbPeripheralConnection.State.Closing && !it.portsClosing.value } }
		if (::observed.isInitialized) observed.close()
		if (::observer.isInitialized) observer.close()
	}

	@Test fun settingsOpensThePadWithoutAPackAndAnEmulatorShowsNothingFound() {
		val settings = ActivityScenario.launch(SettingsActivity::class.java)
		try {
			compose.onNodeWithText(context.getString(R.string.computer_pad_title)).performClick()
			awaitTrue { resumed() is ComputerPadActivity }
			// Test services are virtual MIDI devices, which the system USB discovery never lists.
			compose.onNodeWithTag(ComputerPadTags.STATUS).assertTextEquals(context.getString(R.string.computer_pad_not_found))
			compose.onNodeWithText(context.getString(R.string.computer_pad_preview)).assertIsDisplayed()
			capture("not_found")
			compose.onNodeWithContentDescriptionOf(0).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
			// Back returns to Settings, not to a pack or the main list.
			InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
			awaitTrue { resumed() is SettingsActivity }
		} finally { settings.close() }
	}

	@Test fun fingersSendOnePressAndOneReleasePerPadAndLightsStayAfterRelease() {
		open()
		compose.onNodeWithTag(ComputerPadTags.STATUS).assertTextEquals(context.getString(R.string.computer_pad_port_open))
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput {
			down(0, cell(0))
			down(1, cell(0))
			up(0)
			down(0, cell(9))
			up(1)
			// Dragging off the pad releases it and presses the next one; leaving the grid releases.
			moveTo(0, cell(10))
			moveTo(0, Offset(-50f, -50f))
			up(0)
			down(2, cell(64))
			up(2)
		}
		awaitPresses(listOf(0x90, 81, 127), listOf(0x90, 72, 127), listOf(0x80, 81, 0), listOf(0x80, 72, 0),
			listOf(0x90, 73, 127), listOf(0x80, 73, 0), listOf(0xb0, 104, 127), listOf(0xb0, 104, 0))
		// The peer echoes each press back as a light: the last message for 81 was its release.
		compose.waitUntil(5000) { lightOf(64) == 0 && lightOf(0) == 0 }
		compose.onNodeWithTag(ComputerPadTags.STATUS).assertTextEquals(context.getString(R.string.computer_pad_port_open))
		compose.onNodeWithText(context.getString(R.string.computer_pad_lights_recent)).assertIsDisplayed()
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { down(0, cell(27)) }
		compose.waitUntil(5000) { lightOf(27) == 127 }
		capture("open_${orientation()}_pressed_and_lit")
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { up(0) }
	}

	@Test fun aSystemCancelAndARotationReleaseHeldPads() {
		open()
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { down(0, cell(20)); cancel() }
		awaitPresses(listOf(0x90, 65, 127), listOf(0x80, 65, 0))
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { down(0, cell(75)) }
		awaitPresses(listOf(0x90, 65, 127), listOf(0x80, 65, 0), listOf(0x90, 59, 127))
		scenario!!.onActivity {
			it.requestedOrientation = if (it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
				ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
		}
		awaitPresses(listOf(0x90, 65, 127), listOf(0x80, 65, 0), listOf(0x90, 59, 127), listOf(0x80, 59, 0))
		// The port survives the rotation. The pads are laid out again in the other orientation, so the
		// finger still down no longer reaches them; a finger kept on the same pads is covered by the resize test.
		compose.onNodeWithTag(ComputerPadTags.STATUS).assertTextEquals(context.getString(R.string.computer_pad_port_open))
		capture("open_${orientation()}_after_rotation")
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { up(0) }
		// Only a new touch presses again.
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { down(0, cell(20)); up(0) }
		awaitPresses(listOf(0x90, 65, 127), listOf(0x80, 65, 0), listOf(0x90, 59, 127), listOf(0x80, 59, 0),
			listOf(0x90, 65, 127), listOf(0x80, 65, 0))
	}

	/** A phone on its side at the default text size: the guide may scroll, but no action button is cut off. */
	@Test fun aLandscapePhoneShowsTheConnectedScreensButtonsWhole() {
		open()
		scenario!!.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
		awaitTrue { orientation() == "landscape" }
		compose.waitForIdle()
		// In landscape the pads take the whole height, so their bottom is where the window's content ends.
		val contentBottom = compose.onNodeWithTag(ComputerPadTags.GRID).fetchSemanticsNode().boundsInRoot.bottom
		for (button in listOf(R.string.computer_pad_help, R.string.computer_pad_disconnect)) {
			val bounds = compose.onNode(hasText(context.getString(button)) and hasClickAction()).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
			assertTrue("${context.getString(button)} ends at ${bounds.bottom}, below the content at $contentBottom", bounds.bottom <= contentBottom + 1f)
		}
		capture("open_landscape_buttons")
	}

	@Test fun aResizeReleasesHeldPadsAndTheFingerStaysSilentUntilItLifts() {
		open()
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { down(0, cell(75)) }
		awaitPresses(listOf(0x90, 59, 127))
		val before = compose.onNodeWithTag(ComputerPadTags.GRID).fetchSemanticsNode().size
		// Both sides shrink alike, so the orientation and the pads' place in the layout stay; only their size changes.
		scenario!!.onActivity {
			val window = it.window.decorView
			it.window.setLayout(window.width * 85 / 100, window.height * 85 / 100)
		}
		awaitPresses(listOf(0x90, 59, 127), listOf(0x80, 59, 0))
		compose.waitForIdle()
		assertNotEquals(before, compose.onNodeWithTag(ComputerPadTags.GRID).fetchSemanticsNode().size)
		// The same finger, still on the same pads, presses nothing until it lifts.
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { moveTo(0, cell(20)); moveTo(0, cell(10)); up(0) }
		Thread.sleep(300)
		assertEquals(2, presses().size)
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { down(1, cell(20)); up(1) }
		awaitPresses(listOf(0x90, 59, 127), listOf(0x80, 59, 0), listOf(0x90, 65, 127), listOf(0x80, 65, 0))
	}

	@Test fun leavingReleasesClosesAndComingBackWaitsForTheUser() {
		open()
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { down(0, cell(63)) }
		awaitPresses(listOf(0x90, 18, 127))
		scenario!!.moveToState(Lifecycle.State.CREATED)
		awaitPresses(listOf(0x90, 18, 127), listOf(0x80, 18, 0))
		awaitTrue { created.single().state.value == UsbPeripheralConnection.State.Idle }
		scenario!!.moveToState(Lifecycle.State.RESUMED)
		compose.onNodeWithTag(ComputerPadTags.STATUS).assertTextEquals(context.getString(R.string.computer_pad_paused_title))
		capture("paused_after_leaving")
		// The injected finger is still down; lifting it now must not send anything.
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { up(0) }
		Thread.sleep(300)
		assertEquals(2, presses().size)
		compose.onNodeWithText(context.getString(R.string.computer_pad_reconnect)).performClick()
		awaitTrue { created.single().state.value is UsbPeripheralConnection.State.Open }
		compose.onNodeWithTag(ComputerPadTags.STATUS).assertTextEquals(context.getString(R.string.computer_pad_port_open))
	}

	@Test fun aScreenReaderReadsRowColumnNumberAndLightAndItsClickIsAPressReleasePair() {
		open()
		val node = compose.onNodeWithContentDescriptionOf(9)
		node.assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(cellDescription(context.resources, 9, 0))))
		node.performSemanticsAction(SemanticsActions.OnClick)
		awaitPresses(listOf(0x90, 72, 127), listOf(0x80, 72, 0))
	}

	@Test fun anotherConnectionInUseIsNamedAndNotTakenOver() {
		// A USB launchpad, or a MIDI app on this phone picked in the device connection screen.
		for ((label, acquire) in listOf("usb" to MidiTransportGate::acquireUsb, "phone_app" to MidiTransportGate::acquireVirtual)) {
			val gate = MidiTransportGate()
			val held = acquire(gate)!!
			try {
				launch(gate)
				compose.onNodeWithText(context.getString(R.string.computer_pad_connect)).performClick()
				compose.onNodeWithTag(ComputerPadTags.STATUS).assertTextEquals(context.getString(R.string.computer_pad_busy_title))
				compose.onNodeWithText(context.getString(R.string.computer_pad_busy_desc)).assertIsDisplayed()
				capture("other_connection_in_use_$label")
			} finally {
				held.close()
				scenario?.close()
				scenario = null
			}
		}
	}

	@Test fun losingTheEndpointClearsPressesAndLightsAndAsksToStopTheComputersSound() {
		open()
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { down(0, cell(0)) }
		compose.waitUntil(5000) { lightOf(0) == 127 }
		observer.openInputPort(1)!!.use { it.send(byteArrayOf(1), 0, 1) }
		compose.onNodeWithTag(ComputerPadTags.GRID).performTouchInput { up(0); down(0, cell(1)); up(0) }
		awaitTrue { created.single().state.value is UsbPeripheralConnection.State.Failed }
		compose.onNodeWithTag(ComputerPadTags.STATUS).assertTextEquals(context.getString(R.string.computer_pad_disconnected_title))
		compose.onNodeWithText(context.getString(R.string.computer_pad_stop_sound)).assertIsDisplayed()
		compose.waitUntil(5000) { lightOf(0) == 0 }
		capture("disconnected")
		compose.onNodeWithContentDescriptionOf(0).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
	}

	@Test fun cancellingWhileConnectingClosesTheLateResultAndAllowsANewConnection() {
		var deliver: (() -> Unit)? = null
		val lateClosed = CountDownLatch(1)
		launch(MidiTransportGate(), defer = { deliver = it }, onClosed = { lateClosed.countDown() })
		compose.onNodeWithText(context.getString(R.string.computer_pad_connect)).performClick()
		compose.onNodeWithTag(ComputerPadTags.STATUS).assertTextEquals(context.getString(R.string.computer_pad_connecting))
		// Choices and a second connection are not offered while connecting.
		compose.onNodeWithText(context.getString(R.string.computer_pad_connect)).assertDoesNotExist()
		awaitTrue { deliver != null }
		capture("connecting")
		compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
		awaitTrue { created.single().state.value == UsbPeripheralConnection.State.Idle }
		deliver!!()
		assertTrue("the late device was not closed", lateClosed.await(15, TimeUnit.SECONDS))
		compose.onNodeWithText(context.getString(R.string.computer_pad_connect)).performClick()
		awaitTrue { created.single().state.value is UsbPeripheralConnection.State.Open }
		assertTrue(presses().isEmpty())
	}

	private fun open() {
		launch(MidiTransportGate())
		compose.onNodeWithText(context.getString(R.string.computer_pad_connect)).performClick()
		awaitTrue { (created.single().state.value as? UsbPeripheralConnection.State.Open)?.lightsAvailable == true }
		compose.waitForIdle()
	}

	private fun launch(gate: MidiTransportGate, defer: ((() -> Unit) -> Unit)? = null, onClosed: () -> Unit = {}) {
		ComputerPadActivity.connectionFactory = {
			UsbPeripheralConnection(PeerAccess(manager, info, defer, onClosed), gate).also { created.add(it) }
		}
		scenario = ActivityScenario.launch(ComputerPadActivity::class.java)
		compose.onNodeWithText(context.getString(R.string.computer_pad_found)).assertIsDisplayed()
	}

	private fun capture(name: String) {
		compose.waitForIdle()
		TestCaptures.screenshot(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()), "computer_pad/$name.png")
	}

	private fun orientation(): String {
		var portrait = false
		scenario?.onActivity { portrait = it.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT }
		return if (portrait) "portrait" else "landscape"
	}

	private fun TouchInjectionScope.cell(index: Int): Offset {
		val (row, column) = PadGridGeometry.position(index)
		val pitch = width.toFloat() / PadGridGeometry.CELLS
		return Offset((column + 0.5f) * pitch, (row + 0.5f) * pitch)
	}

	private fun androidx.compose.ui.test.SemanticsNodeInteractionsProvider.onNodeWithContentDescriptionOf(index: Int) =
		onNode(hasContentDescription(cellDescription(context.resources, index, lightOf(index))) and hasClickActionOrDisabled())

	private fun hasClickActionOrDisabled() = hasClickAction() or SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)

	private fun lightOf(index: Int): Int {
		var color = 0
		scenario?.onActivity { color = created.lastOrNull()?.lights?.snapshot()?.get(index) ?: 0 }
		return color
	}

	/** Messages the screen sent, without the peer's own counter. */
	private fun presses() = wire.toList().filter { it[0] != 0xb0 || it[1] != 111 }

	private fun awaitPresses(vararg expected: List<Int>) {
		awaitTrue { presses().size >= expected.size }
		assertEquals(expected.toList(), presses())
	}

	private fun record(bytes: ByteArray, offset: Int, count: Int) = synchronized(wire) {
		for (i in offset until offset + count) {
			partial.add(bytes[i].toInt() and 255)
			if (partial.size == 3) { wire.add(partial.toList()); partial.clear() }
		}
	}

	private fun resumed() = run {
		var activity: android.app.Activity? = null
		InstrumentationRegistry.getInstrumentation().runOnMainSync {
			activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
		}
		activity
	}

	private fun awaitTrue(condition: () -> Boolean) {
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
		while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
		assertTrue("Condition did not complete; wire=${wire.toList()}", condition())
	}

	private companion object { const val PEER = "Peripheral port test peer" }
}

/** The test peer offered as if the system had listed it as a USB endpoint. */
private class PeerAccess(
	private val manager: MidiManager,
	private val info: MidiDeviceInfo,
	private var defer: ((() -> Unit) -> Unit)? = null,
	private val onClosed: () -> Unit = {},
) : PeripheralMidiAccess {
	override fun devices() = listOf(PeripheralDevice(info.id, "test peer", listOf(PeripheralDevice.Port(0, "press")), listOf(PeripheralDevice.Port(0, "lights"))))
	@Suppress("DEPRECATION")
	override fun watch(changed: () -> Unit, removed: (Int) -> Unit): AutoCloseable {
		val callback = object : MidiManager.DeviceCallback() {
			override fun onDeviceRemoved(device: MidiDeviceInfo) { removed(device.id) }
		}
		manager.registerDeviceCallback(callback, Handler(Looper.getMainLooper()))
		return AutoCloseable { manager.unregisterDeviceCallback(callback) }
	}
	override fun open(id: Int, completed: (PeripheralMidiAccess.Device?) -> Unit) {
		manager.openDevice(info, { device ->
			val ports = device?.let(::AndroidMidiPorts)?.let { ports ->
				object : PeripheralMidiAccess.Device by ports {
					override fun close() { try { ports.close() } finally { onClosed() } }
				}
			}
			val delay = defer
			defer = null
			if (delay == null) completed(ports) else delay { completed(ports) }
		}, Handler(Looper.getMainLooper()))
	}
}
