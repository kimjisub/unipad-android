package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import org.junit.Assert.assertTrue

/**
 * Mouse clicks injected through UiAutomation, so they reach the app through the same window
 * dispatch as a real mouse: ACTION_DOWN carrying the pressed button, then ACTION_UP with none.
 */
class Mouse(private val screen: FeatureScreen) {
    fun click(at: Point, button: Int) {
        val downAt = SystemClock.uptimeMillis()
        inject(downAt, MotionEvent.ACTION_DOWN, button, at)
        inject(downAt, MotionEvent.ACTION_UP, 0, at)
    }

    private fun inject(downAt: Long, action: Int, buttonState: Int, at: Point) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
        val coordinates = MotionEvent.PointerCoords().apply { x = at.x.toFloat(); y = at.y.toFloat(); pressure = 1f; size = 1f }
        val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, 1, arrayOf(properties), arrayOf(coordinates),
            0, buttonState, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        try { assertTrue("Mouse injection failed", screen.instrumentation.uiAutomation.injectInputEvent(event, true)) }
        finally { event.recycle() }
    }
}
