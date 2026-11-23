package com.kimjisub.launchpad.midi.controller

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllerInputHoldTest {
	private val calls = mutableListOf<String>()
	private var virtual = true
	private var multiWindow = true
	private val hold = ControllerInputHold({ virtual && multiWindow }, object : ControllerInputHold.Target {
		override fun take(redraw: Boolean) { calls += if (redraw) "take+redraw" else "take" }
		override fun release() { calls += "release" }
		override fun scheduleUnfocusedStop() { calls += "schedule" }
		override fun cancelUnfocusedStop() { calls += "cancel" }
	})

	/** Opened in a single window, then put beside the other app. */
	private fun front() {
		val split = multiWindow
		multiWindow = false
		hold.onStart()
		multiWindow = split
		hold.onResume()
		hold.onFocusChanged(true)
		calls.clear()
	}

	/** Android 7–9: the visible window that lost focus is paused. */
	private fun otherWindowTappedBefore10() {
		hold.onFocusChanged(false)
		hold.onPause(finishing = false)
	}

	@Test fun android7To9PausedBesideTheVirtualAppKeepsInputAndResumesWithoutRedraw() {
		front()
		otherWindowTappedBefore10()
		assertTrue(hold.receiving)
		assertEquals(listOf("cancel", "cancel"), calls)
		calls.clear()
		hold.onResume()
		hold.onFocusChanged(true)
		assertEquals("Lights stayed current while held", listOf("take", "cancel"), calls)
	}

	@Test fun android7To9ShownAgainBesideTheVirtualAppWithoutResumingTakesInputOnStart() {
		front()
		otherWindowTappedBefore10()
		hold.onStop()
		calls.clear()
		hold.onStart()
		assertTrue(hold.receiving)
		assertEquals(listOf("take+redraw"), calls)
		hold.onResume()
		assertEquals("Resuming after the start-time take must not redraw again", listOf("take+redraw", "take"), calls)
	}

	@Test fun startingInASingleWindowWaitsForResume() {
		multiWindow = false
		hold.onStart()
		assertFalse(hold.receiving)
		assertTrue(calls.isEmpty())
	}

	@Test fun android10UnfocusedBesideTheVirtualAppSkipsTheDelayedStop() {
		front()
		hold.onFocusChanged(false)
		assertTrue(hold.receiving)
		assertEquals(listOf("cancel"), calls)
	}

	@Test fun stoppingAHeldScreenReleasesOnceForHomeLockAndFullScreenApps() {
		front()
		otherWindowTappedBefore10()
		calls.clear()
		hold.onStop()
		assertFalse(hold.receiving)
		assertEquals(listOf("release"), calls)
		hold.onStop()
		assertEquals(listOf("release"), calls)
	}

	@Test fun android10PauseOnTheWayToStopHoldsUntilStopReleases() {
		front()
		hold.onFocusChanged(false)
		hold.onPause(finishing = false)
		hold.onStop()
		assertEquals(listOf("cancel", "cancel", "release"), calls)
	}

	@Test fun usbOrSingleWindowKeepsThePreviousRules() {
		for ((usb, single) in listOf(true to false, false to true)) {
			virtual = !usb
			multiWindow = !single
			front()
			hold.onFocusChanged(false)
			assertEquals("Focus loss must schedule the delayed stop (usb=$usb)", listOf("schedule"), calls)
			hold.onPause(finishing = false)
			assertFalse(hold.receiving)
			assertEquals("Pause must release at once (usb=$usb)", listOf("schedule", "cancel", "release"), calls)
			hold.onStop()
			assertEquals("Stop must not release again (usb=$usb)", listOf("schedule", "cancel", "release"), calls)
		}
	}

	@Test fun leavingMultiWindowOrLosingTheVirtualDeviceWhileHeldReleases() {
		for (change in listOf("single window", "virtual gone")) {
			virtual = true
			multiWindow = true
			front()
			otherWindowTappedBefore10()
			calls.clear()
			if (change == "single window") multiWindow = false else virtual = false
			hold.reevaluate()
			assertFalse(change, hold.receiving)
			assertEquals(change, listOf("release"), calls)
			hold.onStop()
		}
	}

	@Test fun android10EndOfTheHoldRestoresTheSkippedDelayedStopOnce() {
		front()
		hold.onFocusChanged(false)
		calls.clear()
		virtual = false
		hold.reevaluate()
		hold.reevaluate()
		assertEquals(listOf("schedule"), calls)
	}

	@Test fun connectionChangesWithoutASkippedStopChangeNothing() {
		virtual = false
		front()
		hold.reevaluate()
		hold.onFocusChanged(false)
		calls.clear()
		hold.reevaluate()
		assertTrue(calls.isEmpty())
	}

	@Test fun closingTheScreenBesideTheVirtualAppReleasesAtOnce() {
		for (unfocusedFirst in listOf(true, false)) {
			front()
			if (unfocusedFirst) hold.onFocusChanged(false)
			calls.clear()
			hold.onPause(finishing = true)
			assertFalse(hold.receiving)
			assertEquals("A closing screen must not hold input for the screen shown next", listOf("cancel", "release"), calls)
			hold.onStop()
			assertEquals("Stop must not release again", listOf("cancel", "release"), calls)
		}
	}

	@Test fun android10ConnectingWhileUnfocusedBesideTheOtherAppSkipsTheScheduledStop() {
		virtual = false
		front()
		hold.onFocusChanged(false)
		assertEquals(listOf("schedule"), calls)
		virtual = true
		hold.reevaluate()
		hold.reevaluate()
		assertTrue(hold.receiving)
		assertEquals("The pending stop is cancelled once", listOf("schedule", "cancel"), calls)
		virtual = false
		hold.reevaluate()
		assertEquals("Losing the device again restores the stop", listOf("schedule", "cancel", "schedule"), calls)
	}
}
