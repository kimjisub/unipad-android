package com.kimjisub.launchpad.basefeatures

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.manager.LaunchpadColor
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.midi.driver.DriverRef
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Checks actual screen pixels beside unlit pads, using pack commands rather than UI-only colors. */
@RunWith(AndroidJUnit4::class)
class LogoLedOffThemeTest : PlaybackScreenTest() {
    @Test fun offColorFollowsThemeAcrossPhoneAndTablet() {
        val prefs = PreferenceManager(screen.context)
        val originalTheme = prefs.selectedTheme
        val watermark = prefs.watermark
        val size = overrideValue(screen.device.executeShellCommand("wm size"))
        val density = overrideValue(screen.device.executeShellCommand("wm density"))
        val mismatches = mutableListOf<String>()
        val pack = screen.pack()
        File(pack, "keyLED").listFiles().orEmpty().forEach { it.delete() }
        File(pack, "keyLED/1 1 1 1").writeText("o l a 5\nd 60000\n")
        File(pack, "keyLED/1 1 2 1").writeText("o l a 5\nf l\n")
        try {
            prefs.watermark = false
            assertTrue(TestCaptures.save(screen.device, "logo-off-theme/pack.zip") { it.writeBytes(screen.zip()); true })
            for ((layout, dimensions) in listOf("phone" to "720x1280", "tablet" to "1600x2560")) {
                screen.device.executeShellCommand("wm density 320")
                screen.device.executeShellCommand("wm size $dimensions")
                for ((theme, color) in listOf(screen.context.packageName to 0xFF2A3A52.toInt(), "asset://sskin" to 0xFFBEBEBE.toInt())) {
                    prefs.selectedTheme = theme
                    val scenario = screen.openPlay()
                    screen.onMain {
                        screen.resumed().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        screen.vm().scbProLightMode.setChecked(true)
                    }
                    screen.await("Display did not settle") { screen.onMain {
                        screen.vm().uiLoaded && screen.resumed().resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT &&
                            screen.bounds(screen.resumed().window.decorView).width() == dimensions.substringBefore('x').toInt()
                    } }
                    screen.instrumentation.waitForIdleSync()
                    val label = "$layout-${if (theme.startsWith("asset")) "sskin" else "default"}"
                    captureAndCheck("$label-off", color, mismatches)
                    screen.tapPad(0)
                    screen.await("Pack did not light logo") { screen.onMain { screen.vm().channelManager.get(-1, DriverRef.LOGO_FUNCTION_KEY)?.color == LaunchpadColor.ARGB[5].toInt() } }
                    captureAndCheck("$label-on", LaunchpadColor.ARGB[5].toInt(), mismatches)
                    screen.onMain { screen.vm().scbLed.setChecked(false) }
                    captureAndCheck("$label-switch-off", color, mismatches)
                    screen.onMain { screen.vm().scbLed.setChecked(true) }
                    screen.tapPad(1)
                    screen.await("Pack did not turn logo off") { screen.onMain { screen.vm().channelManager.get(-1, DriverRef.LOGO_FUNCTION_KEY) == null } }
                    captureAndCheck("$label-command-off", color, mismatches)
                    println("Off-theme evidence: layout=$layout size=$dimensions density=320 theme=$theme pack=${pack.name}")
                    scenario.close()
                }
            }
            assertTrue("Unlit logo must match the pad: $mismatches", mismatches.isEmpty())
        } finally {
            prefs.selectedTheme = originalTheme
            prefs.watermark = watermark
            screen.device.executeShellCommand("wm size ${size ?: "reset"}")
            screen.device.executeShellCommand("wm density ${density ?: "reset"}")
        }
    }

    private fun captureAndCheck(name: String, expected: Int, mismatches: MutableList<String>) {
        screen.instrumentation.waitForIdleSync()
        // Wait for a rendered frame after the channel/control update.
        screen.device.waitForIdle(500)
        val bounds = screen.node(By.res("play_logo_led")).visibleBounds
        val bitmap = screen.instrumentation.uiAutomation.takeScreenshot()!!
        try {
            var matches = 0
            for (x in bounds.left until bounds.right) for (y in bounds.top until bounds.bottom) {
                if (bitmap.getPixel(x, y) == expected) matches++
            }
            if (matches < 4) mismatches += "$name expected=${expected.toUInt().toString(16)}"
        } finally { bitmap.recycle() }
        screen.capture("logo-off-theme-$name")
    }
    private fun overrideValue(output: String) = Regex("Override (?:size|density): (.+)").find(output)?.groupValues?.get(1)?.trim()
}
