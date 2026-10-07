package com.kimjisub.launchpad.basefeatures

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import java.io.File

/**
 * Pads held through more than a plain lift: the screen changing size, or a second kind of
 * pointer. Every first-chain pad plays its own endless loop, so each sound request names the pad
 * that made it, and a pad left playing shows as a loop nothing stopped. The events the window
 * received are kept in [received] and quoted when a check fails.
 */
abstract class HeldPadTest : MultiTouchPlaybackTest(slideMode = false) {
    protected lateinit var received: ReceivedInput

    @Before fun loopEveryFirstChainPad() {
        val pack = screen.pack()
        val silence = File(pack, "sounds/silence.wav").readBytes()
        val keySound = File(pack, "keySound")
        keySound.writeText(keySound.readLines().filter(String::isNotBlank).joinToString("\n") { line ->
            val (chain, x, y) = line.split(" ")
            if (chain != "1") return@joinToString line
            File(pack, "sounds/${loopFile(x, y)}").writeBytes(silence)
            "1 $x $y ${loopFile(x, y)} 0"
        } + "\n")
    }

    @After fun detachReceived() {
        if (this::received.isInitialized) received.detach()
    }

    protected fun startHeld(slide: Boolean = false) {
        start(slide)
        received = ReceivedInput(screen)
    }

    /** Waits until exactly [cells] are lit by a press and each loops once. */
    protected fun awaitHeld(message: String, cells: Set<Cell>) {
        var lit = emptySet<Cell>()
        var looping = emptyList<Cell>()
        screen.await({ "$message (expected lit and looping: $cells, lit: $lit, looping: $looping)\n$received" }) {
            lit = litCells()
            looping = loopingCells()
            lit == cells && looping.size == cells.size && looping.toSet() == cells
        }
    }

    /** After input settles: exactly [played] were requested, in order, and only [held] are lit and looping. */
    protected fun assertHeldStill(message: String, played: List<Cell>, held: Set<Cell>) {
        assertStill("$message\n$received", played.size, held)
        assertEquals("$message: pads whose sound was requested\n$received", played, audio.plays.map(::cellOf))
        assertEquals("$message: pads still looping\n$received", held, loopingCells().toSet())
    }

    protected fun loopingCells(): List<Cell> = audio.ringingLoops().map { checkNotNull(cellOf(it)) { "Sound $it loops but is no pad's loop" } }

    private fun cellOf(soundId: Int): Cell? {
        val file = audio.loaded.entries.firstOrNull { it.value == soundId }?.key ?: return null
        val (x, y) = LOOP_FILE.matchEntire(file)?.destructured ?: return null
        return Cell(x.toInt() - 1, y.toInt() - 1)
    }

    private companion object {
        val LOOP_FILE = Regex("loop-(\\d+)-(\\d+)\\.wav")

        fun loopFile(x: String, y: String) = "loop-$x-$y.wav"
    }
}
