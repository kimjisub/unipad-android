package com.kimjisub.launchpad.midi.controller

/**
 * Decides when the play screen lets go of controller input, its lights and the sound it started.
 *
 * Normally the screen lets go once it is paused, or shortly after its window loses focus. One case
 * keeps them while the screen is still visible: a virtual MIDI device (another app on this phone)
 * with the screen sharing the display in multi-window mode, so pads pressed in the other window
 * keep playing. Android 7–9 pause the visible window that lost focus; Android 10 and later keep it
 * resumed but unfocused. Either way the hold ends when the screen is stopped (Home, lock, another
 * full-screen app, leaving the screen) or when [keepWhileUnfocused] stops being true.
 *
 * Events and callbacks belong to the main thread.
 */
internal class ControllerInputHold(
	private val keepWhileUnfocused: () -> Boolean,
	private val target: Target,
) {
	interface Target {
		/** Receives input and sends lights; [redraw] when the device's lights may be stale. */
		fun take(redraw: Boolean)

		/** Stops input, the sound it started, and the device's lights. */
		fun release()

		/** The screen's delayed stop for a window left unfocused, as for an ordinary focus loss. */
		fun scheduleUnfocusedStop()

		fun cancelUnfocusedStop()
	}

	private var started = false
	private var resumed = false
	// Only a focus loss the window reported schedules a stop.
	private var focused = true
	private var held = false
	// The focus loss whose delayed stop was skipped for the hold; it is owed once the hold ends.
	private var stopSkipped = false

	/** True while input is received: resumed, or paused but kept visible beside the other app. */
	val receiving: Boolean get() = resumed || held

	/** Android 7–9 can show the screen again beside the other app without resuming it (after unlock). */
	fun onStart() {
		started = true
		if (!resumed && !held && keepWhileUnfocused()) {
			held = true
			target.take(redraw = true)
		}
	}

	fun onResume() {
		resumed = true
		val wasHeld = held
		held = false
		target.take(redraw = !wasHeld)
	}

	fun onPause() {
		resumed = false
		target.cancelUnfocusedStop()
		if (started && keepWhileUnfocused()) held = true else target.release()
	}

	fun onStop() {
		started = false
		if (held) {
			held = false
			target.release()
		}
	}

	fun onFocusChanged(hasFocus: Boolean) {
		focused = hasFocus
		stopSkipped = !hasFocus && keepWhileUnfocused()
		if (hasFocus || stopSkipped) target.cancelUnfocusedStop() else target.scheduleUnfocusedStop()
	}

	/** Call when the connection or the multi-window mode changes. */
	fun reevaluate() {
		if (keepWhileUnfocused()) return
		if (held) {
			held = false
			target.release()
		}
		if (stopSkipped) {
			stopSkipped = false
			if (resumed && !focused) target.scheduleUnfocusedStop()
		}
	}
}
