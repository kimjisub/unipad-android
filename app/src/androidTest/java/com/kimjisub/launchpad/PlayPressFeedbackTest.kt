package com.kimjisub.launchpad

import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.PlayActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The play screen's controls show their press without a framework ripple. Material's ripple is a
 * RippleDrawable in a RippleHostView, whose animations run on the render thread; graphics library
 * crashes while playing had that path on their main thread side.
 *
 * Each control is held down and the play screen's windows (its own and its dialog's) are read for a
 * ripple view or drawable while it is pressed, with a screenshot before and during the press; after
 * release the control must still act.
 */
@RunWith(AndroidJUnit4::class)
class PlayPressFeedbackTest : BaseUITest() {

    /** Ripple views found while each control was held, kept so every control is pressed and captured. */
    private val ripplesWhileHeld = linkedMapOf<String, List<String>>()

    @Test
    fun playControlsActWithoutFrameworkRipple() {
        launchToMainScreen()
        openTestPackInPlay()

        holdAndRelease("Menu", By.desc(str(R.string.menu)))
        assertTrueWithLabels("Menu did not open the play option panel", waitUntil(5000L) { isPlayOptionsOpen() })

        val feedbackBefore = isPlayOptionChecked(R.string.feedbackLight)
        holdAndRelease("Feedback light row") { playOption(R.string.feedbackLight) }
        assertTrue("Feedback light row did not toggle", waitUntil { isPlayOptionChecked(R.string.feedbackLight) != feedbackBefore })

        holdAndRelease("Auto mapping row") { playMode(R.string.autoMapping) }
        assertTrueWithLabels("Auto mapping did not ask first", device.wait(Until.hasObject(By.text(str(R.string.autoMappingConfirm))), 5000L))
        holdAndRelease("Dialog Cancel button", By.text(str(R.string.cancel)), windows = 2)
        assertTrue("Cancel did not close the dialog", device.wait(Until.gone(By.text(str(R.string.autoMappingConfirm))), 5000L))

        holdAndRelease("AutoPlay mode") { playMode(R.string.autoPlay) }
        assertTrue("AutoPlay did not close the options", device.wait(Until.gone(By.res("play_options")), 5000L))
        withShortIdleWait {
            assertTrue("AutoPlay controls did not appear", waitUntil { device.hasObject(By.desc(str(R.string.cd_autoplay_pause))) })
            holdAndRelease("Pause button", By.desc(str(R.string.cd_autoplay_pause)))
            assertTrue("Pause did not pause", device.wait(Until.hasObject(By.desc(str(R.string.cd_autoplay_play))), 3000L))
        }

        assertEquals("Ripple views or drawables while held", emptyMap<String, List<String>>(), ripplesWhileHeld.filterValues { it.isNotEmpty() })
        assertEquals("Ripple views or drawables left on the play screen", emptyList<String>(), rippleViews())
        quitPlayToMain()
    }

    private fun holdAndRelease(name: String, selector: BySelector, windows: Int = 1) =
        holdAndRelease(name, windows) { device.findObject(selector) }

    /**
     * Hold [find]'s control down past the press delay of a scrolling panel, read the play screen's
     * [windows] windows, then release.
     */
    private fun holdAndRelease(name: String, windows: Int = 1, find: () -> UiObject2?) {
        var center: android.graphics.Point? = null
        assertTrueWithLabels("$name not found", waitUntil {
            try {
                center = find()?.visibleCenter
                center != null
            } catch (_: StaleObjectException) {
                false
            }
        })
        val x = center!!.x.toFloat()
        val y = center!!.y.toFloat()
        val shot = "press_" + name.lowercase().replace(' ', '_')
        takeScreenshot("${shot}_idle")
        val downTime = SystemClock.uptimeMillis()
        inject(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0))
        try {
            Thread.sleep(HOLD_MS)
            takeScreenshot("${shot}_held")
            ripplesWhileHeld[name] = rippleViews(windows)
        } finally {
            inject(MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0))
        }
    }

    private fun inject(event: MotionEvent) {
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        assertTrue("Touch could not be injected", InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true))
        event.recycle()
    }

    /**
     * Compose ripple host views, and any view drawing a RippleDrawable, in the play screen's windows:
     * its own and its dialogs', which carry its token. The main screen's window stays behind it.
     */
    private fun rippleViews(windows: Int = 1): List<String> {
        val found = mutableListOf<String>()
        var screen: String? = null
        var windowsRead = 0
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val play = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).singleOrNull()
            screen = play?.javaClass?.name
            if (play !is PlayActivity) return@runOnMainSync
            val token = play.window.attributes.token
            val roots = windowRoots().filter { (it.layoutParams as? WindowManager.LayoutParams)?.token == token }
            windowsRead = roots.size
            fun collect(view: View) {
                val name = view.javaClass.name
                if (name.startsWith(COMPOSE_RIPPLE_PACKAGE) || view.background is RippleDrawable || view.foreground is RippleDrawable) {
                    found += name
                }
                if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
            }
            roots.forEach(::collect)
        }
        assertEquals("Resumed screen", PlayActivity::class.java.name, screen)
        assertEquals("Play screen windows read", windows, windowsRead)
        return found
    }

    @Suppress("UNCHECKED_CAST")
    private fun windowRoots(): List<View> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            WindowInspector.getGlobalWindowViews()
        } else {
            val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)!!
            val views = global.javaClass.getDeclaredField("mViews").apply { isAccessible = true }.get(global)
            ArrayList(views as List<View>)
        }

    private companion object {
        const val HOLD_MS = 600L
        const val COMPOSE_RIPPLE_PACKAGE = "androidx.compose.material.ripple."
    }
}
