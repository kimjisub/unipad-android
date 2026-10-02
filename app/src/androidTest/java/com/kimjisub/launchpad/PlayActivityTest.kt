package com.kimjisub.launchpad

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.kimjisub.design.view.ChainView
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.design.R as DesignR
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * PlayActivity Tests
 * Tests for play activity features (controls, recording, LED, volume, etc.)
 *
 * Every test plays [TestUniPack] (8x8, 2 chains, LED and autoPlay, silent sounds). The play
 * screen is Compose: options live in a side panel opened from the Menu button of the right
 * chrome column, AutoPlay is a "Play Mode" in that panel with its transport in the chrome
 * column, Back toggles the panel and Quit (in the panel) returns to the main screen.
 * Pads and chain buttons are plain views without ids; taps use their actual native bounds.
 */
@RunWith(AndroidJUnit4::class)
class PlayActivityTest : BaseUITest() {

    private fun enterPlay() {
        clearClipboard()
        launchToMainScreen()
        openTestPackInPlay()
    }

    private fun assertStillPlaying(message: String) {
        assertTrue(message, waitUntil { isOnPlayScreen() })
    }

    /** Tap a Play Mode in the option panel; returns [SystemClock.elapsedRealtime] of the tap. */
    private fun selectPlayMode(labelRes: Int): Long {
        openPlayOptions()
        assertTrue("Play mode '${str(labelRes)}' could not be tapped", clickFresh { playMode(labelRes) })
        val selectedAt = SystemClock.elapsedRealtime()
        assertTrue("Play mode selection did not close the options", device.wait(Until.gone(By.res("play_options")), 5000L))
        return selectedAt
    }

    private fun hasTransport(): Boolean =
        device.hasObject(By.desc(str(R.string.cd_autoplay_next)))

    private fun clickTransport(descRes: Int) {
        val clicked = clickFresh { device.findObject(By.desc(str(descRes))) }
        assertTrueWithLabels("AutoPlay button '${str(descRes)}' not found", clicked)
    }

    private val clipboard by lazy { context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager }

    /** Text of the clipboard, or null when empty; [enterPlay] clears it so a stale log never counts. */
    private fun clipboardText(): String? {
        var text: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text
                ?.toString()?.takeIf { it.isNotEmpty() }
        }
        return text
    }

    /** Play screen geometry read from the view tree: [padArea] of the helper reads the same actual native bounds. */
    private class PlayGeometry(val safe: Rect, val pads: Rect, val chains: List<Rect>)

    /**
     * Pad grid and chain buttons on screen, and the area not covered by system bars or the display
     * cutout. Edge-to-edge windows (targetSdk 35+ on Android 15+) draw under both.
     */
    private fun playGeometry(): PlayGeometry {
        var geometry: PlayGeometry? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).single()
            val decor = activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)
                ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val safe = screenBounds(decor).apply {
                if (insets != null) {
                    left += insets.left; top += insets.top; right -= insets.right; bottom -= insets.bottom
                }
            }
            val shown = mutableListOf<View>()
            fun collect(view: View) {
                if (view.visibility != View.VISIBLE) return
                shown += view
                if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
            }
            collect(decor)
            val pads = shown.filterIsInstance<PadView>().map(::screenBounds)
                .reduceOrNull { acc, r -> Rect(acc).apply { union(r) } } ?: Rect()
            geometry = PlayGeometry(safe, pads, shown.filterIsInstance<ChainView>().map(::screenBounds))
        }
        return geometry!!
    }

    private fun screenBounds(view: View): Rect {
        val origin = IntArray(2).also { view.getLocationOnScreen(it) }
        return Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
    }

    private fun assertInside(safe: Rect, name: String, bounds: Rect) {
        assertTrue("$name $bounds reaches outside the safe area $safe", safe.contains(bounds))
    }

    private fun clearClipboard() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) clipboard.clearPrimaryClip()
            else clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }

    @Test
    fun testPlayActivityControls() {
        enterPlay()
        takeScreenshot("playactivity_controls_start")

        openPlayOptions()
        togglePlayOption(R.string.feedbackLight)
        takeScreenshot("playactivity_feedback_light_toggle")
        togglePlayOption(R.string.led)
        takeScreenshot("playactivity_led_toggle")
        closePlayOptions()

        // AutoPlay with Feedback light on keeps the screen busy until it is paused
        withShortIdleWait {
            val selectedAt = selectPlayMode(R.string.autoPlay)
            fun sinceSelected() = SystemClock.elapsedRealtime() - selectedAt
            assertTrue("AutoPlay controls did not appear", waitUntil { hasTransport() })
            Thread.sleep(1000)
            val stillRunning = hasTransport()
            val checkedAfter = sinceSelected()
            assertTrue(
                "AutoPlay was checked $checkedAfter ms after selecting it but lasts " +
                    "${TestUniPack.AUTO_PLAY_MS} ms, too late to tell whether it stopped early",
                checkedAfter < TestUniPack.AUTO_PLAY_MS
            )
            assertTrueWithLabels("AutoPlay controls gone $checkedAfter ms after selecting it", stillRunning)
            takeScreenshot("playactivity_autoplay_toggle")

            val wasPlaying = waitUntil(1000L) { device.hasObject(By.desc(str(R.string.cd_autoplay_pause))) }
            clickTransport(if (wasPlaying) R.string.cd_autoplay_pause else R.string.cd_autoplay_play)
            val flipped = if (wasPlaying) R.string.cd_autoplay_play else R.string.cd_autoplay_pause
            assertTrue(
                "AutoPlay Play/Pause did not switch",
                device.wait(Until.hasObject(By.desc(str(flipped))), 3000L)
            )
        }
        takeScreenshot("playactivity_autoplay_play")

        tapPad(3, 3)
        takeScreenshot("playactivity_pad_touch")
        assertStillPlaying("App left PlayActivity while operating controls")

        quitPlayToMain()
        takeScreenshot("playactivity_controls_end")
    }

    @Test
    fun testPlayActivityChainSwitching() {
        enterPlay()
        takeScreenshot("chain_switching_start")

        // Record while switching so the chain change can be read back from the log
        openPlayOptions()
        togglePlayOption(R.string.record)
        closePlayOptions()

        assertTrue(
            "Expected ${TestUniPack.CHAINS} chain buttons",
            chainButtons().size == TestUniPack.CHAINS
        )
        tapChain(1)
        Thread.sleep(300)
        takeScreenshot("chain_1_selected")
        tapPad(2, 2)
        tapChain(0)
        Thread.sleep(300)
        takeScreenshot("chain_0_selected")
        tapPad(4, 4)
        takeScreenshot("chain_pad_interaction")

        openPlayOptions()
        togglePlayOption(R.string.record)
        assertTrue("Recording was not copied to the clipboard", waitUntil { clipboardText() != null })
        val log = clipboardText().orEmpty().lines()
        val toTwo = log.indexOf("chain 2")
        assertTrue("Chain switch to 2 was not recorded: $log", toTwo >= 0)
        assertTrue("Chain switch back to 1 was not recorded: $log", log.indexOf("chain 1") > toTwo)
        closePlayOptions()

        assertStillPlaying("App left PlayActivity during chain switching")
        quitPlayToMain()
        takeScreenshot("chain_switching_end")
    }

    @Test
    fun testPlayActivityRecording() {
        enterPlay()
        takeScreenshot("recording_start")

        openPlayOptions()
        togglePlayOption(R.string.traceLog)
        takeScreenshot("recording_tracelog_enabled")
        togglePlayOption(R.string.record)
        takeScreenshot("recording_enabled")
        closePlayOptions()

        tapPad(2, 2)
        takeScreenshot("recording_touch_1")
        tapPad(2, 5)
        takeScreenshot("recording_touch_2")
        tapPad(5, 3)
        takeScreenshot("recording_touch_3")

        tapChain(1)
        Thread.sleep(300)
        takeScreenshot("recording_chain_switch")
        tapPad(4, 4)
        takeScreenshot("recording_touch_after_chain")

        // Stopping the recording copies the log to the clipboard
        openPlayOptions()
        togglePlayOption(R.string.record)
        takeScreenshot("recording_stopped")
        assertTrue("Recording was not copied to the clipboard", waitUntil { clipboardText() != null })
        val log = clipboardText().orEmpty()
        assertTrue("Recording does not start with the chain: $log", log.startsWith("c 1"))
        assertTrue("Chain switch was not recorded: $log", log.lines().contains("chain 2"))

        togglePlayOption(R.string.traceLog)
        assertStillPlaying("App left PlayActivity during recording test")

        quitPlayToMain()
        takeScreenshot("recording_end")
    }

    @Test
    fun testPlayActivityVolumeControl() {
        enterPlay()
        takeScreenshot("volume_control_start")

        repeat(3) { i ->
            device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_UP)
            Thread.sleep(300)
            takeScreenshot("volume_up_pressed_${i + 1}")
        }
        repeat(2) { i ->
            device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
            Thread.sleep(300)
            takeScreenshot("volume_down_pressed_${i + 1}")
        }

        waitForVolumePanelGone()
        tapPad(3, 3)
        takeScreenshot("volume_pad_touch_after_adjustment")
        assertStillPlaying("App left PlayActivity during volume control")

        repeat(10) {
            device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
            Thread.sleep(100)
        }
        takeScreenshot("volume_minimum")
        tapPad(3, 3)

        repeat(15) {
            device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_UP)
            Thread.sleep(100)
        }
        takeScreenshot("volume_maximum")
        tapPad(3, 3)

        repeat(7) {
            device.pressKeyCode(android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
            Thread.sleep(100)
        }
        takeScreenshot("volume_middle")
        waitForVolumePanelGone()
        assertStillPlaying("App left PlayActivity after volume changes")

        quitPlayToMain()
        takeScreenshot("volume_control_end")
    }

    @Test
    fun testPlayActivityAutoPlayControls() {
        launchToMainScreen()
        takeScreenshot("autoplay_controls_start")

        selectTestPack()
        takeScreenshot("autoplay_pack_selected")

        openTestPackInPlay()
        takeScreenshot("autoplay_play_activity_opened")

        selectPlayMode(R.string.autoPlay)
        assertTrue("AutoPlay controls did not appear", waitUntil { hasTransport() })
        takeScreenshot("autoplay_checkbox_enabled")
        takeScreenshot("autoplay_started")

        clickTransport(R.string.cd_autoplay_pause)
        assertTrue(
            "AutoPlay did not pause",
            device.wait(Until.hasObject(By.desc(str(R.string.cd_autoplay_play))), 3000L)
        )
        takeScreenshot("autoplay_paused")

        clickTransport(R.string.cd_autoplay_next)
        takeScreenshot("autoplay_next_clicked")
        clickTransport(R.string.cd_autoplay_prev)
        takeScreenshot("autoplay_prev_clicked")

        clickTransport(R.string.cd_autoplay_play)
        assertTrue(
            "AutoPlay did not resume",
            device.wait(Until.hasObject(By.desc(str(R.string.cd_autoplay_pause))), 3000L)
        )
        takeScreenshot("autoplay_resumed")

        clickTransport(R.string.cd_autoplay_pause)
        assertTrue(
            "AutoPlay did not stop",
            device.wait(Until.hasObject(By.desc(str(R.string.cd_autoplay_play))), 3000L)
        )
        takeScreenshot("autoplay_stopped")

        // Selecting the active mode again turns AutoPlay off
        selectPlayMode(R.string.autoPlay)
        assertTrue("AutoPlay controls did not hide", waitUntil { !hasTransport() })
        takeScreenshot("autoplay_checkbox_disabled")
        assertStillPlaying("App left PlayActivity after AutoPlay controls test")

        quitPlayToMain()
        takeScreenshot("autoplay_controls_test_end")
    }

    @Test
    fun testPlayActivityPadViewPatterns() {
        enterPlay()
        takeScreenshot("padview_patterns_start")

        // Pattern 1: four corners
        listOf(0 to 0, 0 to 7, 7 to 0, 7 to 7).forEach { (x, y) -> tapPad(x, y) }
        takeScreenshot("padview_pattern_corners")

        // Pattern 2: horizontal line
        for (y in 0..7) tapPad(3, y)
        takeScreenshot("padview_pattern_horizontal")

        // Pattern 3: vertical line
        for (x in 0..7) tapPad(x, 3)
        takeScreenshot("padview_pattern_vertical")

        // Pattern 4 / 5: both diagonals
        for (i in 0..7) tapPad(i, i)
        takeScreenshot("padview_pattern_diagonal1")
        for (i in 0..7) tapPad(i, 7 - i)
        takeScreenshot("padview_pattern_diagonal2")

        // Pattern 6: ring around the center
        listOf(2 to 3, 2 to 4, 3 to 5, 4 to 5, 5 to 4, 5 to 3, 4 to 2, 3 to 2).forEach { (x, y) -> tapPad(x, y) }
        takeScreenshot("padview_pattern_circle")

        // Pattern 7: rapid touches on the center pads
        repeat(10) { i -> tapPad(3 + i % 2, 3 + i / 2 % 2) }
        takeScreenshot("padview_pattern_rapid")

        // Pattern 8: 3x3 grid
        for (x in 2..4) for (y in 2..4) tapPad(x, y)
        takeScreenshot("padview_pattern_grid")

        // Pattern 9: spiral outwards
        listOf(3 to 3, 3 to 4, 4 to 4, 4 to 3, 4 to 2, 3 to 2, 2 to 2, 2 to 3, 2 to 4, 2 to 5)
            .forEach { (x, y) -> tapPad(x, y) }
        takeScreenshot("padview_pattern_spiral")

        // Pattern 10: zigzag
        for (x in 0..7) tapPad(x, if (x % 2 == 0) 1 else 6)
        takeScreenshot("padview_pattern_zigzag")

        assertStillPlaying("App left PlayActivity during PadView pattern test")
        takeScreenshot("padview_patterns_final")

        quitPlayToMain()
        assertTrue("Test pack not listed after returning", device.hasObject(By.textContains(TestUniPack.TITLE)))
        takeScreenshot("padview_patterns_end")
    }

    /** Former FAB "Load UniPack": now the sort bar's Import icon, which opens the system file picker. */
    @Test
    fun testImportOpensPickerAndCancelReturnsToMain() {
        launchToMainScreen()
        takeScreenshot("before_load_unipack")

        device.findObject(By.desc(str(R.string.import_unipack))).click()
        assertTrue(
            "File picker did not open",
            device.wait(Until.gone(By.pkg(PACKAGE_NAME)), 5000L)
        )
        takeScreenshot("after_load_unipack_click")

        // Close the file picker without choosing a file
        repeat(3) {
            if (waitForMainScreen(1500L)) return@repeat
            device.pressBack()
        }
        assertTrue("App is not in a normal state after Load UniPack test", waitForMainScreen())
        takeScreenshot("load_unipack_test_end")
    }

    /** Former FAB "Reconnect Launchpad": now Settings > Device > Reconnect Launchpad. */
    @Test
    fun testReconnectOpensDeviceSelectionFromSettings() {
        launchToMainScreen()
        takeScreenshot("before_reconnect_launchpad")

        device.findObject(By.desc(str(R.string.setting))).click()
        val reconnect = device.wait(Until.findObject(By.textStartsWith(str(R.string.reconnect_launchpad))), 5000L)
        assertNotNull("Reconnect Launchpad not found in settings", reconnect)
        reconnect!!.click()
        assertTrue(
            "Launchpad connection screen did not open",
            device.wait(Until.gone(By.text(str(R.string.settings_storage))), 5000L)
        )
        takeScreenshot("after_reconnect_click")

        device.pressBack()
        assertTrue(
            "Did not return to settings",
            device.wait(Until.hasObject(By.text(str(R.string.settings_storage))), 5000L)
        )
        device.pressBack()
        assertTrue("App is not in a normal state after Reconnect Launchpad test", waitForMainScreen())
        takeScreenshot("reconnect_launchpad_test_end")
    }

    @Test
    fun testPlayActivityLEDAnimation() {
        enterPlay()
        takeScreenshot("led_start")

        openPlayOptions()
        if (!isPlayOptionChecked(R.string.led)) togglePlayOption(R.string.led)
        closePlayOptions()
        takeScreenshot("led_enabled")

        // Pad (1,1) of chain 1 carries the pack's LED animation
        tapPad(0, 0)
        listOf(1 to 3, 3 to 5, 5 to 3, 3 to 1).forEach { (x, y) -> tapPad(x, y) }
        takeScreenshot("led_pattern_cross")
        listOf(2 to 2, 2 to 5, 5 to 5, 5 to 2).forEach { (x, y) -> tapPad(x, y) }
        takeScreenshot("led_pattern_diagonal")
        repeat(8) { tapPad(0, 0) }
        takeScreenshot("led_pattern_rapid")
        repeat(5) { tapPad(3, 3) }
        takeScreenshot("led_pattern_center")

        openPlayOptions()
        togglePlayOption(R.string.led)
        closePlayOptions()
        takeScreenshot("led_disabled")
        tapPad(0, 0)
        tapPad(4, 4)
        takeScreenshot("led_disabled_touch")

        openPlayOptions()
        togglePlayOption(R.string.led)
        closePlayOptions()
        takeScreenshot("led_re_enabled")
        tapPad(0, 0)
        takeScreenshot("led_re_enabled_touch")

        openPlayOptions()
        if (!isPlayOptionChecked(R.string.feedbackLight)) togglePlayOption(R.string.feedbackLight)
        closePlayOptions()
        takeScreenshot("led_and_feedback_both_enabled")
        for (y in 1..4) tapPad(3, y)
        takeScreenshot("led_and_feedback_touch")

        assertStillPlaying("App left PlayActivity during LED animation test")
        quitPlayToMain()
        takeScreenshot("led_end")
    }

    @Test
    fun testPlayActivityTraceLogFeature() {
        enterPlay()
        takeScreenshot("tracelog_play_activity_started")

        openPlayOptions()
        togglePlayOption(R.string.traceLog)
        closePlayOptions()
        takeScreenshot("tracelog_enabled")

        listOf(2 to 2, 2 to 5, 3 to 3, 5 to 2, 5 to 5).forEach { (x, y) -> tapPad(x, y) }
        takeScreenshot("tracelog_sequential_touches")
        repeat(5) { tapPad(3, 3) }
        takeScreenshot("tracelog_repeated_touches")
        repeat(10) { i -> tapPad(i % 8, (i * 3) % 8) }
        takeScreenshot("tracelog_rapid_touches")

        tapChain(1)
        takeScreenshot("tracelog_chain_switched")
        listOf(2 to 2, 2 to 5, 3 to 3, 5 to 2, 5 to 5).forEach { (x, y) -> tapPad(x, y) }
        takeScreenshot("tracelog_new_chain_touches")

        tapChain(0)
        takeScreenshot("tracelog_chain_back")
        tapPad(3, 1)
        tapPad(3, 6)
        takeScreenshot("tracelog_original_chain_preserved")

        openPlayOptions()
        togglePlayOption(R.string.traceLog)
        closePlayOptions()
        takeScreenshot("tracelog_disabled")
        tapPad(2, 3)
        tapPad(4, 3)
        takeScreenshot("tracelog_disabled_touches")

        openPlayOptions()
        togglePlayOption(R.string.traceLog)
        closePlayOptions()
        takeScreenshot("tracelog_re_enabled")
        listOf(2 to 3, 3 to 4, 4 to 3, 3 to 2).forEach { (x, y) -> tapPad(x, y) }
        takeScreenshot("tracelog_re_enabled_touches")

        assertStillPlaying("App left PlayActivity during TraceLog test")
        quitPlayToMain()
        takeScreenshot("tracelog_test_end")
    }

    /**
     * With Settings > Play > "Show tap order numbers on pads" on, the numbers only appear once the
     * play screen's Trace Log switch is on, which is what the setting's description tells the user.
     * Pads are not in the accessibility tree, so the numbers are read from the pads' trace log views.
     */
    @Test
    fun testTapOrderNumbersNeedTraceLogSwitch() {
        val prefs = PreferenceManager(context)
        val original = prefs.traceLogClassic
        try {
            prefs.traceLogClassic = true
            enterPlay()

            tapPad(2, 2)
            SystemClock.sleep(1000)
            assertEquals("Tap order numbers appeared while Trace Log was off", emptyMap<String, Rect>(), shownTapOrders())

            openPlayOptions()
            togglePlayOption(R.string.traceLog)
            closePlayOptions()
            listOf(2 to 2, 2 to 5, 3 to 3, 2 to 2).forEach { (x, y) -> tapPad(x, y) }
            val expected = mapOf("1 4" to padBounds(2, 2), "2" to padBounds(2, 5), "3" to padBounds(3, 3))
            waitUntil(5000L) { shownTapOrders().keys == expected.keys }
            takeScreenshot("tap_order_numbers")

            val shown = shownTapOrders()
            assertEquals("Tap orders shown on the pads", expected.keys, shown.keys)
            expected.forEach { (order, pad) ->
                val at = shown.getValue(order)
                assertTrue("Tap order \"$order\" is at $at, not on pad $pad", pad.contains(at.centerX(), at.centerY()))
            }

            openPlayOptions()
            togglePlayOption(R.string.traceLog)
            closePlayOptions()
            quitPlayToMain()
        } finally {
            prefs.traceLogClassic = original
        }
    }

    private fun padBounds(x: Int, y: Int): Rect {
        val area = padArea()
        val cell = area.width() / 8
        return Rect(area.left + cell * y, area.top + cell * x, area.left + cell * (y + 1), area.top + cell * (x + 1))
    }

    /** Non-empty pad trace log texts on the resumed play screen, whitespace collapsed, with their screen bounds. */
    private fun shownTapOrders(): Map<String, Rect> {
        val shown = mutableMapOf<String, Rect>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .firstOrNull() ?: return@runOnMainSync
            fun collect(view: View) {
                if (view is TextView && view.id == DesignR.id.traceLog && view.isShown) {
                    val text = view.text.trim().split(Regex("\\s+")).joinToString(" ")
                    if (text.isNotEmpty()) {
                        val xy = IntArray(2).also(view::getLocationOnScreen)
                        shown[text] = Rect(xy[0], xy[1], xy[0] + view.width, xy[1] + view.height)
                    }
                }
                if (view is ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
            }
            collect(activity.window.decorView)
        }
        return shown
    }

    @Test
    fun testPlayActivityUIVisibilityFeatures() {
        enterPlay()
        takeScreenshot("ui_visibility_test_start")

        // Hide UI hides the chrome column (and its Menu button) until it is turned off again
        openPlayOptions()
        togglePlayOption(R.string.hideUI)
        closePlayOptions()
        assertTrue(
            "Hide UI did not hide the Menu button",
            device.wait(Until.gone(By.desc(str(R.string.menu))), 3000L)
        )
        takeScreenshot("hideui_enabled")
        tapPad(3, 3)
        tapPad(2, 2)
        takeScreenshot("hideui_enabled_with_touches")

        // Back still opens the option panel while the UI is hidden
        openPlayOptionsWithBack()
        togglePlayOption(R.string.hideUI)
        closePlayOptions()
        assertTrue("Menu button did not return", waitForPlayScreen(3000L))
        takeScreenshot("hideui_disabled")
        tapPad(3, 3)
        takeScreenshot("hideui_disabled_with_touches")

        openPlayOptions()
        val watermark = togglePlayOption(R.string.watermark)
        closePlayOptions()
        takeScreenshot("watermark_enabled")
        tapPad(3, 3)
        takeScreenshot("watermark_enabled_with_touches")
        openPlayOptions()
        assertTrue("Watermark did not toggle back", togglePlayOption(R.string.watermark) != watermark)
        closePlayOptions()
        takeScreenshot("watermark_disabled")

        // Both at once, then back off
        openPlayOptions()
        togglePlayOption(R.string.hideUI)
        togglePlayOption(R.string.watermark)
        closePlayOptions()
        takeScreenshot("hideui_watermark_both_enabled")
        listOf(2 to 2, 2 to 5, 5 to 2, 5 to 5).forEach { (x, y) -> tapPad(x, y) }
        takeScreenshot("hideui_watermark_both_enabled_with_touches")
        openPlayOptionsWithBack()
        togglePlayOption(R.string.hideUI)
        togglePlayOption(R.string.watermark)
        takeScreenshot("hideui_watermark_both_disabled")

        // Repeated toggles (stability), ending in the original state
        repeat(6) { togglePlayOption(R.string.hideUI) }
        takeScreenshot("hideui_toggle_test")
        repeat(6) { togglePlayOption(R.string.watermark) }
        takeScreenshot("watermark_toggle_test")
        closePlayOptions()

        assertStillPlaying("App left PlayActivity during UI visibility features test")
        quitPlayToMain()
        takeScreenshot("ui_visibility_test_end")
    }

    @Test
    fun testPlayActivityRecordingClipboard() {
        enterPlay()
        takeScreenshot("recording_clipboard_playactivity")

        openPlayOptions()
        togglePlayOption(R.string.traceLog)
        takeScreenshot("recording_clipboard_tracelog_enabled")
        togglePlayOption(R.string.record)
        takeScreenshot("recording_clipboard_recording_enabled")
        closePlayOptions()

        listOf(1 to 1, 1 to 6, 3 to 3, 6 to 1, 6 to 6).forEachIndexed { i, (x, y) ->
            tapPad(x, y)
            Thread.sleep(200)
            takeScreenshot("recording_clipboard_touch_${i + 1}")
        }

        // Stopping the recording copies the log to the clipboard
        openPlayOptions()
        togglePlayOption(R.string.record)
        takeScreenshot("recording_clipboard_stopped")

        assertTrue(
            "Clipboard is empty. Clipboard copy was not triggered when recording stopped.",
            waitUntil { clipboardText() != null }
        )
        val clipText = clipboardText().orEmpty()
        println("=== Clipboard contents ===\n$clipText\n=== End of clipboard contents ===")
        assertTrue(
            "Recording data is not in the correct format in clipboard. Contents: $clipText",
            clipText.startsWith("c ")
        )
        assertTrue(
            "Touches were not recorded (${clipText.length} chars): $clipText",
            clipText.lines().count { it.startsWith("t ") } >= 5
        )

        togglePlayOption(R.string.traceLog)
        closePlayOptions()
        assertStillPlaying("App left PlayActivity during recording clipboard test")

        quitPlayToMain()
        takeScreenshot("recording_clipboard_end")
    }

    @Test
    fun testPlayScreenStaysClearOfSystemBarsAndCutout() {
        enterPlay()
        takeScreenshot("safe_area_play")
        val geometry = playGeometry()
        val safe = geometry.safe

        assertTrue("Pads not found on the play screen", !geometry.pads.isEmpty)
        assertInside(safe, "Pad grid", geometry.pads)
        assertTrue("Chain buttons not found on the play screen", geometry.chains.isNotEmpty())
        geometry.chains.forEachIndexed { i, chain -> assertInside(safe, "Chain button ${i + 1}", chain) }
        val menu = device.findObject(By.desc(str(R.string.menu)))
        assertNotNull("Menu button not found", menu)
        assertInside(safe, "Menu button", menu!!.visibleBounds)

        openPlayOptions()
        takeScreenshot("safe_area_options")
        val quit = device.findObject(By.desc(str(R.string.quit)))
        assertNotNull("Quit not found in the play option panel", quit)
        assertInside(safe, "Quit button", quit!!.visibleBounds)
        closePlayOptions()

        quitPlayToMain()
    }

    private fun openPlayOptionsWithBack() {
        device.pressBack()
        assertTrue("Back did not open the play option panel", waitUntil { isPlayOptionsOpen() })
    }
}
