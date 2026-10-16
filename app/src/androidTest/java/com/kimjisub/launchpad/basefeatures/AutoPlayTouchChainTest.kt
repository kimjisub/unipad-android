package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** `touch` is `on` + `off`: on a pad whose sound moves the chain, autoplay goes on in the new chain. */
@RunWith(AndroidJUnit4::class)
class AutoPlayTouchChainTest : PlaybackScreenTest() {
    @Test fun autoplayTouchOnAPadThatMovesTheChainStaysOnTheNewChain() {
        val pack = screen.pack()
        File(pack, "keySound").writeText(File(pack, "keySound").readLines().joinToString("\n") {
            if (it == "1 1 1 silence.wav") "1 1 1 silence.wav 1 2" else it
        } + "\n")
        File(pack, "autoPlay").writeText("t 1 1\nd 500\nt 2 2\nd 500\nt 3 3\nd 500\n")
        screen.openPlay()
        val vm = screen.vm()
        val chains = CopyOnWriteArrayList<Int>()
        val record: (Int, Int) -> Unit = { curr, _ -> chains += curr }
        vm.chain.addObserver(record)
        try {
            screen.options()
            screen.clickText(R.string.autoPlay)
            screen.await("Autoplay did not play its three touches") { audio.plays.size >= 3 }
            SystemClock.sleep(300)
            screen.capture("autoplay-touch-chain")
            assertEquals("Chain after autoplay", 1, screen.onMain { vm.chain.value })
            assertTrue("Autoplay never moved to chain 2: $chains", chains.contains(1))
            assertFalse("Autoplay moved back to chain 1: $chains", chains.dropWhile { it != 1 }.contains(0))
            val chain2Sound = audio.loaded.getValue("silence2.wav")
            assertEquals(listOf(audio.loaded.getValue("silence.wav"), chain2Sound, chain2Sound), audio.plays.take(3))
        } finally {
            vm.chain.removeObserver(record)
        }
    }
}
