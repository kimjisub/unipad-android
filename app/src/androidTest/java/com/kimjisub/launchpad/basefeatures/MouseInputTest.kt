package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.design.view.ChainView
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A mouse or trackpad on the play screen (Android laptops, Chromebooks, tablets with a mouse): the
 * left button plays a pad for as long as it is held and switches chains, the same as a finger.
 */
@RunWith(AndroidJUnit4::class)
class MouseInputTest : MultiTouchPlaybackTest(slideMode = false) {
    private lateinit var mouse: Mouse

    @After fun releaseMouse() {
        if (this::mouse.isInitialized) mouse.cancelRemaining()
    }

    private fun startWithMouse(slide: Boolean = false) {
        start(slide)
        mouse = Mouse(screen)
    }

    @Test fun leftClickPlaysPadForAsLongAsTheButtonIsHeld() {
        startWithMouse()
        val cell = Cell(3, 3)
        mouse.press(center(cell))
        awaitPlays("Left click on a pad did not request its sound", 1)
        awaitLights("Left click on a pad did not light it", setOf(cell))
        assertStill("While the left button is held", 1, setOf(cell))
        mouse.release()
        awaitLights("Releasing the left button did not release the pad", emptySet())
        assertStill("After releasing the left button", 1, emptySet())
    }

    @Test fun hoveringOverPadsPlaysNothing() {
        startWithMouse()
        for (cell in listOf(Cell(2, 2), Cell(2, 3), Cell(3, 3), Cell(4, 4))) mouse.hover(center(cell))
        assertStill("After moving over pads with no button held", 0, emptySet())
    }

    @Test fun leftDragWithSlideModeOffKeepsTheFirstPad() {
        startWithMouse(slide = false)
        val first = Cell(3, 3)
        mouse.press(center(first))
        awaitPlays("Left click on a pad did not request its sound", 1)
        mouse.drag(center(Cell(3, 4)))
        assertStill("After dragging onto the neighbour", 1, setOf(first))
        mouse.release()
        awaitLights("Releasing over the neighbour did not release the first pad", emptySet())
        assertStill("After releasing", 1, emptySet())
    }

    @Test fun leftDragWithSlideModePlaysEachPadPassed() {
        startWithMouse(slide = true)
        val cells = listOf(Cell(3, 3), Cell(3, 4), Cell(3, 5))
        mouse.press(center(cells[0]))
        awaitPlays("Left click on a pad did not request its sound", 1)
        awaitLights("Left click on a pad did not light it", setOf(cells[0]))
        for ((i, cell) in cells.withIndex().drop(1)) {
            mouse.drag(center(cell))
            awaitPlays("Dragging onto pad ${i + 1} did not play it", i + 1)
            awaitLights("Dragging did not move the light to pad ${i + 1}", setOf(cell))
        }
        mouse.release()
        awaitLights("Last pad stayed lit after releasing", emptySet())
        assertStill("After releasing", cells.size, emptySet())
    }

    @Test fun rightAndMiddlePressesLeaveNoPadPlaying() {
        startWithMouse()
        val cell = Cell(4, 4)
        for (button in listOf(MotionEvent.BUTTON_SECONDARY, MotionEvent.BUTTON_TERTIARY)) {
            mouse.press(center(cell), button)
            settle()
            mouse.release()
            assertStill("After pressing and releasing mouse button $button on a pad", 0, emptySet())
        }
    }

    /** The pointer comes from the pads, as it does when switching chain mid-performance. */
    @Test fun leftClickOnChainButtonSwitchesChain() {
        startWithMouse()
        val chains = screen.onMain {
            screen.views(screen.resumed().window.decorView).filterIsInstance<ChainView>().filter { it.width > 0 }.map(screen::bounds)
        }
        assertTrue("Both chains must be on screen", chains.size >= 2)
        assertEquals("Play must start on the first chain", 0, screen.onMain { screen.vm().chain.value })
        val second = Point(chains[1].centerX(), chains[1].centerY())
        mouse.hover(center(Cell(1, 7)))
        mouse.hover(second, steps = 8)
        mouse.press(second)
        mouse.release()
        screen.await("Left click on the second chain button did not switch chain") { screen.onMain { screen.vm().chain.value == 1 } }
    }
}
