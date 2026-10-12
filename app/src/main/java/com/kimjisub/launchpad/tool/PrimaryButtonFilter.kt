package com.kimjisub.launchpad.tool

import android.view.InputDevice
import android.view.MotionEvent

/**
 * Lets only the primary (left) mouse button act on the screen. Android delivers a right or middle
 * press as an ordinary touch gesture, so without this every switch, row and chain button treats it
 * as a tap. The whole gesture of such a press is dropped; fingers, styluses, and mouse presses that
 * carry no button state (some injected events) pass through.
 */
class PrimaryButtonFilter {
	private var droppedDeviceId: Int? = null

	fun shouldDrop(event: MotionEvent): Boolean =
		shouldDrop(event.actionMasked, event.deviceId, event.isFromSource(InputDevice.SOURCE_MOUSE), event.buttonState)

	internal fun shouldDrop(action: Int, deviceId: Int, fromMouse: Boolean, buttonState: Int): Boolean {
		if (action == MotionEvent.ACTION_DOWN) {
			droppedDeviceId = if (fromMouse && isOtherButtonOnly(buttonState)) deviceId else null
			return droppedDeviceId != null
		}
		if (droppedDeviceId != deviceId) return false
		if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) droppedDeviceId = null
		return true
	}

	private fun isOtherButtonOnly(buttonState: Int): Boolean =
		buttonState and MotionEvent.BUTTON_PRIMARY == 0 &&
			buttonState and (MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY) != 0
}
