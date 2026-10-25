package com.kimjisub.launchpad.basefeatures

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Real screen dispatch and virtual Launchpad packets share the same looping pad. */
@RunWith(AndroidJUnit4::class)
class SharedMidiPadTest : HeldPadTest() {
    @Test fun controllerReleaseStopsItsLoopAndKeepsScreenFeedback() = sharedPad(controllerFirst = true)

    @Test fun screenReleaseKeepsControllerHoldUntilItsPadReleases() = sharedPad(controllerFirst = false)

    private fun sharedPad(controllerFirst: Boolean) {
        startHeld()
        val launchpad = VirtualLaunchpad(screen)
        val packets = launchpad.packets
        val pad = Cell(2, 2)
        // Launchpad S note: row * 16 + column (the driver converts it to the grid coordinate).
        fun midi(down: Boolean) = screen.onMain { launchpad.driver.getSignal(9, -112, 34, if (down) 127 else 0) }
        val label = InstrumentationRegistry.getArguments().getString("captureLabel") ?: "after"
        val order = if (controllerFirst) "controller-first" else "screen-first"
        fun capture(stage: String) = screen.capture("shared-midi-$label-$order-$stage")
        try {
            val finger = fingers.down(center(pad))
            awaitPlays("Screen press did not play", 1)
            midi(true)
            awaitPlays("Virtual Launchpad press did not play", 2)
            awaitHeld("Both inputs must share one loop", setOf(pad))
            capture("both-held")
            packets.clear()
            if (controllerFirst) midi(false) else fingers.up(finger)
            settle()
            capture("one-released")
            assertStill("One input remains pressed", 2, setOf(pad))
            assertEquals("Only the input that started the current loop may stop it",
                if (controllerFirst) emptyList<Cell>() else listOf(pad), loopingCells())
            assertEquals("Partial release must send no LED-off packet for this pad", false,
                packets.contains(listOf(9, 144, 34, 0)))
            if (controllerFirst) fingers.up(finger) else midi(false)
            awaitHeld("Last release must stop the loop and press light", emptySet())
            assertHeldStill("Both inputs released", listOf(pad, pad), emptySet())
            screen.await("Last release did not send the controller LED-off packet") {
                packets.contains(listOf(9, 144, 34, 0))
            }
            capture("both-released")
        } finally {
            fingers.cancelRemaining()
            launchpad.close()
        }
    }
}
