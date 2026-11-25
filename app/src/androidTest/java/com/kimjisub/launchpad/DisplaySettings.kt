package com.kimjisub.launchpad

import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.SystemClock
import android.view.Surface
import androidx.test.uiautomator.UiDevice

/** Device-wide display overrides, restored only after their window configuration has settled. */
internal object DisplaySettings {
    private const val APPLY_TIMEOUT_MS = 10000L
    private val SIZE = Regex("""(\d+)x(\d+)""")
    private val ROTATION_FIELDS = listOf("mDisplayRotation", "mRotation")
        .map { Regex("\\b$it=ROTATION_(0|90|180|270)\\b") }

    data class State(
        val size: Pair<Int, Int>,
        val density: Int,
        val sizeOverride: Pair<Int, Int>?,
        val densityOverride: Int?,
        val rotation: String,
    )

    /** The size and density overrides, which `wm` has on every supported version. */
    internal data class Metrics(
        val size: Pair<Int, Int>,
        val density: Int,
        val sizeOverride: Pair<Int, Int>?,
        val densityOverride: Int?,
    )

    internal fun readMetrics(device: UiDevice): Metrics {
        val sizes = device.executeShellCommand("wm size")
        fun size(prefix: String) = sizes.lineSequence().firstOrNull { it.startsWith(prefix) }
            ?.let { SIZE.find(it)!!.destructured.let { (w, h) -> w.toInt() to h.toInt() } }
        val densities = device.executeShellCommand("wm density")
        fun density(prefix: String) = densities.lineSequence().firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(":")?.trim()?.toInt()
        val sizeOverride = size("Override size:")
        val densityOverride = density("Override density:")
        return Metrics(
            sizeOverride ?: checkNotNull(size("Physical size:")),
            densityOverride ?: checkNotNull(density("Physical density:")),
            sizeOverride, densityOverride,
        )
    }

    /** Also reads the user rotation preference, which `wm` can read back from Android 12. */
    fun read(device: UiDevice): State {
        val metrics = readMetrics(device)
        val rotation = device.executeShellCommand("wm user-rotation").trim()
        check(rotation.matches(Regex("free|lock [0-3]"))) { "Cannot save display rotation: $rotation" }
        return State(metrics.size, metrics.density, metrics.sizeOverride, metrics.densityOverride, rotation)
    }

    private fun setSize(device: UiDevice, size: Pair<Int, Int>?) {
        device.executeShellCommand(size?.let { "wm size ${it.first}x${it.second}" } ?: "wm size reset")
    }

    private fun setDensity(device: UiDevice, density: Int?) {
        device.executeShellCommand(density?.let { "wm density $it" } ?: "wm density reset")
    }

    /**
     * From Android 16, a display at least 600dp wide ignores the orientation an app targeting
     * API 36 or later requests, so such a test has to turn the display itself.
     */
    fun ignoresOrientationRequests(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 36 && context.applicationInfo.targetSdkVersion >= 36 &&
            context.resources.configuration.smallestScreenWidthDp >= 600

    /** The display rotation that shows [orientation], an [ActivityInfo] screen orientation, on this display. */
    fun rotationShowing(device: UiDevice, orientation: Int): Int {
        val (width, height) = readMetrics(device).size
        val naturallyLandscape = width > height
        return when (orientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE ->
                if (naturallyLandscape) Surface.ROTATION_0 else Surface.ROTATION_90
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE ->
                if (naturallyLandscape) Surface.ROTATION_180 else Surface.ROTATION_270
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ->
                if (naturallyLandscape) Surface.ROTATION_270 else Surface.ROTATION_0
            else -> throw IllegalArgumentException("No single display rotation shows orientation $orientation")
        }
    }

    /**
     * Turns the display itself to the orientation an activity requested, on a display that ignores
     * that request ([ignoresOrientationRequests]); elsewhere it does nothing. Closing it returns the
     * rotation preference saved before the first turn and waits until the display has applied it.
     */
    class OrientationOverride(private val device: UiDevice, private val context: Context) : AutoCloseable {
        private var previous: State? = null

        fun show(orientation: Int) {
            if (!ignoresOrientationRequests(context)) return
            if (previous == null) previous = read(device)
            device.executeShellCommand("wm user-rotation lock ${rotationShowing(device, orientation)}")
        }

        override fun close() {
            val state = previous ?: return
            previous = null
            device.executeShellCommand("wm user-rotation ${state.rotation}")
            awaitApplied(device, state.size, state.density, state.rotation)
        }
    }

    /**
     * A phone display of [size] (portrait, as a phone reports it) and [density], turned to landscape as
     * the app runs on a phone. A naturally landscape display (a tablet) given only the size stays
     * portrait and shows the landscape app in a small letterboxed window. Before Android 12 `wm`
     * cannot read back the user rotation preference to restore it, and on Android 12 a size or
     * density change re-locks a rotation lock to the portrait home screen's rotation (see [with]),
     * so through Android 12 only the size and density change, and the landscape app turns a phone
     * display itself.
     */
    fun <T> withPhoneDisplay(device: UiDevice, size: Pair<Int, Int>, density: Int, block: () -> T): T =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) with(device, size, density, Surface.ROTATION_90, block)
        else withSizeAndDensity(device, size, density, block)

    private fun <T> withSizeAndDensity(device: UiDevice, size: Pair<Int, Int>, density: Int, block: () -> T): T {
        val previous = readMetrics(device)
        try {
            setSize(device, size)
            setDensity(device, density)
            return block()
        } finally {
            setSize(device, previous.sizeOverride)
            setDensity(device, previous.densityOverride)
        }
    }

    /**
     * The display transition encloses overlay changes, so neither apply nor restore races them.
     * Requires Android 13: on Android 12 the navigation bar is rebuilt after a size or density
     * change, and while the rotation is locked and the display shows its natural rotation, it
     * re-locks the rotation there, overwriting the lock this writes or restores.
     */
    fun <T> with(
        device: UiDevice,
        size: Pair<Int, Int>? = null,
        density: Int? = null,
        rotation: Int,
        block: () -> T,
    ): T {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "A rotation lock with a size or density change needs Android 13; use withPhoneDisplay"
        }
        val previous = read(device)
        var failure: Throwable? = null
        try {
            if (size != null) setSize(device, size)
            if (density != null) setDensity(device, density)
            device.executeShellCommand("wm user-rotation lock $rotation")
            awaitApplied(device, size ?: previous.size, density ?: previous.density, "lock $rotation")
            return block()
        } catch (t: Throwable) {
            failure = t
            throw t
        } finally {
            try {
                if (size != null) setSize(device, previous.sizeOverride)
                if (density != null) setDensity(device, previous.densityOverride)
                device.executeShellCommand("wm user-rotation ${previous.rotation}")
                awaitApplied(device, previous.size, previous.density, previous.rotation)
            } catch (t: Throwable) {
                failure?.addSuppressed(t) ?: throw t
            }
        }
    }

    /**
     * Reads the applied window configuration, rather than the overrides the shell just accepted.
     * The user rotation lock is a preference: a portrait-only launcher or a landscape activity can
     * override it. Wait for size, density and layout at the actual rotation; callers that require a
     * particular orientation check it after opening their activity. The preference itself must read
     * back exactly as written before the same deadline, even when the activity ignores it.
     */
    private fun awaitApplied(device: UiDevice, size: Pair<Int, Int>, density: Int, expectedUserRotation: String) {
        var windows = ""
        var userRotation = ""
        val deadline = SystemClock.elapsedRealtime() + APPLY_TIMEOUT_MS
        do {
            userRotation = device.executeShellCommand("wm user-rotation").trim()
            windows = device.executeShellCommand("dumpsys window displays")
            if (applied(windows, size, density, userRotation, expectedUserRotation)) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Display did not reach size=$size density=$density userRotation=$expectedUserRotation (was $userRotation) at the actual rotation within ${APPLY_TIMEOUT_MS}ms: $windows")
    }

    /**
     * Android 13 and later print the display's own configuration after its `Display:` line and name
     * its rotation `mDisplayRotation`; Android 12 prints it just before that line, with only `mRotation`.
     */
    internal fun applied(windows: String, size: Pair<Int, Int>, density: Int, userRotation: String, expectedUserRotation: String): Boolean {
        if ("mDisplayId=0 " !in windows) return false
        val display = windows.substringAfter("mDisplayId=0 ").substringBefore("Display areas")
        fun String.isConfig() = trimStart().startsWith("overrideConfig=")
        val config = display.lineSequence().firstOrNull { it.isConfig() }
            ?: windows.substringBefore("mDisplayId=0 ").substringAfterLast("mDisplayId=").lineSequence().lastOrNull { it.isConfig() }
            ?: return false
        val degrees = (ROTATION_FIELDS.firstNotNullOfOrNull { it.find(config) } ?: return false).groupValues[1].toInt()
        val actualRotation = degrees / 90
        val (width, height) = if (actualRotation % 2 == 0) size else size.second to size.first
        return userRotation == expectedUserRotation &&
            " cur=${width}x$height " in display && " ${density}dpi " in config &&
            "deferred=false mLayoutNeeded=false" in display
    }
}
