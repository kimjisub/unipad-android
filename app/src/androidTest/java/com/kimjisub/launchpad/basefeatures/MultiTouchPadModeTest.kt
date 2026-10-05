package com.kimjisub.launchpad.basefeatures

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Slide Mode off (first-install default): each pad takes its own touches, so a finger stays bound
 * to the pad it first pressed and dragging plays nothing new. Two simultaneous fingers (A) are
 * covered in this mode by [TouchPlaybackTest.twoSimultaneousFingersPlayBothPadsAndReleaseBoth].
 */
@RunWith(AndroidJUnit4::class)
class MultiTouchPadModeTest : MultiTouchPlaybackTest(slideMode = false) {
    /** D: dragging onto the neighbour keeps the first pad and requests no new sound. */
    @Test fun dragOntoNeighbourKeepsFirstPadAndPlaysNothingNew() {
        start()
        val first = Cell(3, 3)
        val finger = fingers.down(center(first))
        awaitPlays("Pad did not play", 1)
        awaitLights("Pad did not light", setOf(first))
        fingers.move(finger to center(Cell(3, 4)))
        assertStill("After dragging onto the neighbour", 1, setOf(first))
        fingers.up(finger)
        awaitLights("Lifting over the neighbour did not release the first pad", emptySet())
        assertStill("After lifting", 1, emptySet())
    }

    /** E: two fingers dragged together each stay on their own first pad. */
    @Test fun twoFingersDraggedTogetherEachKeepTheirFirstPad() {
        start()
        val upper = Cell(2, 2)
        val lower = Cell(5, 2)
        val a = fingers.down(center(upper))
        val b = fingers.down(center(lower))
        awaitPlays("Both fingers did not play", 2)
        awaitLights("Both pads did not light", setOf(upper, lower))
        fingers.move(a to center(Cell(2, 3)), b to center(Cell(5, 3)))
        assertStill("After dragging both fingers", 2, setOf(upper, lower))
        fingers.up(a)
        awaitLights("Lifting the first finger did not release only its pad", setOf(lower))
        fingers.up(b)
        awaitLights("Second pad stayed lit after release", emptySet())
        assertStill("After both fingers lifted", 2, emptySet())
    }
}
