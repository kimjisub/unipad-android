package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import android.graphics.Rect
import android.os.SystemClock
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.kimjisub.design.view.ChainView
import com.kimjisub.design.view.PadView
import com.kimjisub.design.view.SlideTouchOverlayView
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.manager.PreferenceManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before

/**
 * Several fingers on the play screen, injected as real touchscreen events, judged by the sound
 * requests the runner made and the PRESSED light channel. Runs once per pad input mode: pads
 * taking their own touches (Slide Mode off, the first-install default) and the Slide Mode layer.
 * Cases shared by both modes live here; drags differ by design and live in each subclass. Each
 * subclass declares every test itself, so the `@Test` count in the sources matches the tests the
 * runner discovers (scripts/check_connected_results.py).
 */
abstract class MultiTouchPlaybackTest(private val slideMode: Boolean) : PlaybackScreenTest() {
    protected data class Cell(val x: Int, val y: Int)

    protected lateinit var fingers: Fingers
    private lateinit var prefs: PreferenceManager
    private var savedSlideMode = false

    @Before fun setSlideMode() {
        prefs = PreferenceManager(screen.context)
        savedSlideMode = prefs.slideMode
        prefs.slideMode = slideMode
    }

    @After fun restoreSlideMode() {
        try {
            if (this::fingers.isInitialized) fingers.cancelRemaining()
        } finally {
            if (this::prefs.isInitialized) prefs.slideMode = savedSlideMode
        }
    }

    /**
     * Opens the pack with the press light on. A pack with keyLED starts with it off; the option is
     * set on the screen's state rather than through the options panel, whose open/close by Back is
     * not what these tests check.
     */
    protected fun start() {
        screen.openPlay()
        screen.onMain { screen.vm().scbFeedbackLight.setChecked(true) }
        assertTrue("Press light option did not turn on", screen.onMain { screen.vm().scbFeedbackLight.isChecked() })
        val overlay = screen.onMain {
            screen.views(screen.resumed().window.decorView).any { it is SlideTouchOverlayView }
        }
        assertEquals("Slide Mode layer visibility must match the mode under test", slideMode, overlay)
        fingers = Fingers(screen)
    }

    protected fun center(cell: Cell): Point = screen.padBounds()[cell.x * GRID + cell.y].let { Point(it.centerX(), it.centerY()) }

    protected fun awaitLights(message: String, lit: Set<Cell>) {
        var last = emptySet<Cell>()
        screen.await({ "$message (expected lit: $lit, lit: $last)" }) { litCells().also { last = it } == lit }
    }

    private fun litCells(): Set<Cell> = screen.onMain {
        val channels = screen.vm().channelManager
        (0 until GRID).flatMap { x -> (0 until GRID).map { y -> Cell(x, y) } }
            .filter { channels.get(it.x, it.y)?.channel == Channel.PRESSED }
            .toSet()
    }

    protected fun awaitPlays(message: String, count: Int) {
        var last = 0
        screen.await({ "$message (expected $count, got $last)" }) { audio.plays.size.also { last = it } == count }
    }

    /** For checks that something did NOT happen: lets queued input and runner work finish first. */
    protected fun settle() {
        screen.instrumentation.waitForIdleSync()
        SystemClock.sleep(SETTLE_MS)
        screen.instrumentation.waitForIdleSync()
    }

    protected fun assertStill(message: String, plays: Int, lit: Set<Cell>) {
        settle()
        assertEquals("$message: sound requests", plays, audio.plays.size)
        assertEquals("$message: pressed lights", lit, litCells())
    }

    /** B: a five-finger chord plays every pad and lights each one. */
    protected fun fiveFingerChord() {
        start()
        val cells = listOf(Cell(2, 1), Cell(2, 3), Cell(3, 5), Cell(5, 2), Cell(6, 6))
        val ids = cells.map { fingers.down(center(it)) }
        awaitPlays("Not every finger requested its sound", cells.size)
        awaitLights("Not every pressed pad lit", cells.toSet())
        screen.capture(if (slideMode) "five-fingers-slide" else "five-fingers")
        for (i in listOf(2, 0, 4, 1, 3)) fingers.up(ids[i])
        awaitLights("Released pads stayed lit", emptySet())
        assertStill("After releasing the chord", cells.size, emptySet())
    }

    /** C: one finger holds a pad while another taps a second pad three times. */
    protected fun holdAndTap() {
        start()
        val held = Cell(3, 3)
        val tapped = Cell(3, 5)
        val holder = fingers.down(center(held))
        awaitPlays("Held pad did not play", 1)
        awaitLights("Held pad did not light", setOf(held))
        repeat(3) { tap ->
            val tapper = fingers.down(center(tapped))
            awaitPlays("Tap ${tap + 1} did not play", tap + 2)
            awaitLights("Tap ${tap + 1} did not light beside the held pad", setOf(held, tapped))
            fingers.up(tapper)
            awaitLights("Tap ${tap + 1} release turned off the wrong pad", setOf(held))
        }
        fingers.up(holder)
        awaitLights("Held pad stayed lit after release", emptySet())
        assertStill("After releasing the held pad", 4, emptySet())
    }

    /** F: of two held pads, lifting the first finger releases only its pad. */
    protected fun liftOneOfTwo() {
        start()
        val first = Cell(2, 2)
        val second = Cell(5, 5)
        val a = fingers.down(center(first))
        val b = fingers.down(center(second))
        awaitPlays("Both fingers did not play", 2)
        awaitLights("Both pads did not light", setOf(first, second))
        fingers.up(a)
        awaitLights("Lifting the first finger did not release only its pad", setOf(second))
        assertStill("While the second finger is still down", 2, setOf(second))
        fingers.up(b)
        awaitLights("Second pad stayed lit after release", emptySet())
        assertStill("After both fingers lifted", 2, emptySet())
    }

    /** I: a palm landing on the screen margin while a pad is held plays nothing and blocks no pad. */
    protected fun palmOnEdgeWhilePlaying() {
        start()
        val held = Cell(2, 2)
        val holder = fingers.down(center(held))
        awaitPlays("Held pad did not play", 1)
        val edge = edgeBesidePads()
        val palm = fingers.down(edge)
        assertStill("Touching the edge at $edge beside the pads", 1, setOf(held))
        val cell = Cell(4, 4)
        repeat(2) { tap ->
            val finger = fingers.down(center(cell))
            awaitPlays("Pad tap ${tap + 1} did not play while the edge at $edge was held", tap + 2)
            awaitLights("Pad tap ${tap + 1} did not light while the edge was held", setOf(held, cell))
            fingers.up(finger)
            awaitLights("Pad tap ${tap + 1} stayed lit after release", setOf(held))
        }
        fingers.up(palm)
        assertStill("After lifting the edge touch", 3, setOf(held))
        fingers.up(holder)
        awaitLights("Held pad stayed lit after release", emptySet())
        assertStill("After lifting every finger", 3, emptySet())
    }

    /**
     * A point on the left margin between the system back-gesture zone and the leftmost pad or
     * chain button, vertically level with the grid: where a palm rests when holding a tablet.
     */
    private fun edgeBesidePads(): Point = screen.onMain {
        val root = screen.resumed().window.decorView
        val views = screen.views(root)
        val buttons = views.filter { it is PadView || it is ChainView }.map(screen::bounds)
        val pads = views.filterIsInstance<PadView>().map(screen::bounds)
        val grid = boundingBox(buttons)
        val margin = (MARGIN_DP * screen.context.resources.displayMetrics.density).toInt()
        // Insets are relative to the window; pad bounds are on screen coordinates.
        val gestures = checkNotNull(ViewCompat.getRootWindowInsets(root)) { "Play window has no insets yet" }
            .getInsets(WindowInsetsCompat.Type.systemGestures() or WindowInsetsCompat.Type.displayCutout())
        val from = screen.bounds(root).left + gestures.left + margin
        val to = grid.left - margin
        assertTrue("No screen margin beside the pads on this device (from $from to $to)", from < to)
        val point = Point((from + to) / 2, boundingBox(pads).centerY())
        assertTrue("Edge point $point lands on a pad or chain", buttons.none { it.contains(point.x, point.y) })
        point
    }

    private fun boundingBox(rects: List<Rect>) = Rect(rects.first()).apply { rects.forEach { union(it) } }

    private companion object {
        const val GRID = 8
        const val SETTLE_MS = 500L
        const val MARGIN_DP = 16
    }
}
