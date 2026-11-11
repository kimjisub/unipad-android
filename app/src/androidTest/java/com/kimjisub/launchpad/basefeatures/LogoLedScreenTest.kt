package com.kimjisub.launchpad.basefeatures

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
}
