package com.kimjisub.launchpad

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The excerpts keep the lines [SystemWindowState] reads, in the layout an API 35 emulator prints
 * them (and one activity record as API 33 prints it); the last test reads the real output of the
 * device it runs on.
 */
@RunWith(AndroidJUnit4::class)
class SystemWindowStateTest {
    @Test fun resumedDrawnAndFocusedHomeIsSettled() {
        val home = SystemWindowState.home(HOME, activities(), windows())
        assertTrue(home.detail, home.inFront)
        assertTrue(home.detail, home.settled)
    }

    @Test fun homeRecordInTheAndroid13LayoutIsSettled() {
        val record = "ActivityRecord{1ba021d u0 $HOME} t8}"
        val home = SystemWindowState.home(HOME, activities(record = record), windows())
        assertTrue(home.detail, home.inFront)
        assertTrue(home.detail, home.settled)
    }

    @Test fun homeWithAPendingRelaunchOrUndrawnWindowIsNotSettled() {
        for ((name, activities, windows) in listOf(
            Triple("relaunch", activities(extra = "        mPendingRelaunchCount=1\n"), windows()),
            Triple("not all drawn", activities(allDrawn = false), windows()),
            Triple("pausing", activities(state = "PAUSING"), windows()),
            Triple("draw pending", activities(), windows(drawState = "DRAW_PENDING")),
            Triple("window gone", activities(), windows(home = false)),
            Triple("new resources not sent yet", activities(), windows(assets = 5)),
        )) {
            val home = SystemWindowState.home(HOME, activities, windows)
            assertTrue("$name: ${home.detail}", home.inFront)
            assertFalse("$name: ${home.detail}", home.settled)
        }
    }

    @Test fun homeBehindTheNotRespondingDialogIsNotSettledAndTheDialogIsNamed() {
        val windows = windows(dialog = NOT_RESPONDING)
        assertEquals(listOf(LAUNCHER), SystemWindowState.notResponding(windows))
        val home = SystemWindowState.home(HOME, activities(focus = "Application Not Responding: $LAUNCHER"), windows)
        assertTrue(home.detail, home.inFront)
        assertFalse(home.detail, home.settled)
        assertTrue(home.detail, LAUNCHER in home.detail)
    }

    @Test fun onlyAVisibleDialogOwnedByTheSystemCounts() {
        assertEquals(emptyList<String>(), SystemWindowState.notResponding(windows()))
        assertEquals(emptyList<String>(), SystemWindowState.notResponding(windows(dialog = NOT_RESPONDING.replace("isVisible=true", "isVisible=false"))))
        assertEquals(emptyList<String>(), SystemWindowState.notResponding(windows(dialog = NOT_RESPONDING.replace("package=android ", "package=com.example.app "))))
    }

    @Test fun homeIsNotInFrontWhileAnotherAppIsFocused() {
        val app = "ActivityRecord{132351 u0 com.kimjisub.launchpad.dev/com.kimjisub.launchpad.activity.MainActivity t100}"
        val home = SystemWindowState.home(HOME, activities(focusedApp = app), windows())
        assertFalse(home.detail, home.inFront)
    }

    @Test fun thisDevicesHomeScreenReadsAsSettled() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val home = SystemWindowState.homeActivity(device)
        assertNotNull("No home activity resolves", home)
        device.pressHome()
        var state: SystemWindowState.Home
        var activities: String
        var windows: String
        val deadline = SystemClock.elapsedRealtime() + 10000
        do {
            activities = device.executeShellCommand("dumpsys activity activities")
            windows = device.executeShellCommand("dumpsys window windows")
            state = SystemWindowState.home(home!!, activities, windows)
            if (state.inFront && state.settled) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        // The raw output shows which line this device's version prints differently.
        for ((name, text) in listOf("activities" to activities, "windows" to windows)) {
            TestCaptures.save(device, "system-window-state/home-$name.txt") { it.writeText(text); true }
        }
        throw AssertionError("Home $home did not read as settled after pressing Home: inFront=${state.inFront} ${state.detail}")
    }

    private companion object {
        const val LAUNCHER = "com.google.android.apps.nexuslauncher"
        const val HOME = "$LAUNCHER/.NexusLauncherActivity"
        const val RECORD = "ActivityRecord{6e84e7c u0 $HOME t99}"
        const val WINDOW = "$LAUNCHER/$LAUNCHER.NexusLauncherActivity"
        val NOT_RESPONDING = """
            |  Window #3 Window{203ccac u0 Application Not Responding: $LAUNCHER}:
            |    mDisplayId=0 rootTaskId=1 mSession=Session{8f02b1e 615:1000} mClient=android.os.BinderProxy@b1f
            |    mOwnerUid=1000 showForAllUsers=true package=android appop=SYSTEM_ALERT_WINDOW
            |      Surface: shown=true      mDrawState=HAS_DRAWN       mLastHidden=false
            |    isOnScreen=true
            |    isVisible=true
            |""".trimMargin()

        fun activities(
            state: String = "RESUMED",
            allDrawn: Boolean = true,
            extra: String = "",
            focus: String = WINDOW,
            record: String = RECORD,
            focusedApp: String = record,
        ) = """
            |ACTIVITY MANAGER ACTIVITIES (dumpsys activity activities)
            |Display #0 (activities from top to bottom):
            |  * Task{c81b024 #1 type=home U=0 visible=true visibleRequested=true mode=fullscreen translucent=false sz=1}
            |      topResumedActivity=$record
            |      * Hist  #1: $record
            |        packageName=$LAUNCHER processName=$LAUNCHER
            |        mLastReportedConfigurations:
            |          mGlobalConfig={1.0 310mcc260mnc [en_US] ldltr sw411dp w411dp h914dp 420dpi nrml long port finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1080, 2400) mRotation=ROTATION_0} as.4 s.99 fontWeightAdjustment=0}
            |          mOverrideConfig={1.0 310mcc260mnc [en_US] ldltr sw411dp w411dp h914dp 420dpi nrml long port finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1080, 2400) mActivityType=home} as.4 s.8 fontWeightAdjustment=0}
            |        stateNotNeeded=true componentSpecified=true mActivityType=home
            |        mHaveState=false mIcicle=null
            |        state=$state delayedResume=false finishing=false
            |        mVisibleRequested=true mVisible=true mClientVisible=true reportedDrawn=true reportedVisible=true
            |        mNumInterestingWindows=1 mNumDrawnWindows=${if (allDrawn) 1 else 0} allDrawn=$allDrawn lastAllDrawn=false)
            |""".trimMargin() + extra + """
            |      * TaskFragment{cb7bd72 mode=multi-window organizerUid=10177 organizerProc=$LAUNCHER}
            |
            |ActivityTaskSupervisor state:
            |  topDisplayFocusedRootTask=Task{c81b024 #1 type=home}
            |  mCurrentFocus=Window{4885e20 u0 $focus}
            |  mFocusedApp=$focusedApp
            |""".trimMargin()

        fun windows(drawState: String = "HAS_DRAWN", home: Boolean = true, dialog: String = "", assets: Int = 4) = buildString {
            appendLine("WINDOW MANAGER WINDOWS (dumpsys window windows)")
            appendLine("  Window #0 Window{2641eac u0 ScreenDecorOverlayBottom}:")
            appendLine("    mOwnerUid=10179 showForAllUsers=true package=com.android.systemui appop=NONE")
            appendLine("    isVisible=true")
            append(dialog)
            if (home) {
                appendLine("  Window #8 Window{4885e20 u0 $WINDOW}:")
                appendLine("    mOwnerUid=10177 showForAllUsers=false package=$LAUNCHER appop=NONE")
                appendLine("    mToken=$RECORD")
                appendLine("      Surface: shown=true      mDrawState=$drawState       mLastHidden=false")
                appendLine("    isOnScreen=true")
                appendLine("    isVisible=true")
            }
            appendLine("  Window #9 Window{3124aa4 u0 com.android.systemui.wallpapers.ImageWallpaper}:")
            appendLine("    isVisible=true")
            appendLine()
            appendLine("  mGlobalConfiguration={1.0 310mcc260mnc [en_US] ldltr sw411dp w411dp h914dp 420dpi nrml long port finger -keyb/v/h -nav/h winConfig={ mBounds=Rect(0, 0 - 1080, 2400) mRotation=ROTATION_0} as.$assets s.70 fontWeightAdjustment=0}")
            appendLine("  mHasPermanentDpad=false")
        }
    }
}
