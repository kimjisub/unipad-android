package com.kimjisub.launchpad.basefeatures

import android.util.Log
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A pad held by a finger while the play screen leaves the front, as when switching apps. With the
 * finger still down, the held pad's endless loop must stop and its press light go off; after the
 * screen comes back and the finger lifts, nothing plays or lights, and pressing the same pad again
 * plays it. Runs in both pad input modes.
 */
@RunWith(AndroidJUnit4::class)
class HeldPadLeavingScreenTest : HeldPadTest() {
    @Test fun heldPadStopsWhenAppGoesHome() = holdWhileHome(slide = false)

    @Test fun heldPadStopsWhenAppGoesHomeInSlideMode() = holdWhileHome(slide = true)

    private fun holdWhileHome(slide: Boolean) {
        lateinit var vm: PlayActivityViewModel
        lateinit var activity: android.app.Activity
        holdWhileLeaving("going Home", slide,
            leave = {
                vm = screen.vm()
                activity = screen.resumed()
                screen.goHome()
                screen.await("Home did not stop the play screen") { screen.onMain { !vm.screenVisible } }
            },
            back = {
                screen.bringToFront(activity)
                screen.await("Play screen did not come back") { screen.onMain { vm.screenVisible } }
            },
        )
    }

    private fun holdWhileLeaving(context: String, slide: Boolean, leave: () -> Unit, back: () -> Unit) {
        startHeld(slide)
        val mode = if (slide) "Slide Mode" else "pad mode"
        val vm = screen.vm()
        val held = Cell(3, 3)
        val finger = fingers.down(center(held))
        awaitHeld("Held pad did not play, light and loop before $context in $mode", setOf(held))
        val receivedBefore = received.events.size

        leave()
        var lit = emptySet<Cell>()
        var looping = emptyList<Cell>()
        screen.await({ "$context in $mode with the finger still down left the pad lit or looping (lit: $lit, looping: $looping)\n$received" }) {
            lit = litCells(vm)
            looping = loopingCells()
            lit.isEmpty() && looping.isEmpty()
        }
        val cancelled = received.events.drop(receivedBefore).any { it.action == MotionEvent.ACTION_CANCEL }
        Log.i(ReceivedInput.TAG, "$context in $mode with a pad held: system ${if (cancelled) "cancelled" else "did not cancel"} the touch")

        back()
        if (cancelled) fingers.forgetCancelled() else fingers.up(finger)
        assertHeldStill("After $context in $mode and lifting the finger", listOf(held), emptySet())

        val again = fingers.down(center(held))
        awaitHeld("Pressing the same pad after $context in $mode did not play, light and loop it", setOf(held))
        // The press light is drawn on a later frame than the state the check above reads.
        screen.device.waitForIdle()
        screen.capture("held-pad-again-after-${context.replace(' ', '-')}${if (slide) "-slide" else ""}")
        fingers.up(again)
        awaitHeld("Pad pressed again after $context in $mode stayed lit or looping", emptySet())
        assertHeldStill("After pressing the same pad again following $context in $mode", listOf(held, held), emptySet())
    }
}
