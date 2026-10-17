package com.kimjisub.launchpad.basefeatures

import android.util.Log
import android.view.MotionEvent
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A pad held by a finger while the play screen leaves the front: paused behind another activity
 * that keeps it visible, or stopped by Home. With the finger still down, the held pad's endless
 * loop must stop and its press light go off; after the screen comes back and the finger lifts,
 * nothing plays or lights, and pressing the same pad again plays it. Runs in both pad input modes.
 */
@RunWith(AndroidJUnit4::class)
class HeldPadLeavingScreenTest : HeldPadTest() {
    @Test fun heldPadStopsWhenPlayScreenIsPaused() = holdWhilePaused(slide = false)

    @Test fun heldPadStopsWhenPlayScreenIsPausedInSlideMode() = holdWhilePaused(slide = true)

    @Test fun heldPadStopsWhenAppGoesHome() = holdWhileHome(slide = false)

    @Test fun heldPadStopsWhenAppGoesHomeInSlideMode() = holdWhileHome(slide = true)

    /** ActivityScenario pauses the play screen by opening a floating activity over it. */
    private fun holdWhilePaused(slide: Boolean) = holdWhileLeaving("pausing the play screen", slide,
        leave = { _ -> play.moveToState(Lifecycle.State.STARTED) },
        back = { play.moveToState(Lifecycle.State.RESUMED) },
    )

    private fun holdWhileHome(slide: Boolean) {
        lateinit var activity: android.app.Activity
        holdWhileLeaving("going Home", slide,
            leave = { vm ->
                activity = screen.resumed()
                screen.goHome { screen.onMain { !vm.screenVisible } }
            },
            back = { screen.bringToFront(activity) },
        )
    }

    /**
     * [leave] gets the play screen's state, which [FeatureScreen.vm] cannot reach while the screen
     * is not resumed. After [back], the play window must be resumed and focused again before the
     * finger lifts, so the lift reaches it.
     */
    private fun holdWhileLeaving(
        context: String,
        slide: Boolean,
        leave: (PlayActivityViewModel) -> Unit,
        back: () -> Unit,
    ) {
        startHeld(slide)
        val mode = if (slide) "Slide Mode" else "pad mode"
        val vm = screen.vm()
        val held = Cell(3, 3)
        val finger = fingers.down(center(held))
        awaitHeld("Held pad did not play, light and loop before $context in $mode", setOf(held))
        val receivedBefore = received.events.size

        leave(vm)
        var lit = emptySet<Cell>()
        var looping = emptyList<Cell>()
        screen.await({ "$context in $mode with the finger still down left the pad lit or looping (lit: $lit, looping: $looping)\n$received" }) {
            lit = litCells(vm)
            looping = loopingCells()
            lit.isEmpty() && looping.isEmpty()
        }
        // The screen can let go of the pad before a system cancel queued behind it is delivered.
        settle()
        val cancelled = received.events.drop(receivedBefore).any { it.action == MotionEvent.ACTION_CANCEL }
        Log.i(ReceivedInput.TAG, "$context in $mode with a pad held: system ${if (cancelled) "cancelled" else "did not cancel"} the touch")

        back()
        screen.awaitActivity(PlayActivity::class.java)
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
