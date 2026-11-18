package com.kimjisub.launchpad.basefeatures

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.kimjisub.launchpad.viewmodel.PlayMode
import java.io.File

/** Real screen gestures and controller packets while the options panel covers held looping pads. */
@RunWith(AndroidJUnit4::class)
class OptionWindowInputTest : HeldPadTest() {
    private val label get() = InstrumentationRegistry.getArguments().getString("captureLabel") ?: "after"

    private fun heldFinger(slide: Boolean, menuFinger: Boolean = false) {
        startHeld(slide)
        val pad = Cell(3, 3)
        val next = Cell(3, 4)
        val finger = fingers.down(center(pad))
        awaitHeld("Finger must start a loop", setOf(pad))
        if (menuFinger) {
            val opener = fingers.down(screen.node(By.desc(screen.text(R.string.menu))).visibleCenter)
            fingers.up(opener)
        } else screen.device.pressBack()
        screen.await("Menu or Back must open options") { screen.onMain { screen.vm().isOptionWindowVisible } }
        settle()
        screen.capture("options-$label-slide-$slide-open")
        assertHeldStill("Opening options must release the finger now", listOf(pad), emptySet())
        fingers.move(finger to center(next))
        assertHeldStill("Dragging under options must not press a pad", listOf(pad), emptySet())
        screen.device.pressBack()
        screen.await("Back must close options") { screen.onMain { !screen.vm().isOptionWindowVisible } }
        fingers.move(finger to center(pad))
        assertHeldStill("The old finger stays cancelled after closing options", listOf(pad), emptySet())
        fingers.up(finger)
        val fresh = fingers.down(center(next))
        awaitHeld("A fresh finger must play normally", setOf(next))
        fingers.up(fresh)
        assertHeldStill("Fresh input must release normally", listOf(pad, next), emptySet())
    }

    @Test fun openingOptionsReleasesAHeldFingerInPadMode() = heldFinger(false)
    @Test fun openingOptionsReleasesAHeldFingerInSlideMode() = heldFinger(true)
    @Test fun aSecondFingerOpeningOptionsReleasesTheFirstFinger() = heldFinger(true, menuFinger = true)

    @Test fun controllerFunctionKeyReleasesHeldPadsAndKeepsPanelControls() {
        startHeld(slide = true)
        val pad = Cell(2, 2)
        val next = Cell(3, 3)
        val launchpad = VirtualLaunchpad(screen)
        fun midi(note: Int, down: Boolean) = screen.onMain { launchpad.driver.getSignal(9, -112, note, if (down) 127 else 0) }
        fun options() = screen.onMain {
            launchpad.driver.getSignal(11, -80, 107, 127)
            launchpad.driver.getSignal(11, -80, 107, 0)
        }
        try {
            val finger = fingers.down(center(pad))
            awaitPlays("Finger must play", 1)
            midi(34, true)
            awaitPlays("Controller must play", 2)
            options()
            assertTrue(screen.onMain { screen.vm().isOptionWindowVisible })
            settle()
            screen.capture("options-$label-controller-open")
            assertHeldStill("Function key must release both manual inputs", listOf(pad, pad), emptySet())
            midi(34, false)
            midi(51, true)
            midi(51, false)
            midi(24, true)
            midi(24, false)
            assertEquals("Chain button must not change chain under options", 0, screen.onMain { screen.vm().chain.value })
            fingers.move(finger to center(next))
            assertHeldStill("Panel must ignore pad input", listOf(pad, pad), emptySet())
            options()
            assertFalse(screen.onMain { screen.vm().isOptionWindowVisible })
            fingers.up(finger)
            midi(51, true)
            awaitHeld("New controller press must work after closing options", setOf(next))
            midi(51, false)
            assertHeldStill("Controller release must work", listOf(pad, pad, next), emptySet())
        } finally {
            launchpad.close()
        }
    }
    @Test fun optionsKeepAutoplayHeldPadsAndContinueTheNextCommand() {
        File(screen.pack(), "autoPlay").writeText("on 1 1\ndelay 3000\noff 1 1\non 2 1\ndelay 60000\n")
        startHeld()
        screen.onMain {
            screen.vm().switchPlayMode(PlayMode.AutoPlay)
            // Entering a mode restores the pack's default lights; this test observes press feedback.
            screen.vm().scbFeedbackLight.setChecked(true)
        }
        awaitHeld("Autoplay must hold its first pad", setOf(Cell(0, 0)))
        screen.device.pressBack()
        screen.await("Back must open options") { screen.onMain { screen.vm().isOptionWindowVisible } }
        assertEquals("Opening options must keep the autoplay loop", listOf(Cell(0, 0)), loopingCells())
        assertTrue(screen.onMain { screen.vm().isAutoPlayPlaying })
        assertFalse(screen.onMain { screen.vm().autoPlayRunner!!.suspended })
        awaitHeld("Autoplay must reach the next command while options remain open", setOf(Cell(1, 0)))
        assertTrue(screen.onMain { screen.vm().isOptionWindowVisible })
        screen.device.pressBack()
        screen.await("Back must close options") { screen.onMain { !screen.vm().isOptionWindowVisible } }
        assertHeldStill("Closing options must keep the next autoplay loop", listOf(Cell(0, 0), Cell(1, 0)), setOf(Cell(1, 0)))
    }

}
