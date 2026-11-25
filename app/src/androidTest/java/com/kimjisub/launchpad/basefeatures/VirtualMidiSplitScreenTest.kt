package com.kimjisub.launchpad.basefeatures

import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Point
import android.hardware.usb.UsbManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.midi.MidiConnection
import com.kimjisub.launchpad.midi.MidiFixturePadActivity
import com.kimjisub.launchpad.midi.VirtualMidiConnection
import com.kimjisub.launchpad.midi.VirtualMidiFixture
import com.kimjisub.launchpad.midi.driver.Matrix
import com.kimjisub.launchpad.midi.driver.Noting
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Another app on the same phone plays the pack through a virtual MIDI device, beside the play screen
 * in split screen. Pads pressed in that app's window keep playing and lighting the pack while the
 * play screen stays visible. Android 7–9 pause the window that lost focus and Android 10 and later
 * keep it resumed; each run checks the state its Android version uses. Leaving the play screen,
 * losing sound output, rotating, resizing or the other app exiting still lets go of a held pad, and
 * input received while the screen is hidden is not played on return. A controller that is not a
 * virtual device keeps the earlier rule: its held pad stops once the other window has focus.
 */
@RunWith(AndroidJUnit4::class)
class VirtualMidiSplitScreenTest : PlaybackScreenTest() {
	private val fixture by lazy { VirtualMidiFixture(screen.context) }
	private val fingers by lazy { Fingers(screen) }
	private var connection: VirtualMidiConnection? = null
	private lateinit var play: PlayActivity
	private lateinit var vm: PlayActivityViewModel
	private var resized = false

	// Each step runs even when an earlier one fails, so the display is always restored.
	@After fun leaveSplitScreen() {
		val failures = listOf(
			runCatching { fingers.cancelRemaining() },
			runCatching { connection?.let { fixture.close(it) } },
			runCatching { endSplitScreen() },
			runCatching { screen.onMain { MidiConnection.driver = Noting() } },
			runCatching { if (resized) screen.device.executeShellCommand("wm size reset") },
			runCatching { screen.device.setOrientationNatural() },
			runCatching { screen.device.unfreezeRotation() },
		).mapNotNull { it.exceptionOrNull() }
		failures.drop(1).forEach { failures.first().addSuppressed(it) }
		failures.firstOrNull()?.let { throw it }
	}

	/**
	 * The next test must start without this split screen: a split left behind opens its play screen
	 * in one half, beside a stale pad window that holds focus.
	 */
	private fun endSplitScreen() {
		runCatching { fixture.call("closePad") }
		if (::play.isInitialized) screen.onMain { if (!play.isDestroyed) play.finish() }
		// Before Android 9 the docked stack outlives the other half; removing it closes the play screen.
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) screen.device.executeShellCommand("am stack remove $DOCKED_STACK")
		screen.await("The other app's window stayed open after the test") { !screen.device.hasObject(By.desc(PAD)) }
		if (::play.isInitialized) screen.await("The play screen stayed open after the test") { screen.onMain { play.isDestroyed } }
	}

	@Test fun connectingBesideAnOpenEightByEightPackKeepsTheConnectionAndRedrawsTheLights() {
		openWithVirtualDevice()
		// Clearing and redrawing 64 pads and the 32-button ring, before any pad is pressed. The lights
		// are written on another thread, so they are counted once they have had time to arrive.
		val expected = 96 + 64 + 32
		runCatching { screen.await("The attach-time redraw did not reach the peer") { fixture.lights().size >= expected } }
		val lights = fixture.lights()
		Log.i(TAG, "API ${Build.VERSION.SDK_INT}: lights the peer received while connecting to the open 8x8 pack: ${lights.size}")
		assertTrue("The attach-time redraw did not reach the peer (${lights.size} of $expected messages)", lights.size >= expected)
		SystemClock.sleep(2_000)
		assertEquals("The connection must survive the attach-time redraw",
			VirtualMidiConnection.State.Connected(true), fixture.main { connection!!.state })
	}

	@Test fun padsPressedInTheOtherWindowKeepPlayingAndLighting() {
		openBesideTheVirtualApp()
		fixture.call("clear")
		val finger = fingers.down(otherAppPad())
		screen.await("The other window's press did not reach the play screen") { audio.plays.size == 1 }
		// Longer than the delayed stop of an ordinary unfocused window.
		SystemClock.sleep(1_500)
		assertBesideTheOtherApp()
		assertEquals("The held pad must keep looping while the play screen is visible", 1, audio.ringingLoops().size)
		screen.await("The pack's light did not reach the other app") { lightOn() >= 0 }
		screen.capture("virtual-midi-split-held")
		fingers.up(finger)
		screen.await("Releasing in the other window did not stop the loop") { audio.ringingLoops().isEmpty() }
		screen.await("The light-off did not follow the light-on at the other app") { lightOffAfter(lightOn()) }
		assertEquals("One press must play once", 1, audio.plays.size)
		fingers.up(fingers.down(otherAppPad()))
		screen.await("A second press in the other window did not play") { audio.plays.size == 2 }
		screen.capture("virtual-midi-split-released")
	}

	@Test fun goingHomeLetsGoOfTheHeldPad() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
			holdThenLeave("going Home") {
				assertTrue(screen.device.pressHome())
				screen.await("Home did not hide the play screen") { screen.onMain { !vm.screenVisible } }
			}
			return
		}
		// With the docked split of Android 7–12, Home only minimizes the docked play screen, which stays
		// visible and keeps playing; releasing the pad then stops it, and nothing is played again.
		changeWindowWhileHeld("going Home beside the minimized dock") {
			// pressHome reports whether the window list changed in time, which the minimized dock delays.
			screen.device.pressHome()
			screen.await("Home did not cover the other app's window") { !screen.device.hasObject(By.desc(PAD)) }
			SystemClock.sleep(1_000)
			assertTrue("Home must leave the minimized dock visible",
				screen.onMain { vm.screenVisible && play.isInMultiWindowMode })
		}
	}

	@Test fun theOtherAppGoingFullScreenLetsGoOfTheHeldPad() = holdThenLeave("the other app going full screen") {
		exitSplitScreen()
		screen.await("The full-screen app did not hide the play screen") { screen.onMain { !vm.screenVisible } }
	}

	/**
	 * A closing play screen lets go as it pauses, before the screen shown next takes the device;
	 * letting go only at its stop would clear the next screen's lights. Android 10 and later report
	 * the moment right after the pause.
	 */
	@Test fun closingThePlayScreenLetsGoOfTheHeldPad() = holdThenLeave("closing the play screen") {
		// -1 until the play screen pauses, then 1 if it still held the device.
		val heldAfterPause = java.util.concurrent.atomic.AtomicInteger(-1)
		val callbacks = object : Application.ActivityLifecycleCallbacks {
			override fun onActivityPostPaused(activity: Activity) {
				if (activity === play) heldAfterPause.compareAndSet(-1, if (MidiConnection.controller != null) 1 else 0)
			}
			override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
			override fun onActivityStarted(activity: Activity) = Unit
			override fun onActivityResumed(activity: Activity) = Unit
			override fun onActivityPaused(activity: Activity) = Unit
			override fun onActivityStopped(activity: Activity) = Unit
			override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
			override fun onActivityDestroyed(activity: Activity) = Unit
		}
		screen.onMain { play.application.registerActivityLifecycleCallbacks(callbacks) }
		try {
			screen.onMain { play.finish() }
			screen.await("The play screen did not close") { screen.onMain { play.isDestroyed } }
		} finally {
			screen.onMain { play.application.unregisterActivityLifecycleCallbacks(callbacks) }
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			assertEquals("The closing play screen still held the device after its pause", 0, heldAfterPause.get())
		}
	}

	// The stream-type request exists on every supported version; AudioFocusRequest is API 26+, and a
	// lambda capturing one would keep this class from loading on Android 7.
	@Suppress("DEPRECATION")
	@Test fun losingSoundOutputLetsGoOfTheHeldPad() {
		val audioManager = screen.context.getSystemService(AudioManager::class.java)
		val otherPlayer = AudioManager.OnAudioFocusChangeListener { }
		try {
			// The earlier rule for sound output: silence, while the press itself stays held.
			holdThenLeave("another player taking sound output", lightsOff = false) {
				assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
					audioManager.requestAudioFocus(otherPlayer, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN))
			}
		} finally {
			audioManager.abandonAudioFocus(otherPlayer)
		}
	}

	// The exited app has no light port left to read.
	@Test fun theOtherAppExitingLetsGoOfTheHeldPad() = holdThenLeave("the other app exiting", lightsOff = false) {
		fixture.call("exit")
		// The exited app's window took the finger's gesture with it.
		fingers.forgetCancelled()
		screen.await("The other app's exit did not end the connection") {
			fixture.main { connection!!.state is VirtualMidiConnection.State.Failed }
		}
		// A light written as the peer dies can report the end before its status checks do.
		Log.i(TAG, "API ${Build.VERSION.SDK_INT}: the other app's exit ended the connection as ${fixture.main { connection!!.state }}")
	}

	@Test fun lockingLetsGoAndInputWhileLockedIsNotPlayedOnReturn() {
		openBesideTheVirtualApp()
		holdOtherAppPad()
		screen.device.sleep()
		screen.await("Locking did not hide the play screen") { screen.onMain { !vm.screenVisible } }
		assertLetGo("locking")
		// Ends the gesture; the other app's release reaches a hidden screen and is dropped.
		fingers.forgetCancelled()
		val played = audio.plays.size
		// The other app keeps sending while the play screen is hidden.
		fixture.send(0x91, 64, 127)
		fixture.send(0x91, 64, 0)
		fixture.send(0x91, 64, 127)
		SystemClock.sleep(500)
		screen.device.wakeUp()
		// `wm dismiss-keyguard` arrived in Android 8; the menu key dismisses an insecure lock before it.
		screen.device.executeShellCommand(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) "wm dismiss-keyguard" else "input keyevent 82")
		screen.await("Unlocking did not show the play screen again") { screen.onMain { vm.screenVisible } }
		SystemClock.sleep(1_000)
		assertEquals("Input received while locked must not play on return", played, audio.plays.size)
		assertTrue("Nothing may loop after returning", audio.ringingLoops().isEmpty())
		assertBesideTheOtherApp()
		fixture.send(0x91, 64, 127)
		screen.await("A press after returning did not play") { audio.plays.size == played + 1 }
		fixture.send(0x81, 64, 64)
		screen.await("A release after returning did not stop the loop") { audio.ringingLoops().isEmpty() }
	}

	@Test fun rotatingWhileHeldLeavesNothingStuck() = changeWindowWhileHeld("rotating") {
		screen.device.setOrientationLeft()
	}

	@Test fun resizingWhileHeldLeavesNothingStuck() = changeWindowWhileHeld("resizing the display", restore = {
		screen.device.executeShellCommand("wm size reset")
		resized = false
	}) {
		resized = true
		screen.device.executeShellCommand("wm size 900x2000")
	}

	@Test fun aControllerThatIsNotAVirtualDeviceStillStopsWhenTheOtherWindowHasFocus() {
		loopTopLeftPad()
		screen.openPlay()
		play = screen.resumed() as PlayActivity
		vm = screen.vm()
		val driver = Matrix()
		// The controller path a USB device uses, without a virtual connection.
		screen.onMain {
			MidiConnection.initConnection(Intent(), screen.context.getSystemService(UsbManager::class.java), screen.context)
			MidiConnection.driver = driver
		}
		enterSplitScreen()
		// Focus the play screen again, then hold its pad from the controller.
		fingers.up(fingers.down(playScreenCentre()))
		screen.await("The play screen did not take focus back") { screen.onMain { play.hasWindowFocus() } }
		screen.onMain { driver.getSignal(9, 0x90, 64, 127) }
		screen.await("The controller press did not play") { audio.ringingLoops().size == 1 }
		fingers.up(fingers.down(otherAppPad()))
		screen.await("The other window did not take focus") { screen.onMain { !play.hasWindowFocus() } }
		screen.await("A controller that is not a virtual device must stop once the other window has focus") {
			audio.ringingLoops().isEmpty()
		}
		screen.onMain { driver.getSignal(8, 0x80, 64, 0) }
	}

	private fun holdThenLeave(context: String, lightsOff: Boolean = true, leave: () -> Unit) {
		openBesideTheVirtualApp()
		holdOtherAppPad()
		leave()
		assertLetGo(context, lightsOff)
		screen.capture("virtual-midi-split-after-${context.replace(' ', '-')}")
	}

	/** [restore] undoes the change before the closing screenshot, which some sizes cannot take. */
	private fun changeWindowWhileHeld(context: String, restore: () -> Unit = {}, change: () -> Unit) {
		openBesideTheVirtualApp()
		val finger = holdOtherAppPad()
		change()
		SystemClock.sleep(1_500)
		Log.i(TAG, "$context while held: play screen ${screen.onMain { play.lifecycle.currentState }}, " +
			"multi-window ${screen.onMain { play.isInMultiWindowMode }}, looping ${audio.ringingLoops().size}")
		// The finger may have been cancelled by the change; lifting ends it either way.
		runCatching { fingers.up(finger) }
		fingers.forgetCancelled()
		fixture.send(0x81, 64, 64)
		screen.await("After $context and releasing, the pad kept looping") { audio.ringingLoops().isEmpty() }
		SystemClock.sleep(500)
		assertEquals("After $context the held press must not play again", 1, audio.plays.size)
		screen.await("After $context the pad's light stayed on at the other app") { lightOffAfter(lightOn()) }
		restore()
		screen.capture("virtual-midi-split-after-${context.replace(' ', '-')}")
	}

	private fun holdOtherAppPad(): Int {
		fixture.call("clear")
		val presses = padPresses()
		val finger = fingers.down(otherAppPad())
		// Separates a press the emulator never delivered from one the play screen did not play.
		screen.await("The other app's window did not receive the press") { padPresses() > presses }
		screen.await("The other window's press did not start the looping pad") { audio.ringingLoops().size == 1 }
		SystemClock.sleep(1_000)
		assertBesideTheOtherApp()
		assertEquals("The held pad must keep looping beside the other app", 1, audio.ringingLoops().size)
		return finger
	}

	private fun assertLetGo(context: String, lightsOff: Boolean = true) {
		screen.await("$context with the other app's pad held left the pad looping") { audio.ringingLoops().isEmpty() }
		if (lightsOff) screen.await("$context left the pad lit at the other app") { lightOffAfter(lightOn()) }
		assertFalse("A hidden play screen must not take controller input after $context",
			screen.onMain { !vm.screenVisible && MidiConnection.controller != null })
		assertEquals("$context must not play the held press again", 1, audio.plays.size)
	}

	/** Android 7–9 pause the visible window that lost focus; Android 10 and later keep it resumed. */
	private fun assertBesideTheOtherApp() {
		val state = screen.onMain { play.lifecycle.currentState }
		Log.i(TAG, "API ${Build.VERSION.SDK_INT}: play screen beside the other app is $state, " +
			"focused ${screen.onMain { play.hasWindowFocus() }}, multi-window ${screen.onMain { play.isInMultiWindowMode }}")
		assertFalse("The other window must have focus", screen.onMain { play.hasWindowFocus() })
		assertTrue("The play screen must share the display", screen.onMain { play.isInMultiWindowMode })
		val expected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Lifecycle.State.RESUMED else Lifecycle.State.STARTED
		assertEquals("Play screen state beside the other app on API ${Build.VERSION.SDK_INT}", expected, state)
	}

	/** Index of the latest light-on for the top-left pad at the other app, or -1. */
	private fun lightOn(): Int = fixture.lights().indexOfLast { it.size == 3 && it[1] == 64.toByte() && it[0] == 0x91.toByte() && it[2] != 0.toByte() }

	private fun lightOffAfter(index: Int): Boolean = fixture.lights().drop(index + 1)
		.any { it.size == 3 && it[1] == 64.toByte() && it[0] == 0x91.toByte() && it[2] == 0.toByte() }

	/** The pack's top-left pad loops while held, so letting go of it is a loop that stops. */
	private fun loopTopLeftPad() {
		val keySound = File(screen.pack(), "keySound")
		keySound.writeText(keySound.readLines().joinToString("\n") {
			if (it.startsWith("1 1 1 ")) "1 1 1 silence.wav 0" else it
		} + "\n")
	}

	private fun openWithVirtualDevice() {
		loopTopLeftPad()
		screen.openPlay()
		screen.onMain { screen.vm().scbFeedbackLight.setChecked(true) }
		play = screen.resumed() as PlayActivity
		vm = screen.vm()
		val opened = fixture.connection().also { connection = it }
		val selected = fixture.selection(opened)
		fixture.call("clear")
		fixture.main { assertTrue(opened.connect(selected, Matrix())) }
		fixture.await("The virtual device did not connect") {
			fixture.main { opened.state == VirtualMidiConnection.State.Connected(true) }
		}
	}

	private fun openBesideTheVirtualApp() {
		openWithVirtualDevice()
		enterSplitScreen()
	}

	/**
	 * Opens the other app's pad window and puts it beside the play screen. Android 12L and later
	 * split through the system UI's window manager shell; earlier versions dock the play screen.
	 * Launched as an adjacent window from here, the other window would land in the docked stack, on
	 * top of the play screen; before Android 9 it goes into the full-screen stack by its id instead.
	 */
	private fun enterSplitScreen() {
		val pad = Intent().setClassName(screen.instrumentation.context.packageName, MidiFixturePadActivity::class.java.name)
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
		val playTask = screen.onMain { play.taskId }
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
			screen.context.startActivity(pad)
			screen.await("The other app's window did not open") { screen.device.hasObject(By.desc(PAD)) }
			screen.device.executeShellCommand("dumpsys activity service SystemUIService WMShell splitscreen moveToSideStage $playTask 0")
		} else {
			// The docked stack of Android 7–12; the launcher's recents fill the other half until a launch.
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
				screen.device.executeShellCommand("am stack movetask $playTask $DOCKED_STACK true")
			} else {
				// From Android 9 stacks are numbered as created, so the system docks the top task itself,
				// as when the user long-presses Overview.
				assertTrue("The system refused to split the screen",
					screen.instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN))
			}
			screen.await("The play screen was not docked") { screen.onMain { play.isInMultiWindowMode } }
			// Opened before them, the other app's window would be covered by those recents.
			screen.await("The recents did not take the other half") { screen.onMain { !play.hasWindowFocus() } }
			screen.device.waitForIdle()
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
				screen.device.executeShellCommand("am start --stack $FULL_SCREEN_STACK -f ${pad.flags} -n ${pad.component!!.flattenToShortString()}")
			} else {
				// From Android 9 a new task opened beside the dock fills the other half by itself.
				screen.context.startActivity(pad)
			}
		}
		screen.await("The play screen did not share the display with the other app") {
			screen.onMain { play.isInMultiWindowMode && !play.isDestroyed } && screen.device.hasObject(By.desc(PAD))
		}
		screen.device.waitForIdle()
		screen.capture("virtual-midi-split-entered")
	}

	private fun exitSplitScreen() {
		val padTask = fixture.call("read").getInt("padTask")
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
			screen.device.executeShellCommand("dumpsys activity service SystemUIService WMShell splitscreen exitSplitScreen $padTask")
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
			// Android 12L–13 have no exit command; taking the play screen out of its side ends the split.
			val playTask = screen.onMain { play.taskId }
			screen.device.executeShellCommand("dumpsys activity service SystemUIService WMShell splitscreen removeFromSideStage $playTask")
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
			// Emptying the docked stack ends the split; behind the other app, the play screen is hidden.
			val playTask = screen.onMain { play.taskId }
			moveTask(playTask, stackOf(padTask), toTop = false)
		} else {
			// Emptying the docked stack ends the split; the other app stays on top, full screen.
			val playTask = screen.onMain { play.taskId }
			screen.device.executeShellCommand("am stack movetask $playTask $FULL_SCREEN_STACK false")
			screen.device.executeShellCommand("am stack movetask $padTask $FULL_SCREEN_STACK true")
		}
	}

	/** Android 10 refuses the older `movetask` spelling, and a refused move shows only in the output. */
	private fun moveTask(task: Int, stack: Int, toTop: Boolean) {
		val output = screen.device.executeShellCommand("am stack move-task $task $stack $toTop").trim()
		assertTrue("Moving task $task to stack $stack failed: $output", output.isEmpty())
	}

	/** The stack holding a task, from Android 9 numbered as stacks are created. */
	private fun stackOf(task: Int): Int {
		val list = screen.device.executeShellCommand("am stack list")
		var stack: Int? = null
		list.lineSequence().map { it.trim() }.forEach { line ->
			STACK_LINE.find(line)?.let { stack = it.groupValues[1].toInt() }
			if (line.startsWith("taskId=$task:")) return checkNotNull(stack) { "Task $task is listed before any stack:\n$list" }
		}
		throw AssertionError("Task $task is in no stack:\n$list")
	}

	private fun padPresses(): Int = fixture.call("read").getInt("padPresses")

	/** The centre of the other app's pad once its window has stopped moving (a split settles after it opens). */
	private fun otherAppPad(): Point {
		var last: android.graphics.Rect? = null
		screen.await("The other app's window did not settle") {
			val now = screen.device.findObject(By.desc(PAD))?.visibleBounds
			(now != null && now == last).also { last = now; if (!it) SystemClock.sleep(150) }
		}
		return last!!.let { Point(it.centerX(), it.centerY()) }
	}

	private fun playScreenCentre(): Point = screen.onMain { screen.bounds(play.window.decorView) }.let { Point(it.centerX(), it.centerY()) }

	private companion object {
		const val PAD = "Fixture pad"
		// Fixed stack ids before Android 9 (ActivityManager.StackId).
		const val FULL_SCREEN_STACK = 1
		const val DOCKED_STACK = 3
		val STACK_LINE = Regex("""^Stack id=(\d+)""")
		const val TAG = "VirtualMidiSplitScreen"
	}
}
