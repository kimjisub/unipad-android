package com.kimjisub.launchpad

import android.content.pm.ActivityInfo
import androidx.test.core.app.ActivityScenario
import com.kimjisub.launchpad.activity.MidiSelectActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplaySettingsTest {
    @Test fun onlyTheAppliedSizeDensityRotationAndFinishedLayoutCount() {
        val applied = windows()
        assertTrue(DisplaySettings.applied(applied, 1600 to 2560, 533, 1))
        for (stale in listOf(
            applied.replace("cur=2560x1600", "cur=1600x2560"),
            applied.replace("533dpi", "320dpi"),
            applied.replace("ROTATION_90", "ROTATION_270"),
            applied.replace("mLayoutNeeded=false", "mLayoutNeeded=true"),
            applied.replace("deferred=false", "deferred=true"),
            applied.replace("mDisplayId=0", "mDisplayId=1"),
        )) {
            assertFalse(stale, DisplaySettings.applied(stale, 1600 to 2560, 533, 1))
        }
    }

    @Test fun freeRotationStillRequiresDimensionsForTheActualRotation() {
        assertTrue(DisplaySettings.applied(windows(), 1600 to 2560, 533, null))
        assertTrue(DisplaySettings.applied(windows().replace("ROTATION_90", "ROTATION_270"), 1600 to 2560, 533, null))
        assertFalse(DisplaySettings.applied(windows().replace("ROTATION_90", "ROTATION_0"), 1600 to 2560, 533, null))
        assertTrue(DisplaySettings.applied(windows().replace("ROTATION_90", "ROTATION_0"), 2560 to 1600, 533, null))
    }

    @Test fun thisDevicesDisplayRestoresExistingOverridesEvenWhenTheBlockFails() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // A phone's home activity may be portrait-only and ignore the user's rotation lock.
        // Use an unrestricted app window to exercise display rotation in both directions.
        ActivityScenario.launch(MidiSelectActivity::class.java).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            val original = DisplaySettings.read(device)
            val (width, height) = original.size
            val shortest = minOf(width, height)
            DisplaySettings.with(device, shortest to maxOf(width, height), shortest * 160 / 480, 1) {
                val outer = DisplaySettings.read(device)
                val failure = IllegalStateException("exercise display restoration")
                try {
                    DisplaySettings.with(device, density = shortest * 160 / 720, rotation = 3) { throw failure }
                } catch (caught: IllegalStateException) {
                    assertTrue(caught === failure)
                }
                assertEquals(outer, DisplaySettings.read(device))
            }
            assertEquals(original, DisplaySettings.read(device))
        }
    }

    @Test fun restoresPortraitUserLockWhileTheActivityStillRequiresLandscape() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val original = DisplaySettings.read(device)
        val (width, height) = original.size
        val shortest = minOf(width, height)
        // Phone dimensions make activity orientation requests effective on large-screen devices too.
        DisplaySettings.with(device, shortest to maxOf(width, height), shortest * 160 / 480, 0) {
            ActivityScenario.launch(MidiSelectActivity::class.java).use { scenario ->
                val locked = DisplaySettings.read(device)
                assertEquals("lock 0", locked.rotation)
                DisplaySettings.with(device, density = locked.density + 1, rotation = 1) {
                    scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                }
                assertEquals(locked, DisplaySettings.read(device))
                assertTrue(DisplaySettings.applied(
                    device.executeShellCommand("dumpsys window displays"), locked.size, locked.density, null,
                ))
                scenario.onActivity { assertEquals(1, it.display!!.rotation) }
            }
        }
        assertEquals(original, DisplaySettings.read(device))
    }

    // The fields used by the wait, as Android 16's dumpsys window displays prints them.
    private fun windows() = """
        WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)
          Display: mDisplayId=0 (organized)
            init=2560x1600 320dpi cur=2560x1600 app=2560x1600 rng=1600x1600-2560x2560
            deferred=false mLayoutNeeded=false
          overrideConfig={1.0 310mcc260mnc [en_US] ldltr sw480dp w768dp h480dp 533dpi nrml land winConfig={ mBounds=Rect(0, 0 - 2560, 1600) mDisplayRotation=ROTATION_90 mRotation=ROTATION_90}}
    """.trimIndent()
}
