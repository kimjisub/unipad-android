package com.kimjisub.launchpad

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import com.kimjisub.launchpad.activity.MidiSelectActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplaySettingsTest {
    @Test fun onlyTheAppliedSizeDensityAndFinishedLayoutCount() {
        val applied = windows()
        assertTrue(DisplaySettings.applied(applied, 1600 to 2560, 533, "lock 1", "lock 1"))
        for (stale in listOf(
            applied.replace("cur=2560x1600", "cur=1600x2560"),
            applied.replace("533dpi", "320dpi"),
            applied.replace("ROTATION_90", "ROTATION_0"),
            applied.replace("mLayoutNeeded=false", "mLayoutNeeded=true"),
            applied.replace("deferred=false", "deferred=true"),
            applied.replace("mDisplayId=0", "mDisplayId=1"),
        )) {
            assertFalse(stale, DisplaySettings.applied(stale, 1600 to 2560, 533, "lock 1", "lock 1"))
        }
    }

    @Test fun freeRotationStillRequiresDimensionsForTheActualRotation() {
        assertTrue(DisplaySettings.applied(windows(), 1600 to 2560, 533, "free", "free"))
        assertTrue(DisplaySettings.applied(windows().replace("ROTATION_90", "ROTATION_270"), 1600 to 2560, 533, "free", "free"))
        assertFalse(DisplaySettings.applied(windows().replace("ROTATION_90", "ROTATION_0"), 1600 to 2560, 533, "free", "free"))
        assertTrue(DisplaySettings.applied(windows().replace("ROTATION_90", "ROTATION_0"), 2560 to 1600, 533, "free", "free"))
    }

    @Test fun userRotationMustSettleEvenWhenTheActivityIgnoresIt() {
        // The landscape window is settled, but the asynchronously written preference is still old.
        assertFalse(DisplaySettings.applied(windows(), 1600 to 2560, 533, "lock 0", "lock 1"))
        assertFalse(DisplaySettings.applied(windows(), 1600 to 2560, 533, "lock 1", "lock 0"))
        assertFalse(DisplaySettings.applied(windows(), 1600 to 2560, 533, "lock 1", "free"))
        // A landscape activity may keep rotation 1 while the user preference is portrait or free.
        assertTrue(DisplaySettings.applied(windows(), 1600 to 2560, 533, "lock 0", "lock 0"))
        assertTrue(DisplaySettings.applied(windows(), 1600 to 2560, 533, "free", "free"))
    }

    @Test fun android12PrintsTheDisplayConfigurationBeforeItsDisplayLine() {
        val applied = android12Windows()
        assertTrue(DisplaySettings.applied(applied, 720 to 1440, 320, "lock 1", "lock 1"))
        for (stale in listOf(
            applied.replace("cur=720x1440", "cur=1080x2400"),
            applied.replace("h625dp 320dpi", "h625dp 420dpi"),
            applied.replace("mRotation=ROTATION_0", "mRotation=ROTATION_90"),
            applied.replace("mLayoutNeeded=false", "mLayoutNeeded=true"),
            applied.replace("mDisplayId=0", "mDisplayId=1"),
        )) {
            assertFalse(stale, DisplaySettings.applied(stale, 720 to 1440, 320, "lock 1", "lock 1"))
        }
        assertTrue(DisplaySettings.applied(applied.replace("mRotation=ROTATION_0", "mRotation=ROTATION_90"), 1440 to 720, 320, "free", "free"))
    }

    @Test fun android12UsesOnlyTheConfigurationPrintedForDisplayZero() {
        val otherDisplayFirst = """
            WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)
              overrideConfig={1.0 ?mcc?mnc [en_US] ldltr sw360dp w360dp h640dp 320dpi nrml port winConfig={ mBounds=Rect(0, 0 - 720, 1280) mRotation=ROTATION_0}}
              Display: mDisplayId=2 rootTasks=1
                init=720x1280 320dpi cur=720x1280 app=720x1280 rng=720x720-1280x1280
                deferred=false mLayoutNeeded=false
              Display areas in top down Z order:
        """.trimIndent() + "\n" + android12Windows().substringAfter("\n")
        assertTrue(DisplaySettings.applied(otherDisplayFirst, 720 to 1440, 320, "lock 1", "lock 1"))
        val withoutOwnConfig = otherDisplayFirst.lines().filterIndexed { i, line ->
            i < 2 || !line.trimStart().startsWith("overrideConfig=")
        }.joinToString("\n")
        assertFalse(DisplaySettings.applied(withoutOwnConfig, 720 to 1440, 320, "lock 1", "lock 1"))
    }

    @Test fun thisDevicesDisplayRestoresExistingOverridesEvenWhenTheBlockFails() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            phoneDisplayRestoresOverridesEvenWhenTheBlockFails(device)
            return phoneDisplayKeepsALockedRotationOrRefuses(device)
        }
        // A phone's home activity may be portrait-only and ignore the user's rotation lock.
        // Use an unrestricted app window to exercise display rotation in both directions.
        ActivityScenario.launch(MidiSelectActivity::class.java).use { scenario ->
            scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            val original = DisplaySettings.read(device)
            val (width, height) = original.size
            val shortest = minOf(width, height)
            DisplaySettings.with(device, shortest to maxOf(width, height), shortest * 160 / 480, 1) {
                assertEquals("lock 1", device.executeShellCommand("wm user-rotation").trim())
                awaitRotation(scenario, 1)
                val outer = DisplaySettings.read(device)
                val failure = IllegalStateException("exercise display restoration")
                try {
                    DisplaySettings.with(device, density = shortest * 160 / 720, rotation = 3) {
                        assertEquals("lock 3", device.executeShellCommand("wm user-rotation").trim())
                        awaitRotation(scenario, 3)
                        throw failure
                    }
                } catch (caught: IllegalStateException) {
                    assertTrue(caught === failure)
                }
                assertEquals(outer, DisplaySettings.read(device))
                awaitRotation(scenario, 1)
            }
            assertEquals(original, DisplaySettings.read(device))
        }
    }

    @Test fun restoresPortraitUserLockWhileTheActivityStillRequiresLandscape() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return rotationLockIsRefusedBeforeAndroid13(device)
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
                    awaitRotation(scenario, 1)
                }
                assertEquals(locked, DisplaySettings.read(device))
                scenario.onActivity { assertEquals(1, it.display!!.rotation) }
            }
        }
        assertEquals(original, DisplaySettings.read(device))
    }

    /**
     * Below Android 13 the phone display changes only size and density, and restores both. The
     * rotation setting found (free or locked to the natural rotation) must survive both exits.
     */
    private fun phoneDisplayRestoresOverridesEvenWhenTheBlockFails(device: UiDevice) {
        val rotation = rotationSetting(device)
        val original = DisplaySettings.readMetrics(device)
        val (width, height) = original.size
        val shortest = minOf(width, height)
        val phone = shortest to maxOf(width, height)
        DisplaySettings.withPhoneDisplay(device, phone, shortest * 160 / 480) {
            val outer = DisplaySettings.readMetrics(device)
            // A size equal to the physical size is reported as no override, so compare what applies.
            assertEquals(phone to shortest * 160 / 480, outer.size to outer.density)
            val failure = IllegalStateException("exercise display restoration")
            val caught = assertThrows(IllegalStateException::class.java) {
                DisplaySettings.withPhoneDisplay(device, phone, shortest * 160 / 720) {
                    assertEquals(shortest * 160 / 720, DisplaySettings.readMetrics(device).density)
                    throw failure
                }
            }
            assertTrue(caught === failure)
            assertEquals(outer, DisplaySettings.readMetrics(device))
        }
        assertEquals(original, DisplaySettings.readMetrics(device))
        SystemClock.sleep(SYSTEM_UI_RELOCK_MS)
        assertEquals("rotation setting after the phone display", rotation, rotationSetting(device))
    }

    /**
     * Below Android 13, a size or density change makes the navigation bar re-lock a locked rotation
     * to the natural one, so a lock to another rotation cannot be kept and is refused untouched.
     */
    private fun phoneDisplayKeepsALockedRotationOrRefuses(device: UiDevice) {
        val saved = rotationSetting(device)
        try {
            setRotationSetting(device, ACCELEROMETER_OFF to "1")
            val original = DisplaySettings.readMetrics(device)
            val (width, height) = original.size
            val shortest = minOf(width, height)
            var ran = false
            val result = runCatching {
                DisplaySettings.withPhoneDisplay(device, shortest to maxOf(width, height), shortest * 160 / 480) { ran = true }
            }
            SystemClock.sleep(SYSTEM_UI_RELOCK_MS)
            assertEquals("rotation setting after the phone display", ACCELEROMETER_OFF to "1", rotationSetting(device))
            assertTrue("expected a refusal, got $result", result.exceptionOrNull() is IllegalStateException)
            assertFalse(ran)
            assertEquals(original, DisplaySettings.readMetrics(device))
        } finally {
            setRotationSetting(device, saved)
        }
    }

    /** The auto-rotate switch and the user rotation, as `settings` stores them on every version. */
    private fun rotationSetting(device: UiDevice) =
        device.executeShellCommand("settings get system accelerometer_rotation").trim() to
            device.executeShellCommand("settings get system user_rotation").trim()

    private fun setRotationSetting(device: UiDevice, setting: Pair<String, String>) {
        for ((name, value) in listOf("accelerometer_rotation" to setting.first, "user_rotation" to setting.second)) {
            device.executeShellCommand(if (value == "null") "settings delete system $name" else "settings put system $name $value")
        }
    }

    /** Android 12's navigation bar would overwrite the lock, so the helper refuses before changing anything. */
    private fun rotationLockIsRefusedBeforeAndroid13(device: UiDevice) {
        val original = DisplaySettings.readMetrics(device)
        val rotation = device.executeShellCommand("wm user-rotation").trim()
        var ran = false
        assertThrows(IllegalStateException::class.java) {
            DisplaySettings.with(device, density = original.density + 1, rotation = 1) { ran = true }
        }
        assertFalse(ran)
        assertEquals(original, DisplaySettings.readMetrics(device))
        assertEquals(rotation, device.executeShellCommand("wm user-rotation").trim())
    }

    private companion object {
        const val ACCELEROMETER_OFF = "0"

        /** An Android 12 emulator re-locked the rotation about 0.3 s after a display change. */
        const val SYSTEM_UI_RELOCK_MS = 2000L
    }

    private fun awaitRotation(scenario: ActivityScenario<MidiSelectActivity>, expected: Int) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        var rotation: Int? = null
        do {
            scenario.onActivity { rotation = it.display!!.rotation }
            if (rotation == expected) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        assertEquals("Requested activity rotation did not reach its window", expected, rotation)
    }

    // The fields used by the wait, as an Android 12 (API 31) emulator printed them with a 720x1440
    // 320dpi override while its portrait home screen kept rotation 0.
    private fun android12Windows() = """
        WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)
          mLastOrientationSource=WindowedMagnification:0:31@217892158
          overrideConfig={1.0 310mcc260mnc [en_US] ldltr sw360dp w360dp h625dp 320dpi nrml long port finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 720, 1440) mAppBounds=Rect(0, 0 - 720, 1314) mMaxBounds=Rect(0, 0 - 720, 1440) mWindowingMode=fullscreen mDisplayWindowingMode=fullscreen mActivityType=undefined mAlwaysOnTop=undefined mRotation=ROTATION_0} s.96 fontWeightAdjustment=0}
          Display: mDisplayId=0 rootTasks=6
            init=1080x2400 420dpi base=720x1440 320dpi cur=720x1440 app=720x1314 rng=720x657-1314x1251
            deferred=false mLayoutNeeded=false mTouchExcludeRegion=SkRegion((0,0,720,1440))
          Display areas in top down Z order:
    """.trimIndent()

    // The fields used by the wait, as Android 16's dumpsys window displays prints them.
    private fun windows() = """
        WINDOW MANAGER DISPLAY CONTENTS (dumpsys window displays)
          Display: mDisplayId=0 (organized)
            init=2560x1600 320dpi cur=2560x1600 app=2560x1600 rng=1600x1600-2560x2560
            deferred=false mLayoutNeeded=false
          overrideConfig={1.0 310mcc260mnc [en_US] ldltr sw480dp w768dp h480dp 533dpi nrml land winConfig={ mBounds=Rect(0, 0 - 2560, 1600) mDisplayRotation=ROTATION_90 mRotation=ROTATION_90}}
    """.trimIndent()
}
