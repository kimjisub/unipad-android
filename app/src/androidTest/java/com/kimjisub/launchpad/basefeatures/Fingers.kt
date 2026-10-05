package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import org.junit.Assert.assertTrue

/**
 * One multi-finger touchscreen gesture injected through UiAutomation, so it reaches the app
 * through the same window dispatch as a real screen: ACTION_DOWN / POINTER_DOWN / MOVE /
 * POINTER_UP / UP. Pointer ids are reused lowest-first, as touchscreen drivers do.
 */
class Fingers(private val screen: FeatureScreen) {
    private class Finger(val id: Int, var x: Float, var y: Float)

    private val downAt = SystemClock.uptimeMillis()
    private val fingers = mutableListOf<Finger>()

    /** Puts a new finger down and returns its pointer id. */
    fun down(at: Point): Int {
        val id = generateSequence(0) { it + 1 }.first { id -> fingers.none { it.id == id } }
        fingers += Finger(id, at.x.toFloat(), at.y.toFloat())
        inject(if (fingers.size == 1) MotionEvent.ACTION_DOWN else pointerAction(MotionEvent.ACTION_POINTER_DOWN, fingers.lastIndex))
        return id
    }

    /** Moves the given fingers together in [steps] events, as one stroke. */
    fun move(vararg targets: Pair<Int, Point>, steps: Int = 5) {
        val starts = targets.map { (id, _) -> finger(id).let { it.x to it.y } }
        for (step in 1..steps) {
            targets.forEachIndexed { i, (id, to) ->
                val (fromX, fromY) = starts[i]
                finger(id).apply {
                    x = fromX + (to.x - fromX) * step / steps
                    y = fromY + (to.y - fromY) * step / steps
                }
            }
            inject(MotionEvent.ACTION_MOVE)
        }
    }

    fun up(id: Int) {
        val index = fingers.indexOf(finger(id))
        inject(if (fingers.size == 1) MotionEvent.ACTION_UP else pointerAction(MotionEvent.ACTION_POINTER_UP, index))
        fingers.removeAt(index)
    }

    /** Ends the gesture if a failed assertion left fingers down, so the next test starts clean. */
    fun cancelRemaining() {
        if (fingers.isEmpty()) return
        inject(MotionEvent.ACTION_CANCEL)
        fingers.clear()
    }

    private fun finger(id: Int) = fingers.first { it.id == id }

    private fun pointerAction(action: Int, index: Int) = action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    private fun inject(action: Int) {
        val properties = fingers.map { MotionEvent.PointerProperties().apply { id = it.id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coordinates = fingers.map { MotionEvent.PointerCoords().apply { x = it.x; y = it.y; pressure = 1f; size = 1f } }
        val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, fingers.size,
            properties.toTypedArray(), coordinates.toTypedArray(), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        try { assertTrue("Touch injection failed", screen.instrumentation.uiAutomation.injectInputEvent(event, true)) }
        finally { event.recycle() }
    }
}
