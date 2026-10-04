package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.kimjisub.design.view.SlideTouchOverlayView
import com.kimjisub.launchpad.manager.PreferenceManager
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.design.view.ChainView
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TouchPlaybackTest : PlaybackScreenTest() {
    @Test fun padTouchRequestsSoundFromLoadedPack() {
        screen.openPlay()
        screen.tapPad(0)
        screen.await("Pad input did not request playback from the injected audio engine") { audio.plays.size == 1 }
        assertEquals(audio.loaded.getValue("silence.wav"), audio.plays.single())
        screen.await("keyLED did not reach the screen channel") { screen.onMain {
            screen.vm().channelManager.get(0, 0)?.let { it.channel == Channel.LED && it.code == 5 } == true
        } }
        val color = screen.onMain {
            val pad = screen.views(screen.resumed().window.decorView).filterIsInstance<PadView>().first()
            val led = pad.findViewById<android.view.View>(com.kimjisub.design.R.id.led)
            (led.background as android.graphics.drawable.ColorDrawable).color
        }
        assertEquals(com.kimjisub.launchpad.manager.LaunchpadColor.ARGB[5].toInt(), color)
        screen.capture("pad-keyled")
        screen.await("keyLED did not turn off") { screen.onMain { screen.vm().channelManager.get(0, 0)?.channel != Channel.LED } }
        val chains = screen.onMain {
            screen.views(screen.resumed().window.decorView).filterIsInstance<ChainView>().filter { it.width > 0 }.map(screen::bounds)
        }
        assertTrue("Both chains must be on screen", chains.size >= 2)
        val second = chains[1]
        screen.device.click(second.centerX(), second.centerY())
        screen.await("Chain button did not switch chain") { screen.onMain { screen.vm().chain.value == 1 } }
        screen.tapPad(0)
        assertEquals(audio.loaded.getValue("silence2.wav"), audio.plays.last())
    }

    @Test fun twoSimultaneousFingersPlayBothPadsAndReleaseBoth() {
        screen.openPlay()
        screen.options()
        screen.clickText(R.string.feedbackLight)
        screen.device.pressBack()
        screen.await("Options remained open") { screen.onMain { !screen.vm().isOptionWindowVisible } }
        val pads = screen.padBounds()
        val downAt = SystemClock.uptimeMillis()
        val properties = Array(2) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val coordinates = Array(2) { i -> MotionEvent.PointerCoords().apply {
            x = pads[i + 1].centerX().toFloat(); y = pads[i + 1].centerY().toFloat(); pressure = 1f; size = 1f
        } }
        fun inject(action: Int, count: Int) {
            val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, count, properties, coordinates,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { assertTrue("Touch injection failed", screen.instrumentation.uiAutomation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        inject(MotionEvent.ACTION_DOWN, 1)
        var activePointers = 1
        try {
            inject(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2)
            activePointers = 2
            screen.await("Both fingers did not play") { audio.plays.size == 2 }
            assertEquals(listOf(audio.loaded.getValue("silence.wav"), audio.loaded.getValue("silence.wav")), audio.plays.toList())
            screen.await("Both pressed lights were not shown at once") { screen.onMain {
                (1..2).all { screen.vm().channelManager.get(0, it)?.channel == Channel.PRESSED }
            } }
            screen.capture("two-fingers")
            inject(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2)
            activePointers = 1
            inject(MotionEvent.ACTION_UP, 1)
            activePointers = 0
            screen.await("Released fingers still light pads") { screen.onMain {
                (1..2).all { screen.vm().channelManager.get(0, it)?.channel != Channel.PRESSED }
            } }
        } finally {
            if (activePointers > 0) inject(MotionEvent.ACTION_CANCEL, activePointers)
        }
        cancelledTouchesReleaseOnlyTheirPadsAndCanBePressedAgain()
        wholeTouchStreamCancellationReleasesBothPads()
        slideTouchCancellationStillReleasesBothPads()
    }

    private fun cancelledTouchesReleaseOnlyTheirPadsAndCanBePressedAgain() {
        resetTouchFixture()
        android.util.Log.i("TouchCancellation", "cancelledTouchesReleaseOnlyTheirPadsAndCanBePressedAgain")
        val prefs = PreferenceManager(screen.context)
        val originalSlideMode = prefs.slideMode
        prefs.slideMode = false
        try {
            // Each pad has a distinct sound ID; 0 in keySound means infinite playback.
            val pack = screen.pack()
            File(pack, "sounds/loop.wav").writeBytes(File(pack, "sounds/silence.wav").readBytes())
            File(pack, "keySound").appendText("1 1 2 loop.wav 0\n1 1 3 silence2.wav 0\n")
            // Replace the finite mappings rather than rotate into them on a second press.
            val soundFile = File(pack, "keySound")
            soundFile.writeText(soundFile.readLines().filterNot {
                it == "1 1 2 silence.wav" || it == "1 1 3 silence.wav"
            }.joinToString("\n") + "\n")
            for (column in 2..3) {
                File(pack, "keyLED/1 1 $column 0").writeText("o 1 $column a 5\nd 100\n")
            }
            screen.openPlay()
            screen.options()
            screen.clickText(R.string.feedbackLight)
            screen.device.pressBack()
            screen.await("Options remained open") { screen.onMain { !screen.vm().isOptionWindowVisible } }
            val pads = screen.onMain {
                screen.views(screen.resumed().window.decorView).filterIsInstance<PadView>()
            }
            fun touch(index: Int, action: Int) = dispatch(pads[index], action)
            fun pressed(index: Int) = screen.onMain {
                screen.vm().channelManager.get(0, index)?.channel == Channel.PRESSED
            }
            fun animation(index: Int) = screen.onMain { screen.vm().ledRunner!!.isEventExist(0, index) }
            fun released(index: Int) = screen.onMain {
                val channel = screen.vm().channelManager.get(0, index)?.channel
                channel != Channel.PRESSED && channel != Channel.LED && !screen.vm().ledRunner!!.isEventExist(0, index) && ledIsOff(pads[index])
            }
            fun start(index: Int) {
                touch(index, MotionEvent.ACTION_DOWN)
                screen.await("Pad $index was not pressed with a running LED") { pressed(index) && animation(index) }
                assertEquals(-1, audio.loops.last())
            }
            fun cancel(index: Int, label: String) {
                val voice = audio.plays.size
                val stopCount = audio.stoppedVoices.size
                touch(index, MotionEvent.ACTION_CANCEL)
                android.util.Log.i("TouchCancellation", "$label plays=${audio.plays} stops=${audio.stoppedVoices}")
                try {
                    screen.await("Cancelled pad $index still has a pressed light or running LED") { released(index) }
                    assertEquals(listOf(voice), audio.stoppedVoices.drop(stopCount))
                } finally {
                    captureAfterDraw(label)
                }
            }
            // Single-pad cancellation, then a fresh press through the same real View dispatch path.
            start(1)
            cancel(1, "single-pad-cancel")
            start(1)
            touch(1, MotionEvent.ACTION_UP)
            screen.await("Repressed pad did not release") { released(1) }
            assertEquals(audio.plays.size, audio.stoppedVoices.last())

            // Independent View streams model the per-child cancellation Android delivers.
            start(2)
            val otherVoice = audio.plays.size
            start(1)
            cancel(1, "one-of-two-pads-cancel")
            assertTrue("The other pad must stay held", pressed(2) && animation(2))
            val stopsBeforeRepeat = audio.stoppedVoices.size
            touch(1, MotionEvent.ACTION_CANCEL)
            touch(1, MotionEvent.ACTION_UP)
            assertFalse("Repeated cancel/up stopped the other voice", audio.stoppedVoices.drop(stopsBeforeRepeat).contains(otherVoice))
            assertTrue(pressed(2) && animation(2))
            touch(2, MotionEvent.ACTION_UP)
            screen.await("Other pad did not release") { released(2) }
            assertEquals(otherVoice, audio.stoppedVoices.last())

            // Finite samples follow the existing UP rule: releasing does not stop their voice.
            for (action in listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL)) {
                touch(3, MotionEvent.ACTION_DOWN)
                assertEquals(0, audio.loops.last())
                val count = audio.stoppedVoices.size
                touch(3, action)
                assertEquals("Finite sound was cut off", count, audio.stoppedVoices.size)
                assertFalse(pressed(3))
            }
        } finally { prefs.slideMode = originalSlideMode }
    }

    private fun wholeTouchStreamCancellationReleasesBothPads() {
        resetTouchFixture()
        android.util.Log.i("TouchCancellation", "wholeTouchStreamCancellationReleasesBothPads")
        val prefs = PreferenceManager(screen.context)
        val original = prefs.slideMode
        prefs.slideMode = false
        try {
            val pack = screen.pack()
            File(pack, "keySound").writeText("1 1 2 silence.wav 0\n1 1 3 silence2.wav 0\n")
            for (column in 2..3) File(pack, "keyLED/1 1 $column 0").writeText("o 1 $column a 5\nd 100\n")
            screen.openPlay()
            screen.options()
            screen.clickText(R.string.feedbackLight)
            screen.device.pressBack()
            screen.await("Options remained open") { screen.onMain { !screen.vm().isOptionWindowVisible } }
            val views = screen.onMain { screen.views(screen.resumed().window.decorView).filterIsInstance<PadView>() }
            val pads = screen.padBounds()
            val downAt = SystemClock.uptimeMillis()
            val properties = Array(2) { i -> MotionEvent.PointerProperties().apply { id = i; toolType = MotionEvent.TOOL_TYPE_FINGER } }
            val coords = Array(2) { i -> MotionEvent.PointerCoords().apply {
                x = pads[i + 1].centerX().toFloat(); y = pads[i + 1].centerY().toFloat(); pressure = 1f; size = 1f
            } }
            fun inject(action: Int, count: Int) {
                val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, count, properties, coords,
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                try { assertTrue(screen.instrumentation.uiAutomation.injectInputEvent(event, true)) }
                finally { event.recycle() }
            }
            inject(MotionEvent.ACTION_DOWN, 1)
            var active = true
            try {
                inject(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2)
                screen.await("Two looping pads did not start") { audio.plays.size == 2 && screen.onMain {
                    (1..2).all { screen.vm().channelManager.get(0, it)?.channel == Channel.PRESSED && screen.vm().ledRunner!!.isEventExist(0, it) }
                } }
                assertEquals(listOf(-1, -1), audio.loops.toList())
                screen.capture("whole-cancel-before")
                val stopCount = audio.stoppedVoices.size
                inject(MotionEvent.ACTION_CANCEL, 2)
                active = false
                android.util.Log.i("TouchCancellation", "whole-cancel plays=${audio.plays} stops=${audio.stoppedVoices}")
                try {
                    screen.await("Whole cancellation left lights or LED execution") { screen.onMain {
                        (1..2).all {
                            val channel = screen.vm().channelManager.get(0, it)?.channel
                            channel != Channel.PRESSED && channel != Channel.LED && !screen.vm().ledRunner!!.isEventExist(0, it) && ledIsOff(views[it])
                        }
                    } }
                    assertEquals(setOf(1, 2), audio.stoppedVoices.drop(stopCount).toSet())
                    assertEquals(2, audio.stoppedVoices.size - stopCount)
                } finally {
                    captureAfterDraw("whole-cancel-after")
                }
            } finally { if (active) inject(MotionEvent.ACTION_CANCEL, 2) }
        } finally { prefs.slideMode = original }
    }

    private fun slideTouchCancellationStillReleasesBothPads() {
        resetTouchFixture()
        android.util.Log.i("TouchCancellation", "slideTouchCancellationStillReleasesBothPads")
        val prefs = PreferenceManager(screen.context)
        val original = prefs.slideMode
        prefs.slideMode = true
        try {
            File(screen.pack(), "keySound").writeText("1 1 2 silence.wav 0\n1 1 3 silence2.wav 0\n")
            screen.openPlay()
            val overlay = screen.onMain {
                screen.views(screen.resumed().window.decorView).filterIsInstance<SlideTouchOverlayView>().single()
            }
            val downAt = SystemClock.uptimeMillis()
            val properties = Array(2) { i -> MotionEvent.PointerProperties().apply { id = i } }
            val coordinates = Array(2) { i -> MotionEvent.PointerCoords().apply {
                x = overlay.width * (i + 1.5f) / 8; y = overlay.height / 16f
            } }
            fun touch(action: Int, count: Int) = screen.onMain {
                val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, count, properties, coordinates,
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
                try { assertTrue(overlay.dispatchTouchEvent(event)) } finally { event.recycle() }
            }
            touch(MotionEvent.ACTION_DOWN, 1)
            touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2)
            assertEquals(2, audio.plays.size)
            val count = audio.stoppedVoices.size
            touch(MotionEvent.ACTION_CANCEL, 2)
            assertEquals(setOf(1, 2), audio.stoppedVoices.drop(count).toSet())
            android.util.Log.i("TouchCancellation", "slide-cancel plays=${audio.plays} stops=${audio.stoppedVoices}")
            val afterCancel = audio.stoppedVoices.size
            touch(MotionEvent.ACTION_CANCEL, 2)
            touch(MotionEvent.ACTION_UP, 1)
            assertEquals(afterCancel, audio.stoppedVoices.size)
        } finally { prefs.slideMode = original }
    }

    private fun ledIsOff(pad: PadView): Boolean {
        val led = pad.findViewById<View>(com.kimjisub.design.R.id.led)
        return (led.background as? android.graphics.drawable.ColorDrawable)?.color == 0
    }

    private fun captureAfterDraw(name: String) {
        val drawn = CountDownLatch(1)
        screen.onMain {
            val decor = screen.resumed().window.decorView
            decor.postOnAnimation { decor.postOnAnimation { drawn.countDown() } }
        }
        assertTrue("The screen did not draw its updated lights", drawn.await(10, TimeUnit.SECONDS))
        screen.capture(name)
    }

    private fun resetTouchFixture() {
        screen.close()
        screen.install()
        audio.plays.clear()
        audio.loops.clear()
        audio.stoppedVoices.clear()
    }

    /** Deliver to the actual pad child: PadView forwards its listener to touchSpace. */
    private fun dispatch(pad: PadView, action: Int) = screen.onMain {
        val target = pad.findViewById<View>(com.kimjisub.design.R.id.touchSpace)
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, action, target.width / 2f, target.height / 2f, 0)
        try { target.dispatchTouchEvent(event) } finally { event.recycle() }
    }

}
