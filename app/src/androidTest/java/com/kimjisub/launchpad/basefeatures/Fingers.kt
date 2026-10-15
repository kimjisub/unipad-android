package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import org.junit.Assert.assertTrue

/**
 * One multi-finger touchscreen gesture injected through UiAutomation, so it reaches the app
 * through the same window dispatch as a real screen: ACTION_DOWN / POINTER_DOWN / MOVE /
 * POINTER_UP / UP / CANCEL. Pointer ids are reused lowest-first and kept in id order, as
 * touchscreen drivers do.
 */
class Fingers(private val screen: FeatureScreen) {
    private class Finger(val id: Int, var x: Float, var y: Float)

    /** The last lift or cancel, with the fingers it carried, kept for [redeliverEnd]. */
    private class Ended(val action: Int, val flags: Int, val fingers: List<Finger>)

    private var downAt = 0L
    private val fingers = mutableListOf<Finger>()
    private var ended: Ended? = null

    /** Puts a new finger down and returns its pointer id. */
    fun down(at: Point): Int {
        // The lowest free id has exactly `id` lower ids in use, so it also is its index.
        val id = generateSequence(0) { it + 1 }.first { id -> fingers.none { it.id == id } }
        fingers.add(id, Finger(id, at.x.toFloat(), at.y.toFloat()))
        if (fingers.size == 1) downAt = SystemClock.uptimeMillis()
        inject(if (fingers.size == 1) MotionEvent.ACTION_DOWN else pointerAction(MotionEvent.ACTION_POINTER_DOWN, id))
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

    /**
     * Lifts one finger. [cancelled] marks it FLAG_CANCELED, as Android 13+ reports a single
     * finger the system cancelled while others stay down.
     */
    fun up(id: Int, cancelled: Boolean = false) {
        val index = fingers.indexOf(finger(id))
        end(if (fingers.size == 1) MotionEvent.ACTION_UP else pointerAction(MotionEvent.ACTION_POINTER_UP, index),
            if (cancelled) MotionEvent.FLAG_CANCELED else 0)
        fingers.removeAt(index)
    }

    /**
     * Cancels every finger still down, as the system does when it takes the gesture away. Also
     * ends a gesture a failed assertion left down, so the next test starts clean.
     */
    fun cancelRemaining() {
        if (fingers.isEmpty()) return
        end(MotionEvent.ACTION_CANCEL)
        fingers.clear()
    }

    /** Forgets the fingers of a gesture the system already ended with its own cancel; injecting more would be refused. */
    fun forgetCancelled() = fingers.clear()

    /**
     * Delivers the last lift or cancel once more, straight to the resumed activity's window, with
     * [action], [flags] or only the first [pointers] fingers when given. The system refuses events
     * for fingers it already ended, so a duplicate end can only reach the app this way.
     */
    fun redeliverEnd(action: Int? = null, flags: Int? = null, pointers: Int? = null) {
        val last = checkNotNull(ended) { "No finger has lifted or been cancelled yet" }
        val event = obtain(action ?: last.action, flags ?: last.flags, last.fingers.take(pointers ?: last.fingers.size))
        try { screen.onMain { screen.resumed().dispatchTouchEvent(event) } }
        finally { event.recycle() }
    }

    private fun finger(id: Int) = fingers.first { it.id == id }

    private fun pointerAction(action: Int, index: Int) = action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    private fun end(action: Int, flags: Int = 0) {
        ended = Ended(action, flags, fingers.map { Finger(it.id, it.x, it.y) })
        inject(action, flags)
    }

    private fun inject(action: Int, flags: Int = 0) {
        val event = obtain(action, flags, fingers)
        try { assertTrue("Touch injection failed", screen.instrumentation.uiAutomation.injectInputEvent(event, true)) }
        finally { event.recycle() }
    }

    private fun obtain(action: Int, flags: Int, pointers: List<Finger>): MotionEvent {
        val properties = pointers.map { MotionEvent.PointerProperties().apply { id = it.id; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coordinates = pointers.map { MotionEvent.PointerCoords().apply { x = it.x; y = it.y; pressure = 1f; size = 1f } }
        return MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, pointers.size,
            properties.toTypedArray(), coordinates.toTypedArray(), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, flags)
    }
}
