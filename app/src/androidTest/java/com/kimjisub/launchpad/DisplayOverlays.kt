package com.kimjisub.launchpad

import android.os.Build
import android.os.SystemClock
import androidx.test.uiautomator.UiDevice
import kotlin.math.roundToInt

/**
 * Switches the system's emulated navigation mode or display cutout for the length of a block, then
 * puts back whatever each category had before. These are device-wide settings: one left on runs
 * every later test on the same emulator under it.
 *
 * `cmd overlay` returns before the system has applied the change: on Android 13 the navigation bar
 * window takes one to three seconds to follow. Starting another overlay change or a rotation in that
 * time can leave the bar, and the windows laid out around it, in a mode no overlay asks for. So when
 * a navigation overlay is switched, both the change and the restore wait until the navigation bar
 * has the mode and size the enabled overlays define. That wait reads the overlays' values with
 * `cmd overlay lookup`, which Android 11 added, so below it a navigation overlay is switched without
 * waiting, as before. Switching only the cutout does not wait on any version.
 */
object DisplayOverlays {
    private const val NAVIGATION = "com.android.internal.systemui.navbar."
    private const val CUTOUT = "com.android.internal.display.cutout.emulation."
    const val THREE_BUTTON_NAVIGATION = NAVIGATION + "threebutton"
    const val GESTURAL_NAVIGATION = NAVIGATION + "gestural"
    const val HOLE_CUTOUT = CUTOUT + "hole"
    private const val APPLY_TIMEOUT_MS = 10000L
    private const val POLL_MS = 100L
    private val CAN_LOOK_UP_OVERLAYS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    private val NAVIGATION_BAR_SOURCE =
        Regex("""InsetsSource [^\n]*type=(?:ITYPE_NAVIGATION_BAR|navigationBars) frame=\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)]""")
    private val SIZE = Regex("""(\d+)x(\d+)""")

    fun <T> with(device: UiDevice, vararg overlays: String, block: () -> T): T {
        val previous = overlays.map(::categoryOf).associateWith { enabledIn(device, it) }
        val awaitApplied = { if (NAVIGATION in previous && CAN_LOOK_UP_OVERLAYS) awaitNavigationBar(device) }
        var failure: Throwable? = null
        try {
            overlays.forEach {
                val result = device.executeShellCommand("cmd overlay enable-exclusive --category $it")
                check(result.isBlank()) { "Could not enable $it: $result" }
            }
            awaitApplied()
            return block()
        } catch (t: Throwable) {
            failure = t
            throw t
        } finally {
            previous.forEach { (category, overlay) ->
                if (overlay != null) device.executeShellCommand("cmd overlay enable-exclusive --category $overlay")
                else enabledIn(device, category)?.let { device.executeShellCommand("cmd overlay disable $it") }
            }
            try {
                awaitApplied()
            } catch (t: Throwable) {
                failure?.addSuppressed(t) ?: throw t
            }
        }
    }

    /**
     * Waits until SystemUI reports the navigation mode the overlays now define and the navigation bar
     * window is as thick as that mode makes it: a bar along the bottom takes the bar height for the
     * display's orientation, a bar on a side takes the bar width.
     */
    private fun awaitNavigationBar(device: UiDevice) {
        val mode = lookup(device, "integer/config_navBarInteractionMode")
        val heightDp = dip(lookup(device, "dimen/navigation_bar_height"))
        val landscapeHeightDp = dip(lookup(device, "dimen/navigation_bar_height_landscape"))
        val widthDp = dip(lookup(device, "dimen/navigation_bar_width"))
        val scale = density(device) / 160f
        val longestSide = displaySize(device).let { (w, h) -> maxOf(w, h) }
        var state = ""
        val deadline = SystemClock.elapsedRealtime() + APPLY_TIMEOUT_MS
        do {
            val reported = device.executeShellCommand("settings get secure navigation_mode").trim()
            val bar = NAVIGATION_BAR_SOURCE.find(device.executeShellCommand("dumpsys window displays"))
                ?.destructured?.toList()?.map(String::toInt)
            state = "mode=$reported bar=$bar"
            if (reported == mode && bar != null) {
                val (left, top, right, bottom) = bar
                val width = right - left
                val height = bottom - top
                val expected = when {
                    width <= height -> widthDp
                    width >= longestSide -> landscapeHeightDp
                    else -> heightDp
                }
                if (minOf(width, height) == (expected * scale).roundToInt()) return
            }
            SystemClock.sleep(POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError(
            "Navigation bar did not reach mode $mode (height ${heightDp}dp, landscape ${landscapeHeightDp}dp, " +
                "width ${widthDp}dp) within ${APPLY_TIMEOUT_MS}ms: $state",
        )
    }

    private fun lookup(device: UiDevice, resource: String): String {
        val value = device.executeShellCommand("cmd overlay lookup android android:$resource").trim()
        check(value.isNotEmpty() && value.first().isDigit()) {
            "Cannot read android:$resource for the navigation overlay wait (API ${Build.VERSION.SDK_INT}): $value"
        }
        return value
    }

    private fun dip(value: String) = value.removeSuffix("dip").toFloat()

    private fun density(device: UiDevice): Int {
        val output = device.executeShellCommand("wm density")
        val value = Regex("""Override density: (\d+)""").find(output) ?: Regex("""Physical density: (\d+)""").find(output)!!
        return value.groupValues[1].toInt()
    }

    private fun displaySize(device: UiDevice): Pair<Int, Int> {
        val output = device.executeShellCommand("wm size")
        val line = output.lines().firstOrNull { it.startsWith("Override size") } ?: output.lines().first { it.startsWith("Physical size") }
        return SIZE.find(line)!!.destructured.let { (w, h) -> w.toInt() to h.toInt() }
    }

    private fun categoryOf(overlay: String) = listOf(NAVIGATION, CUTOUT).first { overlay.startsWith(it) }

    private fun enabledIn(device: UiDevice, category: String): String? =
        device.executeShellCommand("cmd overlay list").lines().map { it.trim() }
            .filter { it.startsWith("[x]") }.map { it.substringAfter("]").trim() }
            .firstOrNull { it.startsWith(category) }
}
