package com.kimjisub.launchpad.basefeatures

import android.content.pm.ActivityInfo
import android.graphics.Rect
import com.kimjisub.launchpad.R
import java.io.File
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real play screen, with the same pack and controls used for before/after captures. */
@RunWith(AndroidJUnit4::class)
class LogoLedScreenTest : PlaybackScreenTest() {
    @Test
    fun logoIsVisibleOnlyInProModeAndStaysWithHiddenControls() {
        screen.openPlay()
        screen.capture("logo-normal")
        assertFalse(screen.has(By.res("play_logo_led")))
        screen.onMain { screen.vm().scbProLightMode.setChecked(true) }
        screen.await("Pro mode was not applied") { screen.onMain { screen.vm().scbProLightMode.checked } }
        val logo = screen.node(By.res("play_logo_led"))
        screen.instrumentation.waitForIdleSync()
        screen.capture("logo-pro-off")
        assertFalse("Logo must not be an input button", logo.isClickable)
        assertTrue("Logo must have visible bounds", !logo.visibleBounds.isEmpty)
        screen.onMain { screen.vm().scbHideUI.setChecked(true) }
        screen.await("Hide UI did not hide the menu") { !screen.has(By.desc(screen.text(com.kimjisub.launchpad.R.string.menu))) }
        assertTrue("Hide UI must retain the logo LED", screen.has(By.res("play_logo_led")))
        screen.onMain {
            screen.vm().scbHideUI.setChecked(false)
            screen.vm().scbProLightMode.setChecked(false)
        }
        screen.await("Normal mode kept the logo LED") { !screen.has(By.res("play_logo_led")) }
    }
    @Test
    fun smallPackLogoKeepsClearOfMenuWithAndWithoutAutoplay() {
        val originalSize = Regex("Override size: (.+)").find(screen.device.executeShellCommand("wm size"))?.groupValues?.get(1)?.trim()
        val originalDensity = Regex("Override density: (.+)").find(screen.device.executeShellCommand("wm density"))?.groupValues?.get(1)?.trim()
        try {
            screen.device.executeShellCommand("wm size 720x1280")
            screen.device.executeShellCommand("wm density 320")
            val pack = screen.pack()
            val info = File(pack, "info")
            info.writeText(info.readText().replace("buttonX=8", "buttonX=1").replace("buttonY=8", "buttonY=5"))
            File(pack, "keySound").writeText("1 1 1 silence.wav\n")
            File(pack, "keyLED/1 1 1 1").writeText("o l a 5\nd 60000\n")
            File(pack, "autoPlay").writeText("o 1 1\nd 60000\nf 1 1\n")
            val overlaps = mutableListOf<String>()
            for (autoplay in listOf(true, false)) {
                if (!autoplay) assertTrue(File(pack, "autoPlay").delete())
                val scenario = screen.openPlay()
                screen.onMain {
                    screen.resumed().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    screen.vm().scbProLightMode.setChecked(true)
                    screen.vm().autoPlayControlVisible = autoplay
                }
                screen.await("Portrait display did not settle") {
                    screen.onMain { screen.resumed().resources.configuration.screenWidthDp <= 360 &&
                        screen.resumed().resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
                }
                screen.tapPad(0)
                screen.instrumentation.waitForIdleSync()
                val logo = screen.node(By.res("play_logo_led")).visibleBounds
                val menu = screen.node(By.desc(screen.text(R.string.menu))).visibleBounds
                assertTrue("Transport visibility did not match fixture", screen.has(By.desc(screen.text(R.string.cd_autoplay_next))) == autoplay)
                if (autoplay) {
                    for (control in listOf(R.string.cd_autoplay_prev, R.string.cd_autoplay_play, R.string.cd_autoplay_next)) {
                        val bounds = screen.node(By.desc(screen.text(control))).visibleBounds
                        if (Rect.intersects(logo, bounds)) overlaps += "logo=$logo covers transport=$bounds"
                    }
                }
                screen.capture("small-pack-logo-autoplay-$autoplay")
                if (Rect.intersects(logo, menu)) overlaps += "autoplay=$autoplay, logo=$logo, menu=$menu"
                screen.clickDescription(R.string.menu)
                screen.await("Menu must remain usable") { screen.onMain { screen.vm().isOptionWindowVisible } }
                scenario.close()
            }
            assertTrue("Logo covers menu: $overlaps", overlaps.isEmpty())
        } finally {
            screen.device.executeShellCommand("wm size ${originalSize ?: "reset"}")
            screen.device.executeShellCommand("wm density ${originalDensity ?: "reset"}")
        }
    }

}
