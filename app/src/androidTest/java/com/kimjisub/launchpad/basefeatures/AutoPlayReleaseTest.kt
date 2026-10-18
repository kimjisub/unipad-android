package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real play controls and parsed multi-mapping, with decoded sound requests recorded instead of a speaker. */
@RunWith(AndroidJUnit4::class)
class AutoPlayReleaseTest : PlaybackScreenTest() {
    @Test fun pausingAutoplayPreservesTheSamePadHeldByAFinger() {
        val pack = screen.pack()
        File(pack, "keySound").writeText("1 1 1 silence.wav 0\n")
        File(pack, "autoPlay").writeText("o 1 1\nd 60000\nf 1 1\n")
        screen.openPlay()
        screen.options()
        screen.clickText(R.string.autoPlay)
        screen.await("Autoplay did not hold an infinite loop") { audio.ringingLoops().size == 1 }
        val bounds = screen.padBounds().first()
        val fingers = Fingers(screen)
        try {
            val id = fingers.down(Point(bounds.centerX(), bounds.centerY()))
            screen.await("Finger did not play the same pad") { audio.plays.size == 2 }
            val pause = screen.node(By.desc(screen.text(R.string.cd_autoplay_pause))).visibleBounds
            val buttonFinger = fingers.down(Point(pause.centerX(), pause.centerY()))
            fingers.up(buttonFinger)
            screen.instrumentation.waitForIdleSync()
            assertEquals("The finger still owns this pad", 1, audio.ringingLoops().size)
            fingers.up(id)
            screen.await("Lifting the last input did not stop the loop") { audio.ringingLoops().isEmpty() }
            screen.capture("autoplay-paused-finger-released")
        } finally {
            fingers.cancelRemaining()
        }
    }

    @Test fun disablingAutoplayReleasesItsLoopAfterTheMappingHasAdvanced() {
        val pack = screen.pack()
        File(pack, "keySound").writeText("1 1 1 silence.wav 0\n1 1 1 silence2.wav 1\n")
        File(pack, "autoPlay").writeText("o 1 1\nd 60000\nf 1 1\n")
        screen.openPlay()
        screen.options()
        screen.clickText(R.string.autoPlay)
        screen.await("Autoplay did not hold an infinite loop") { audio.ringingLoops().size == 1 }
        screen.capture("autoplay-loop-held")
        screen.options()
        screen.clickText(R.string.autoPlay)
        screen.instrumentation.waitForIdleSync()
        screen.capture("autoplay-loop-disabled")
        println("AUTOPLAY_RELEASE plays=${audio.plays.size} ringingLoops=${audio.ringingLoops().size}")
        assertEquals("Disabling autoplay must release the sound it started, not the next mapping", emptyList<Int>(), audio.ringingLoops())
    }
}
