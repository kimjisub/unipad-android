package com.kimjisub.launchpad.basefeatures

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Point
import android.graphics.Rect
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A pad held by a finger while the play screen changes size: the device turning between landscape
 * and portrait, and the window shrinking and growing back. The pads are laid out again, so the
 * held pad and its endless loop must stop, either at the change or when the finger lifts; nothing
 * may stay lit or looping, no other pad may play, and the first tap afterwards plays the pad under it.
 */
@RunWith(AndroidJUnit4::class)
class HeldPadLayoutChangeTest : HeldPadTest() {
    private var resized = false

    @After fun restoreScreen() {
        if (resized) screen.device.executeShellCommand("wm size reset")
        // Play's own orientation from the manifest; a failed check may have left no screen to turn.
        runCatching { screen.onMain { screen.resumed().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE } }
    }

    @Test fun heldPadStopsWhenScreenTurnsToPortrait() {
        startHeld()
        holdThrough("turning to portrait") { turn(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT) }
    }

    @Test fun heldPadStopsWhenScreenTurnsBackToLandscape() {
        startHeld()
        turn(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        holdThrough("turning back to landscape") { turn(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE) }
    }

    @Test fun heldPadStopsWhenWindowShrinks() {
        startHeld()
        val original = windowBounds()
        holdThrough("shrinking the window") { shrink() }
        restoreSize(original)
    }

    @Test fun heldPadStopsWhenWindowGrowsBack() {
        startHeld()
        val original = windowBounds()
        shrink()
        holdThrough("growing the window back") { restoreSize(original) }
    }

    /**
     * Holds a pad, checks it plays, lights and loops, makes [change] with the finger still down,
     * then lifts the finger and taps another pad in the new layout.
     */
    private fun holdThrough(context: String, change: () -> Unit) {
        val held = Cell(3, 3)
        val finger = fingers.down(center(held))
        awaitHeld("Held pad did not play, light and loop before $context", setOf(held))
        assertHeldStill("Before $context, with the finger down", listOf(held), setOf(held))
        val receivedBefore = received.events.size

        change()
        settle()
        val lit = litCells()
        val looping = loopingCells().toSet()
        val path = if (lit.isEmpty()) "released at the change" else "kept until the finger lifts"
        Log.i(ReceivedInput.TAG, "$context with a pad held: $path; system cancelled the touch: ${received.cancelled()}")
        assertTrue("After $context the pad must be either still held or fully released (lit: $lit, looping: $looping)\n$received",
            lit == looping && (lit.isEmpty() || lit == setOf(held)))
        assertHeldStill("After $context, with the finger still down", listOf(held), lit)

        // A gesture the system already cancelled has no finger left to lift.
        if (received.events.drop(receivedBefore).any { it.action == android.view.MotionEvent.ACTION_CANCEL }) fingers.cancelRemaining()
        else fingers.up(finger)
        awaitHeld("Held pad still lit or looping after the finger lifted, following $context", emptySet())
        assertHeldStill("After lifting the finger held through $context", listOf(held), emptySet())

        val next = Cell(5, 2)
        val tap = fingers.down(center(next))
        awaitHeld("First tap after $context did not play and light the pad under it", setOf(next))
        // The press light is drawn on a later frame than the state the check above reads.
        screen.device.waitForIdle()
        screen.capture("held-pad-after-${context.replace(' ', '-')}")
        fingers.up(tap)
        awaitHeld("Tapped pad stayed lit or looping after $context", emptySet())
        assertHeldStill("After the first tap following $context", listOf(held, next), emptySet())
    }

    private fun turn(requested: Int, orientation: Int) {
        val activity = screen.resumed()
        relayout("Screen did not turn to orientation $orientation") {
            screen.onMain { activity.requestedOrientation = requested }
            screen.await("Requested orientation did not reach the play screen") {
                screen.onMain { activity.resources.configuration.orientation == orientation }
            }
        }
    }

    /** Shrinks the display to two thirds of its size, so the play window shrinks with it. */
    private fun shrink() {
        val physical = Regex("Physical size: (\\d+)x(\\d+)").find(screen.device.executeShellCommand("wm size"))
            ?.destructured?.let { (w, h) -> Point(w.toInt(), h.toInt()) } ?: throw AssertionError("No physical display size")
        val original = windowBounds()
        relayout("Pads were not laid out again after the window shrank") {
            resized = true
            screen.device.executeShellCommand("wm size ${physical.x * 2 / 3}x${physical.y * 2 / 3}")
            screen.await("Play window did not shrink") { windowBounds().let { it.width() < original.width() && it.height() < original.height() } }
        }
    }

    /** Returns the display to its own size and checks the play window is back to [original]. */
    private fun restoreSize(original: Rect) {
        relayout("Pads were not laid out again after the window grew back") {
            screen.device.executeShellCommand("wm size reset")
            screen.await({ "Play window did not return to $original (now ${windowBounds()})" }) { windowBounds() == original }
        }
        resized = false
        assertFalse("Display size override remained", screen.device.executeShellCommand("wm size").contains("Override size"))
    }

    /** Runs [change] and waits until the pads are laid out again in a different place. */
    private fun relayout(message: String, change: () -> Unit) {
        val before = screen.padBounds()
        change()
        screen.await(message) { screen.ready() && screen.padBounds().let { it.size == before.size && it != before } }
        screen.instrumentation.waitForIdleSync()
    }

    private fun windowBounds(): Rect = screen.onMain { screen.bounds(screen.resumed().window.decorView) }
}
