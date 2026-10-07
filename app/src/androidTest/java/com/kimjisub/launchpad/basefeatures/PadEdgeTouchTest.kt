package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.design.view.PadView
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Every pad answers to a finger anywhere on it: its centre, a few pixels inside each edge and each
 * corner. Each pad has its own sound file, so a press caught by a neighbour shows up as that
 * neighbour's sound. Stretched pads (`squareButton=false`) used to be sized from the play area
 * before its padding was taken off, so the grid was taller and wider than the space touches are
 * divided over and the lower part of each pad played the pad below.
 */
@RunWith(AndroidJUnit4::class)
class PadEdgeTouchTest : PlaybackScreenTest() {
    private var fingers: Fingers? = null

    @After fun liftFingers() {
        fingers?.cancelRemaining()
    }

    /** The shape of the one stretched pack in the local collection: a 4 x 3 drum pack. */
    @Test fun stretched4x3PadsPlayTheirOwnSoundFromEveryEdge() = checkEveryEdge(rows = 4, columns = 3, square = false)

    @Test fun stretched8x8PadsPlayTheirOwnSoundFromEveryEdge() = checkEveryEdge(rows = 8, columns = 8, square = false)

    /** Most packs: square pads keep the layout they had. */
    @Test fun square8x8PadsPlayTheirOwnSoundFromEveryEdge() = checkEveryEdge(rows = 8, columns = 8, square = true)

    private fun checkEveryEdge(rows: Int, columns: Int, square: Boolean) {
        writePack(rows, columns, square)
        screen.openPlay()
        val shape = "${rows}x$columns ${if (square) "square" else "stretched"}"
        screen.capture("pad-edges-$rows-$columns-${if (square) "square" else "stretched"}")
        val (pads, grid) = screen.onMain {
            val views = screen.views(screen.resumed().window.decorView).filterIsInstance<PadView>()
            views.map(screen::bounds) to screen.bounds(views.first().parent.parent as View)
        }
        assertTrue("$shape: expected ${rows * columns} pads, found ${pads.size}", pads.size == rows * columns)

        val touch = Fingers(screen).also { fingers = it }
        val wrong = mutableListOf<String>()
        for (x in 0 until rows) for (y in 0 until columns) {
            val expected = audio.loaded.getValue(soundName(x, y))
            for ((where, at) in pressPoints(pads[x * columns + y])) {
                val before = audio.plays.size
                touch.up(touch.down(at))
                val played = awaitPlay(before)
                if (played != expected) wrong += "pad ($x, $y) $where at $at played ${played?.let(::nameOf) ?: "nothing"}"
            }
        }
        val outside = pads.filterNot(grid::contains)
        assertTrue(
            "$shape: ${wrong.size} presses missed their pad:\n${wrong.joinToString("\n")}\n" +
                "pads reaching outside the grid $grid that touches are divided over: $outside",
            wrong.isEmpty() && outside.isEmpty(),
        )
    }

    /** The sound requested after the first [before] requests, or null when the press played nothing. */
    private fun awaitPlay(before: Int): Int? {
        val deadline = SystemClock.elapsedRealtime() + PLAY_TIMEOUT_MS
        while (audio.plays.size <= before && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(5)
        return audio.plays.getOrNull(before)
    }

    /** The centre and, [INSET] pixels inside the pad, the middle of each edge and each corner. */
    private fun pressPoints(pad: Rect): List<Pair<String, Point>> {
        val left = pad.left + INSET
        val right = pad.right - 1 - INSET
        val top = pad.top + INSET
        val bottom = pad.bottom - 1 - INSET
        val cx = pad.centerX()
        val cy = pad.centerY()
        return listOf(
            "centre" to Point(cx, cy),
            "top" to Point(cx, top), "bottom" to Point(cx, bottom),
            "left" to Point(left, cy), "right" to Point(right, cy),
            "top-left" to Point(left, top), "top-right" to Point(right, top),
            "bottom-left" to Point(left, bottom), "bottom-right" to Point(right, bottom),
        )
    }

    /** Rewrites the installed pack as one chain of [rows] x [columns] pads, each with its own silent sound. */
    private fun writePack(rows: Int, columns: Int, square: Boolean) {
        val root = screen.pack()
        val silence = File(root, "sounds/silence.wav").readBytes()
        File(root, "sounds").deleteRecursively()
        File(root, "keyLED").deleteRecursively()
        File(root, "autoPlay").delete()
        File(root, "sounds").mkdirs()
        val keySound = StringBuilder()
        for (x in 0 until rows) for (y in 0 until columns) {
            File(root, "sounds/${soundName(x, y)}").writeBytes(silence)
            keySound.append("1 ${x + 1} ${y + 1} ${soundName(x, y)}\n")
        }
        File(root, "keySound").writeText(keySound.toString())
        File(root, "info").writeText(
            "title=${FeatureScreen.TITLE}\nproducerName=UniPad test\nbuttonX=$rows\nbuttonY=$columns\nchain=1\nsquareButton=$square\n"
        )
    }

    private fun soundName(x: Int, y: Int) = "p_${x}_$y.wav"

    private fun nameOf(soundId: Int) = audio.loaded.entries.firstOrNull { it.value == soundId }?.key ?: "sound $soundId"

    private companion object {
        const val INSET = 4
        const val PLAY_TIMEOUT_MS = 2000L
    }
}
