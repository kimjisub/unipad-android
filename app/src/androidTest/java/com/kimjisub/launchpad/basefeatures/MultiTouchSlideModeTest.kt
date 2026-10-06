package com.kimjisub.launchpad.basefeatures

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/** Slide Mode on: a finger dragged onto another pad releases the pad it left and presses the new one. */
@RunWith(AndroidJUnit4::class)
class MultiTouchSlideModeTest : MultiTouchPlaybackTest(slideMode = true) {
    /** A: two different pads pressed at once. */
    @Test fun twoSimultaneousFingersPlayBothPadsAndReleaseBoth() {
        start()
        val cells = setOf(Cell(0, 1), Cell(0, 2))
        val ids = cells.map { fingers.down(center(it)) }
        awaitPlays("Both fingers did not play", 2)
        awaitLights("Both pressed lights were not shown at once", cells)
        ids.reversed().forEach(fingers::up)
        awaitLights("Released fingers still light pads", emptySet())
        assertStill("After releasing both", 2, emptySet())
    }

    /** D: dragging onto the neighbour releases the old pad, presses the new one and plays it. */
    @Test fun dragOntoNeighbourMovesPressAndPlaysNewPad() {
        start()
        val first = Cell(3, 3)
        val next = Cell(3, 4)
        val finger = fingers.down(center(first))
        awaitPlays("Pad did not play", 1)
        awaitLights("Pad did not light", setOf(first))
        fingers.move(finger to center(next))
        awaitPlays("Dragging onto the neighbour did not play it", 2)
        awaitLights("Dragging did not move the light to the neighbour", setOf(next))
        fingers.up(finger)
        awaitLights("Neighbour stayed lit after release", emptySet())
        assertStill("After lifting", 2, emptySet())
    }

    /** E: two fingers dragged together each move their own press. */
    @Test fun twoFingersDraggedTogetherEachMoveTheirOwnPad() {
        start()
        val upper = Cell(2, 2)
        val lower = Cell(5, 2)
        val upperNext = Cell(2, 3)
        val lowerNext = Cell(5, 3)
        val a = fingers.down(center(upper))
        val b = fingers.down(center(lower))
        awaitPlays("Both fingers did not play", 2)
        awaitLights("Both pads did not light", setOf(upper, lower))
        fingers.move(a to center(upperNext), b to center(lowerNext))
        awaitPlays("Each dragged finger did not play its new pad", 4)
        awaitLights("Each finger's light did not follow it", setOf(upperNext, lowerNext))
        fingers.up(a)
        awaitLights("Lifting the first finger did not release only its pad", setOf(lowerNext))
        fingers.up(b)
        awaitLights("Second pad stayed lit after release", emptySet())
        assertStill("After both fingers lifted", 4, emptySet())
    }

    @Test fun fiveSimultaneousFingersPlayAndLightEveryPad() = fiveFingerChord()

    @Test fun heldPadStaysLitWhileAnotherPadIsTappedRepeatedly() = holdAndTap()

    @Test fun liftingOneOfTwoFingersReleasesOnlyItsPad() = liftOneOfTwo()

    @Test fun palmTouchingEdgeWhilePlayingPlaysNothingAndPadsKeepPlaying() = palmOnEdgeWhilePlaying()

    @Test fun palmRestingOnEdgeBeforePlayingPlaysNothingAndPadsStillPlay() = palmOnEdgeBeforePlaying()
}
