package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import org.junit.Assert.assertTrue

/**
 * A mouse on the play screen, injected through UiAutomation as a USB mouse reaches an app: hover
 * moves while no button is held, then ACTION_DOWN, ACTION_BUTTON_PRESS, ACTION_MOVE while held,
 * ACTION_BUTTON_RELEASE and ACTION_UP, all from the mouse source with the mouse tool type.
 */
class Mouse(private val screen: FeatureScreen) {
    private var x = 0f
    private var y = 0f
    private var downAt = 0L
    private var buttons = 0

    /** Moves the pointer to [at] with no button held. */
    fun hover(at: Point, steps: Int = 3) = stroke(at, steps, MotionEvent.ACTION_HOVER_MOVE)

    /** Moves to [at] and presses [button] there. */
    fun press(at: Point, button: Int = MotionEvent.BUTTON_PRIMARY) {
        hover(at)
        downAt = SystemClock.uptimeMillis()
        buttons = button
        inject(MotionEvent.ACTION_DOWN)
        inject(MotionEvent.ACTION_BUTTON_PRESS, actionButton = button)
    }

    /** Moves to [at] with the button still held. */
    fun drag(at: Point, steps: Int = 5) = stroke(at, steps, MotionEvent.ACTION_MOVE)

    fun release() {
        val button = buttons
        buttons = 0
        inject(MotionEvent.ACTION_BUTTON_RELEASE, actionButton = button)
        inject(MotionEvent.ACTION_UP)
        inject(MotionEvent.ACTION_HOVER_MOVE)
    }

    /** Ends a press a failed assertion left held, so the next test starts clean. */
    fun cancelRemaining() {
        if (buttons == 0) return
        buttons = 0
        inject(MotionEvent.ACTION_CANCEL)
    }

    private fun stroke(to: Point, steps: Int, action: Int) {
        val fromX = x
        val fromY = y
        for (step in 1..steps) {
            x = fromX + (to.x - fromX) * step / steps
            y = fromY + (to.y - fromY) * step / steps
            inject(action)
        }
    }

    private fun inject(action: Int, actionButton: Int = 0) {
        val properties = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
        val coordinates = MotionEvent.PointerCoords().apply { x = this@Mouse.x; y = this@Mouse.y; pressure = if (buttons != 0) 1f else 0f }
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(if (buttons != 0) downAt else now, now, action, 1,
            arrayOf(properties), arrayOf(coordinates), 0, buttons, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
        try {
            if (actionButton != 0) setActionButton(event, actionButton)
            assertTrue("Mouse injection failed", screen.instrumentation.uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }

    private companion object {
        // MotionEvent.setActionButton is a test API; a button press or release event carries the button it is about.
        private val setActionButton = MotionEvent::class.java.getMethod("setActionButton", Int::class.javaPrimitiveType)

        fun setActionButton(event: MotionEvent, button: Int) {
            setActionButton.invoke(event, button)
        }
    }
}
