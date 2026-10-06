package com.kimjisub.launchpad.tool

import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import android.view.MotionEvent.BUTTON_PRIMARY
import android.view.MotionEvent.BUTTON_SECONDARY
import android.view.MotionEvent.BUTTON_TERTIARY
import org.junit.Assert.assertEquals
import org.junit.Test

class PrimaryButtonFilterTest {

	private val mouse = 3
	private val touchscreen = 7

	private fun PrimaryButtonFilter.gesture(deviceId: Int, fromMouse: Boolean, buttonState: Int, end: Int = ACTION_UP) =
		listOf(ACTION_DOWN, ACTION_MOVE, end).map { shouldDrop(it, deviceId, fromMouse, buttonState) }

	@Test
	fun rightAndMiddlePressesAreDroppedForTheWholeGesture() {
		val filter = PrimaryButtonFilter()
		assertEquals(listOf(true, true, true), filter.gesture(mouse, true, BUTTON_SECONDARY))
		assertEquals(listOf(true, true, true), filter.gesture(mouse, true, BUTTON_TERTIARY, end = ACTION_CANCEL))
	}

	@Test
	fun leftPressesFingersAndButtonlessMouseEventsPass() {
		val filter = PrimaryButtonFilter()
		assertEquals(listOf(false, false, false), filter.gesture(mouse, true, BUTTON_PRIMARY))
		assertEquals(listOf(false, false, false), filter.gesture(mouse, true, BUTTON_PRIMARY or BUTTON_SECONDARY))
		assertEquals(listOf(false, false, false), filter.gesture(mouse, true, 0))
		assertEquals(listOf(false, false, false), filter.gesture(touchscreen, false, 0))
	}

	@Test
	fun aLeftClickAfterARightClickIsNotDropped() {
		val filter = PrimaryButtonFilter()
		filter.gesture(mouse, true, BUTTON_SECONDARY)
		assertEquals(listOf(false, false, false), filter.gesture(mouse, true, BUTTON_PRIMARY))
	}

	@Test
	fun otherDevicesPassWhileARightPressIsHeld() {
		val filter = PrimaryButtonFilter()
		assertEquals(true, filter.shouldDrop(ACTION_DOWN, mouse, true, BUTTON_SECONDARY))
		assertEquals(false, filter.shouldDrop(ACTION_MOVE, touchscreen, false, 0))
		assertEquals(true, filter.shouldDrop(ACTION_UP, mouse, true, 0))
	}
}
