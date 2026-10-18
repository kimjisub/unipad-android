package com.kimjisub.launchpad.basefeatures

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.TestCaptures
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ZeroDurationLedTest : PlaybackScreenTest() {
    @Test fun maximumRepeatWithoutDelayFinishesAndAnotherPadStillPlays() = checkPack("o 1 1 a 5\nf 1 1\n")

    @Test fun maximumRepeatWithZeroDelayFinishesAndAnotherPadStillPlays() = checkPack("o 1 1 a 5\nd 0\nf 1 1\n")

    private fun checkPack(script: String) {
        File(screen.pack(), "keyLED").deleteRecursively()
        File(screen.pack(), "keyLED").mkdirs()
        File(screen.pack(), "keyLED/1 1 1 2147483647").writeText(script)
        screen.openPlay()
        screen.await("Pad grid did not lay out") { screen.padBounds().let { pads ->
            pads.size == 64 && pads.all { it.width() > 0 && it.height() > 0 }
        } }
        val pads = screen.padBounds()
        val label = InstrumentationRegistry.getArguments().getString("captureLabel") ?: "after"
        val variant = if (script.contains("d 0")) "zero-delay" else "no-delay"
        fun capture(stage: String) {
            val folder = "${TestCaptures.DIRECTORY}/zero-duration"
            screen.device.executeShellCommand("mkdir -p $folder")
            screen.device.executeShellCommand("screencap -p $folder/$label-$variant-$stage.png")
        }
        capture("ready")
        val start = SystemClock.uptimeMillis()
        fun inject(action: Int, pad: Int) {
            val bounds = pads[pad]
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action,
                bounds.centerX().toFloat(), bounds.centerY().toFloat(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(screen.instrumentation.uiAutomation.injectInputEvent(event, false)) }
            finally { event.recycle() }
        }
        inject(MotionEvent.ACTION_DOWN, 0)
        SystemClock.sleep(100)
        inject(MotionEvent.ACTION_UP, 0)
        SystemClock.sleep(100)
        val responsive = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { responsive.countDown() }
        val answered = responsive.await(8, TimeUnit.SECONDS)
        val elapsed = SystemClock.uptimeMillis() - start
        android.util.Log.i("ZeroDurationLed", "$label $variant mainAnswered=$answered elapsedMs=$elapsed")
        capture("pressed")
        assertTrue("Main thread stopped responding after $elapsed ms", answered)
        screen.await("Finite LED animation never finished") { screen.onMain {
            screen.vm().ledRunner?.isEventExist(0, 0) == false
        } }
        screen.await("Last off command did not reach the screen") { screen.onMain {
            screen.vm().channelManager.get(0, 0)?.channel != com.kimjisub.launchpad.manager.ChannelManager.Channel.LED
        } }
        val previous = audio.plays.size
        inject(MotionEvent.ACTION_DOWN, 1)
        inject(MotionEvent.ACTION_UP, 1)
        screen.await("Next pad did not play") { audio.plays.size == previous + 1 }
        capture("responsive")
    }
}
