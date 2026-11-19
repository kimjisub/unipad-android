package com.kimjisub.launchpad.basefeatures

import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.manager.LaunchpadColor
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RotationPlaybackTest : PlaybackScreenTest() {
    @Test fun shortWindowFocusLossKeepsHeldInputAndLights() {
        preparePack()
        screen.openPlay()
        val activity = screen.resumed() as PlayActivity
        val vm = screen.vm()
        screen.onMain {
            vm.scbProLightMode.setChecked(true)
            vm.controllerPadTouch(0, 0, true)
        }
        screen.await("Held sound did not start") { audio.ringingLoops().isNotEmpty() }
        assertLights()
        val silences = audio.silences.get()
        screen.capture("rotation-short-focus-before")
        screen.onMain { activity.onWindowFocusChanged(false) }
        SystemClock.sleep(100)
        screen.onMain { activity.onWindowFocusChanged(true) }
        SystemClock.sleep(600)
        screen.capture("rotation-short-focus-after")
        assertEquals("A short focus change silenced held input", silences, audio.silences.get())
        assertEquals(1, audio.ringingLoops().size)
        assertLights()
        screen.onMain { vm.controllerPadTouch(0, 0, false) }
        screen.await("Releasing the held pad did not stop its sound") { audio.ringingLoops().isEmpty() }
    }

    @Test fun animatedRotationAndResizeKeepManualPlayback() = rotationAndResize(autoplay = false)
    @Test fun animatedRotationAndResizeKeepAutoplay() = rotationAndResize(autoplay = true)

    private fun preparePack() {
        File(screen.pack(), "keySound").writeText("1 1 1 silence.wav 0\n")
        File(screen.pack(), "keyLED/1 1 1 1").writeText("o 1 1 a 5\no l a 5\nd 60000\n")
        File(screen.pack(), "autoPlay").writeText("on 1 1\ndelay 60000\n")
    }

    private fun rotationAndResize(autoplay: Boolean) = withWindowAnimations {
        preparePack()
        val originalSize = screen.device.executeShellCommand("wm size").let {
            Regex("Override size: (.+)").find(it)?.groupValues?.get(1)?.trim() ?: "reset"
        }
        val prefs = PreferenceManager(screen.context)
        val originalTheme = prefs.selectedTheme
        prefs.selectedTheme = screen.context.packageName
        try {
            screen.openPlay()
            val activity = screen.resumed() as PlayActivity
            val vm = screen.vm()
            screen.onMain { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
            awaitRotation(1)
            screen.onMain {
                vm.scbProLightMode.setChecked(true)
                if (autoplay) vm.switchPlayMode(PlayMode.AutoPlay) else vm.controllerPadTouch(0, 0, true)
            }
            screen.await("Held sound did not start") { audio.ringingLoops().size == 1 }
            assertLights()
            assertRenderedLogo()
            val silences = audio.silences.get()
            val kind = if (autoplay) "autoplay" else "manual"
            screen.capture("rotation-$kind-before")
            for ((size, orientation, rotation) in listOf(
                Triple("720x840", ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE, 3),
                Triple("720x1280", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, 1),
            )) {
                screen.device.executeShellCommand("wm size $size")
                screen.onMain { activity.requestedOrientation = orientation }
                awaitRotation(rotation)
                screen.capture("rotation-$kind-$rotation-after-resize")
                assertSame(activity, screen.resumed())
                assertSame(vm, screen.vm())
                assertEquals("Rotation silenced $kind", silences, audio.silences.get())
                assertEquals("Rotation released the held sound", 1, audio.ringingLoops().size)
                assertEquals(autoplay, screen.onMain { vm.isAutoPlayPlaying })
                assertLights()
                assertRenderedLogo()
            }
            if (autoplay) {
                assertTrue(screen.device.openNotification())
                screen.await("Notification shade must interrupt autoplay") { screen.onMain { !vm.isAutoPlayPlaying } }
                screen.await("Notification shade must release held sound") { audio.ringingLoops().isEmpty() }
                screen.onMain {
                    assertNull(vm.channelManager.get(0, 0))
                    assertNull(vm.channelManager.get(-1, DriverRef.LOGO_FUNCTION_KEY))
                }
                screen.device.pressBack()
                screen.await("Play window did not return") { screen.ready() }
                screen.onMain { assertFalse("Returning focus must not resume autoplay", vm.isAutoPlayPlaying) }
                screen.capture("rotation-autoplay-shade-stopped")
            } else {
                screen.onMain { vm.controllerPadTouch(0, 0, false) }
                screen.await("Releasing the pad did not stop the sound") { audio.ringingLoops().isEmpty() }
            }
        } finally {
            screen.close()
            screen.device.unfreezeRotation()
            screen.device.executeShellCommand("wm size $originalSize")
            prefs.selectedTheme = originalTheme
        }
    }

    private fun awaitRotation(rotation: Int) {
        // Large screens can ignore activity orientation requests. Turn the device itself.
        when (rotation) {
            1 -> screen.device.setOrientationLeft()
            3 -> screen.device.setOrientationRight()
            else -> error("Unsupported rotation $rotation")
        }
        screen.await("Animated rotation did not settle at $rotation") {
            screen.device.displayRotation == rotation && screen.onMain {
                screen.resumed().window.decorView.hasWindowFocus() && screen.vm().uiLoaded
            }
        }
        // Observe beyond the focus grace period, including a delayed callback that was not cancelled.
        SystemClock.sleep(600)
        screen.freshControls()
    }

    private fun assertLights() = screen.await("Rotation cleared pad or logo animation") {
        screen.onMain {
            val vm = screen.vm()
            val red = LaunchpadColor.ARGB[5].toInt()
            vm.channelManager.get(0, 0)?.color == red &&
                vm.channelManager.get(-1, DriverRef.LOGO_FUNCTION_KEY)?.color == red
        }
    }

    private fun assertRenderedLogo() = screen.await("Rendered logo lost its red light") {
        if (!screen.has(By.res("play_logo_led"))) return@await false
        val bounds = screen.node(By.res("play_logo_led")).visibleBounds
        val shot = screen.instrumentation.uiAutomation.takeScreenshot() ?: return@await false
        try {
            var redPixels = 0
            for (x in bounds.left until bounds.right) for (y in bounds.top until bounds.bottom) {
                if (shot.getPixel(x, y) == LaunchpadColor.ARGB[5].toInt()) redPixels++
            }
            redPixels >= 4
        } finally { shot.recycle() }
    }
}
