package com.kimjisub.launchpad

import android.os.Build
import android.os.SystemClock
import android.util.Log
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
 *
 * A navigation overlay change also makes the home app relaunch, and with a busy device that takes
 * several seconds. Opening an app screen while it is relaunching made the system time out pausing
 * it and show its "isn't responding" dialog over the app. So whenever the home app is in front, the
 * navigation wait first lets it settle from any earlier change, and after the change waits until it
 * has relaunched and drawn again (see [SystemWindowState.home]). A home app behind another screen
 * relaunches only when it comes back to the front, which the next navigation change waits for.
 */
object DisplayOverlays {
    private const val NAVIGATION = "com.android.internal.systemui.navbar."
    private const val CUTOUT = "com.android.internal.display.cutout.emulation."
    const val THREE_BUTTON_NAVIGATION = NAVIGATION + "threebutton"
    const val GESTURAL_NAVIGATION = NAVIGATION + "gestural"
    const val HOLE_CUTOUT = CUTOUT + "hole"
    private const val APPLY_TIMEOUT_MS = 10000L
    private const val POLL_MS = 100L
    private const val HOME_STEADY_MS = 500L
    private const val TASKBAR_MIN_SMALLEST_WIDTH_DP = 600
    private const val TAG = "DisplayOverlays"
    private val CAN_LOOK_UP_OVERLAYS = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    private val NAVIGATION_BAR_SOURCE =
        Regex("""InsetsSource [^\n]*type=(?:ITYPE_NAVIGATION_BAR|navigationBars) frame=\[(-?\d+),(-?\d+)]\[(-?\d+),(-?\d+)][^\n]*""")
    private val SIZE = Regex("""(\d+)x(\d+)""")

    fun <T> with(device: UiDevice, vararg overlays: String, block: () -> T): T {
        val previous = overlays.map(::categoryOf).associateWith { enabledIn(device, it) }
        val waits = NAVIGATION in previous && CAN_LOOK_UP_OVERLAYS
        val awaitApplied = {
            if (waits) {
                awaitNavigationBar(device)
                awaitHomeSettled(device)
            }
        }
        var failure: Throwable? = null
        try {
            if (waits) awaitHomeSettled(device)
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
        val (displayWidth, displayHeight) = displaySize(device)
        val longestSide = maxOf(displayWidth, displayHeight)
        // From Android 12L, three-button navigation on a tablet is provided by the taskbar.
        // Its inset uses taskbar_frame_height, not the phone navigation_bar_height resources.
        val tablet = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2 &&
            minOf(displayWidth, displayHeight) / scale >= TASKBAR_MIN_SMALLEST_WIDTH_DP
        val taskbarHeightDp = if (tablet) dip(lookup(device, "dimen/taskbar_frame_height")) else null
        // Older launchers may not have transient taskbars. Read their size only when the
        // navigation source advertises the transient taskbar's resizing behavior.
        val transientTaskbarHeightDp by lazy {
            val homePackage = checkNotNull(SystemWindowState.homeActivity(device)).substringBefore('/')
            dip(lookup(device, "dimen/transient_taskbar_stashed_height", homePackage))
        }
        var state = ""
        val deadline = SystemClock.elapsedRealtime() + APPLY_TIMEOUT_MS
        do {
            val reported = device.executeShellCommand("settings get secure navigation_mode").trim()
            val source = NAVIGATION_BAR_SOURCE.find(device.executeShellCommand("dumpsys window displays"))
            val bar = source?.destructured?.toList()?.map(String::toInt)
            val flags = source?.value.orEmpty().substringAfter("flags=", "").substringBefore(' ')
            state = "mode=$reported bar=$bar flags=$flags"
            if (reported == mode && bar != null) {
                val (left, top, right, bottom) = bar
                val width = right - left
                val height = bottom - top
                val expected = when {
                    width <= height -> widthDp
                    tablet && mode == "0" -> taskbarHeightDp!!
                    tablet && mode == "2" && "ANIMATE_RESIZING" in flags -> transientTaskbarHeightDp
                    tablet && mode == "2" && "SUPPRESS_SCRIM" in flags && "INSETS_ROUNDED_CORNER" in flags -> taskbarHeightDp!!
                    width >= longestSide -> landscapeHeightDp
                    else -> heightDp
                }
                if (minOf(width, height) == (expected * scale).roundToInt()) return
            }
            SystemClock.sleep(POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError(
            "Navigation bar did not reach mode $mode (height ${heightDp}dp, landscape ${landscapeHeightDp}dp, " +
                "width ${widthDp}dp, taskbar ${taskbarHeightDp}dp) within ${APPLY_TIMEOUT_MS}ms: $state" + SystemWindowState.describeNotResponding(device),
        )
    }

    /**
     * Waits, when the home app is in front, until it has stayed settled for [HOME_STEADY_MS]: a
     * relaunch the overlay change caused may start a moment after the navigation bar has changed.
     * Its "isn't responding" dialog is never answered; the wait fails naming it instead.
     */
    private fun awaitHomeSettled(device: UiDevice) {
        val home = SystemWindowState.homeActivity(device) ?: return
        var steadySince: Long? = null
        var detail = ""
        val start = SystemClock.elapsedRealtime()
        val deadline = start + APPLY_TIMEOUT_MS
        do {
            val state = SystemWindowState.home(
                home,
                device.executeShellCommand("dumpsys activity activities"),
                device.executeShellCommand("dumpsys window windows"),
            )
            if (!state.inFront) return
            detail = state.detail
            val now = SystemClock.elapsedRealtime()
            if (!state.settled) {
                steadySince = null
            } else {
                val since = steadySince ?: now.also { steadySince = it }
                if (now - since >= HOME_STEADY_MS) {
                    Log.i(TAG, "Home app $home settled ${since - start}ms after the wait began")
                    return
                }
            }
            SystemClock.sleep(POLL_MS)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError(
            "Home app $home did not settle within ${APPLY_TIMEOUT_MS}ms around a navigation overlay change " +
                "(a wait on the system, not on the app under test): $detail" + SystemWindowState.describeNotResponding(device),
        )
    }

    private fun lookup(device: UiDevice, resource: String, packageName: String = "android"): String {
        val value = device.executeShellCommand("cmd overlay lookup $packageName $packageName:$resource").trim()
        check(value.isNotEmpty() && value.first().isDigit()) {
            "Cannot read $packageName:$resource for the navigation overlay wait (API ${Build.VERSION.SDK_INT}): $value"
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
