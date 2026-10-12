package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
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
        withTouchFixture(slide = false, finitePad = true) { pads ->
            fun pressed(index: Int) = screen.onMain {
                screen.vm().channelManager.get(0, index)?.channel == Channel.PRESSED &&
                    screen.vm().ledRunner!!.isEventExist(0, index)
            }
            fun start(gesture: CancelledGesture, index: Int): Int {
                val id = gesture.down(index)
                screen.await("Pad $index was not pressed with a running LED") { pressed(index) }
                assertEquals(-1, audio.loops.last())
                return id
            }
            val gesture = CancelledGesture()
            try {
                start(gesture, 1)
                val voice = audio.plays.size
                val before = audio.stoppedVoices.size
                gesture.cancel()
                try {
                    screen.await("Cancelled pad 1 still has a pressed light or running LED") { released(pads, 1) }
                    assertEquals(listOf(voice), audio.stoppedVoices.drop(before))
                } finally { captureAfterDraw("single-pad-cancel") }
                val after = audio.stoppedVoices.size
                gesture.repeatCancelAndUp()
                assertEquals("Repeated cancel/up stopped another voice", after, audio.stoppedVoices.size)
                val repressed = start(gesture, 1)
                gesture.up(repressed)
                screen.await("Repressed pad did not release") { released(pads, 1) }
                assertEquals(audio.plays.size, audio.stoppedVoices.last())

                val other = start(gesture, 2)
                val otherVoice = audio.plays.size
                val cancelled = start(gesture, 1)
                val cancelledVoice = audio.plays.size
                val stops = audio.stoppedVoices.size
                // Android 13+ reports an individually cancelled finger as POINTER_UP with FLAG_CANCELED.
                gesture.up(cancelled, cancelled = true)
                screen.await("Individually cancelled pad did not release") { released(pads, 1) }
                assertEquals(listOf(cancelledVoice), audio.stoppedVoices.drop(stops))
                assertTrue("The other pad must stay held", pressed(2))
                captureAfterDraw("one-of-two-pads-cancel")
                val again = start(gesture, 1)
                gesture.up(again)
                assertFalse("Repress/release stopped the other voice", audio.stoppedVoices.drop(stops).contains(otherVoice))
                assertTrue("The other pad must stay held", pressed(2))
                gesture.up(other)
                screen.await("Other pad did not release") { released(pads, 2) }
                assertEquals(otherVoice, audio.stoppedVoices.last())

                // Finite samples follow the existing UP rule: releasing does not stop their voice.
                for (cancel in listOf(false, true)) {
                    val id = gesture.down(3)
                    screen.await("Finite sample did not play") { audio.loops.lastOrNull() == 0 }
                    val count = audio.stoppedVoices.size
                    if (cancel) gesture.cancel() else gesture.up(id)
                    screen.await("Finite sample pad stayed pressed") { released(pads, 3) }
                    assertEquals("Finite sound was cut off", count, audio.stoppedVoices.size)
                }
                android.util.Log.i("TouchCancellation", "per-pad plays=${audio.plays} stops=${audio.stoppedVoices}")
            } finally { gesture.cancel() }
        }
    }

    private fun wholeTouchStreamCancellationReleasesBothPads() = checkWholeCancellation(slide = false)

    private fun slideTouchCancellationStillReleasesBothPads() = checkWholeCancellation(slide = true)

    private fun checkWholeCancellation(slide: Boolean) {
        resetTouchFixture()
        android.util.Log.i("TouchCancellation", "wholeTouchStreamCancellation slide=$slide")
        withTouchFixture(slide) { pads ->
            val gesture = CancelledGesture()
            try {
                gesture.down(1)
                gesture.down(2)
                screen.await("Two looping pads did not start") { audio.plays.size == 2 && screen.onMain {
                    (1..2).all { screen.vm().channelManager.get(0, it)?.channel == Channel.PRESSED &&
                        screen.vm().ledRunner!!.isEventExist(0, it) }
                } }
                assertEquals(listOf(-1, -1), audio.loops.toList())
                screen.capture(if (slide) "slide-cancel-before" else "whole-cancel-before")
                val stopCount = audio.stoppedVoices.size
                gesture.cancel()
                try {
                    screen.await("Whole cancellation left lights or LED execution") { (1..2).all { released(pads, it) } }
                    assertEquals(setOf(1, 2), audio.stoppedVoices.drop(stopCount).toSet())
                    assertEquals(2, audio.stoppedVoices.size - stopCount)
                } finally { captureAfterDraw(if (slide) "slide-cancel-after" else "whole-cancel-after") }
                val after = audio.stoppedVoices.size
                gesture.repeatCancelAndUp()
                assertEquals("Repeated cancel/up requested another stop", after, audio.stoppedVoices.size)
                val id = gesture.down(1)
                screen.await("Repress after cancellation did not play") { audio.plays.size == 3 }
                gesture.up(id)
                screen.await("Repress after cancellation did not release") { released(pads, 1) }
                android.util.Log.i("TouchCancellation", "whole-cancel slide=$slide plays=${audio.plays} stops=${audio.stoppedVoices}")
            } finally { gesture.cancel() }
        }
    }

    private fun withTouchFixture(slide: Boolean, finitePad: Boolean = false, check: (List<PadView>) -> Unit) {
        val prefs = PreferenceManager(screen.context)
        val original = prefs.slideMode
        prefs.slideMode = slide
        try {
            val pack = screen.pack()
            File(pack, "keySound").writeText("1 1 2 silence.wav 0\n1 1 3 silence2.wav 0\n" +
                if (finitePad) "1 1 4 silence.wav\n" else "")
            for (column in 2..3) File(pack, "keyLED/1 1 $column 0").writeText("o 1 $column a 5\nd 100\n")
            screen.openPlay()
            screen.options()
            screen.clickText(R.string.feedbackLight)
            screen.device.pressBack()
            screen.await("Options remained open") { screen.onMain { !screen.vm().isOptionWindowVisible } }
            check(screen.onMain { screen.views(screen.resumed().window.decorView).filterIsInstance<PadView>() })
        } finally { prefs.slideMode = original }
    }

    private fun released(pads: List<PadView>, index: Int) = screen.onMain {
        val channel = screen.vm().channelManager.get(0, index)?.channel
        channel != Channel.PRESSED && channel != Channel.LED &&
            !screen.vm().ledRunner!!.isEventExist(0, index) && ledIsOff(pads[index])
    }

    /** Sends cancellation through the real window, including a cancelled individual pointer. */
    private inner class CancelledGesture {
        private val held = mutableListOf<Pair<Int, Int>>() // pointer id to pad index
        private var cancelled = emptyList<Pair<Int, Int>>()
        private var downAt = 0L

        fun down(index: Int): Int {
            val id = generateSequence(0) { it + 1 }.first { id -> held.none { it.first == id } }
            if (held.isEmpty()) downAt = SystemClock.uptimeMillis()
            held += id to index
            inject(if (held.size == 1) MotionEvent.ACTION_DOWN else
                MotionEvent.ACTION_POINTER_DOWN or (held.lastIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), held)
            return id
        }

        fun up(id: Int, cancelled: Boolean = false) {
            val index = held.indexOfFirst { it.first == id }
            check(index >= 0)
            inject(if (held.size == 1) MotionEvent.ACTION_UP else
                MotionEvent.ACTION_POINTER_UP or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), held,
                if (cancelled) MotionEvent.FLAG_CANCELED else 0)
            held.removeAt(index)
        }

        fun cancel() {
            if (held.isEmpty()) return
            cancelled = held.toList()
            inject(MotionEvent.ACTION_CANCEL, held)
            held.clear()
        }

        fun repeatCancelAndUp() {
            check(held.isEmpty() && cancelled.isNotEmpty())
            inject(MotionEvent.ACTION_CANCEL, cancelled)
            inject(MotionEvent.ACTION_UP, cancelled.take(1))
        }

        private fun inject(action: Int, pointers: List<Pair<Int, Int>>, flags: Int = 0) {
            val pads = screen.padBounds()
            val properties = pointers.map { (id, _) -> MotionEvent.PointerProperties().apply {
                this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER
            } }.toTypedArray()
            val coordinates = pointers.map { (_, pad) -> MotionEvent.PointerCoords().apply {
                x = pads[pad].centerX().toFloat(); y = pads[pad].centerY().toFloat(); pressure = 1f; size = 1f
            } }.toTypedArray()
            val event = MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, pointers.size,
                properties, coordinates, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, flags)
            try { assertTrue("Touch injection failed", screen.instrumentation.uiAutomation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
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

}
