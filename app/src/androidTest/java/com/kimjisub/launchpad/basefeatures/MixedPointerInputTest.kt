package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import android.util.Log
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A mouse and a finger on the play screen in the same performance (a tablet or Chromebook with a
 * mouse): one holds a pad while the other presses and lifts from a second pad, in both orders and
 * with Slide Mode off and on. The finger comes from a separate [Touchscreen] device, as on real
 * hardware. Android may end the first device's gesture in a window when another device starts one
 * there; then the first pad must stop at that moment. Otherwise both pads play until each is
 * lifted. Either way, lifting one never stops the other's pad nor leaves its own playing.
 */
@RunWith(AndroidJUnit4::class)
class MixedPointerInputTest : HeldPadTest() {
    private class Pointer(val name: String, val toolType: Int, val press: (Point) -> Unit, val lift: () -> Unit)

    private lateinit var mouse: Mouse
    private lateinit var touchscreen: Touchscreen

    @After fun releaseMouseAndTouchscreen() {
        try {
            if (this::mouse.isInitialized) mouse.cancelRemaining()
        } finally {
            if (this::touchscreen.isInitialized) touchscreen.close()
        }
    }

    @Test fun fingerPlaysAndLiftsWhileMouseHoldsPadWithSlideModeOff() = holdWhileOtherPlays(slide = false, mouseFirst = true)

    @Test fun fingerPlaysAndLiftsWhileMouseHoldsPadWithSlideModeOn() = holdWhileOtherPlays(slide = true, mouseFirst = true)

    @Test fun mousePlaysAndReleasesWhileFingerHoldsPadWithSlideModeOff() = holdWhileOtherPlays(slide = false, mouseFirst = false)

    @Test fun mousePlaysAndReleasesWhileFingerHoldsPadWithSlideModeOn() = holdWhileOtherPlays(slide = true, mouseFirst = false)

    /** The first pointer holds a pad, the second presses and lifts from another, then the first lifts. */
    private fun holdWhileOtherPlays(slide: Boolean, mouseFirst: Boolean) {
        startHeld(slide)
        mouse = Mouse(screen)
        touchscreen = Touchscreen(screen)
        val byMouse = Pointer("mouse", MotionEvent.TOOL_TYPE_MOUSE, { mouse.press(it) }, { mouse.release() })
        var slot = -1
        val byFinger = Pointer("finger", MotionEvent.TOOL_TYPE_FINGER, { slot = touchscreen.down(it) }, { touchscreen.up(slot) })
        val (first, second) = if (mouseFirst) byMouse to byFinger else byFinger to byMouse
        val held = Cell(3, 3)
        val other = Cell(3, 5)

        first.press(center(held))
        awaitHeld("The ${first.name} did not play, light and loop its pad", setOf(held))
        val beforeSecond = received.events.size
        second.press(center(other))
        screen.await({ "The ${second.name} did not play and light its pad while the ${first.name} held another\n$received" }) {
            audio.plays.size == 2 && other in litCells()
        }
        settle()
        val ended = endedBySystem(first, received.events.drop(beforeSecond))
        Log.i(ReceivedInput.TAG, "${second.name} pressed while the ${first.name} held a pad: " +
            if (ended) "Android ended the ${first.name}'s gesture" else "both kept")
        val firstHeld = if (ended) emptySet() else setOf(held)
        assertHeldStill("While the ${second.name} holds its pad" + if (ended) " after Android ended the ${first.name}'s" else "",
            listOf(held, other), firstHeld + other)

        second.lift()
        awaitHeld("Lifting the ${second.name} changed the ${first.name}'s pad or left its own playing", firstHeld)
        assertHeldStill("After lifting the ${second.name}", listOf(held, other), firstHeld)
        first.lift()
        awaitHeld("The ${first.name}'s pad stayed lit or looping after lifting", emptySet())
        assertHeldStill("After lifting the ${first.name}", listOf(held, other), emptySet())
        assertPointers(first, ended)
    }

    /**
     * True when the window received ACTION_CANCEL for [first] before the second device's press,
     * as Android does when it does not stream two devices to one window. A cancel of anything else,
     * or at another time, is not that and fails the check.
     */
    private fun endedBySystem(first: Pointer, events: List<ReceivedInput.Event>): Boolean {
        val cancels = events.withIndex().filter { it.value.action == MotionEvent.ACTION_CANCEL }
        if (cancels.isEmpty()) return false
        val secondDown = events.indexOfFirst { it.action == MotionEvent.ACTION_DOWN && it.actor.toolType != first.toolType }
        assertTrue("Only the ${first.name}'s gesture may be cancelled, once, before the other device's press\n$received",
            cancels.size == 1 && cancels[0].value.actor.toolType == first.toolType && cancels[0].index < secondDown)
        return true
    }

    /** One mouse pointer and one touchscreen finger went down; each ended by a lift, or [first] by the system's cancel. */
    private fun assertPointers(first: Pointer, ended: Boolean) {
        fun pointers(vararg actions: Int) = received.events.filter { it.action in actions }
            .map { it.actor.toolType to (it.deviceId == touchscreen.deviceId) }.sortedBy { it.first }
        val both = listOf(MotionEvent.TOOL_TYPE_FINGER to true, MotionEvent.TOOL_TYPE_MOUSE to false).sortedBy { it.first }
        assertEquals("Pointers that went down (tool type to from the touchscreen)\n$received", both,
            pointers(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN))
        assertEquals("Pointers that ended (tool type to from the touchscreen)\n$received", both,
            pointers(MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL))
        assertEquals("Cancelled pointers\n$received", if (ended) listOf(first.toolType) else emptyList<Int>(),
            received.events.filter { it.action == MotionEvent.ACTION_CANCEL }.map { it.actor.toolType })
    }
}
