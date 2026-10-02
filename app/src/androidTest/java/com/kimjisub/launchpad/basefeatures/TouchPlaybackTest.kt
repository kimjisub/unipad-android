package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
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
    }
}
