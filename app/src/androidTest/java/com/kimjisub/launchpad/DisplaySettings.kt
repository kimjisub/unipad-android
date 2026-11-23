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

    data class State(
        val size: Pair<Int, Int>,
        val density: Int,
        val sizeOverride: Pair<Int, Int>?,
        val densityOverride: Int?,
        val rotation: String,
    )

    fun read(device: UiDevice): State {
        val sizes = device.executeShellCommand("wm size")
        fun size(prefix: String) = sizes.lineSequence().firstOrNull { it.startsWith(prefix) }
            ?.let { SIZE.find(it)!!.destructured.let { (w, h) -> w.toInt() to h.toInt() } }
        val densities = device.executeShellCommand("wm density")
        fun density(prefix: String) = densities.lineSequence().firstOrNull { it.startsWith(prefix) }
            ?.substringAfter(":")?.trim()?.toInt()
        val sizeOverride = size("Override size:")
        val densityOverride = density("Override density:")
        val rotation = device.executeShellCommand("wm user-rotation").trim()
        check(rotation.matches(Regex("free|lock [0-3]"))) { "Cannot save display rotation: $rotation" }
        return State(
            sizeOverride ?: checkNotNull(size("Physical size:")),
            densityOverride ?: checkNotNull(density("Physical density:")),
            sizeOverride, densityOverride, rotation,
        )
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
        val (width, height) = read(device).size
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
     * A phone display of [size] (portrait, as a phone reports it) and [density], turned to landscape as
     * the app runs on a phone. A naturally landscape display (a tablet) given only the size stays
     * portrait and shows the landscape app in a small letterboxed window.
     */
    fun <T> withPhoneDisplay(device: UiDevice, size: Pair<Int, Int>, density: Int, block: () -> T): T =
        with(device, size, density, Surface.ROTATION_90, block)

    /** The display transition encloses overlay changes, so neither apply nor restore races them. */
    fun <T> with(
        device: UiDevice,
        size: Pair<Int, Int>? = null,
        density: Int? = null,
        rotation: Int,
        block: () -> T,
    ): T {
        val previous = read(device)
        var failure: Throwable? = null
        try {
            if (size != null) device.executeShellCommand("wm size ${size.first}x${size.second}")
            if (density != null) device.executeShellCommand("wm density $density")
            device.executeShellCommand("wm user-rotation lock $rotation")
            awaitApplied(device, size ?: previous.size, density ?: previous.density, "lock $rotation")
            return block()
        } catch (t: Throwable) {
            failure = t
            throw t
        } finally {
            try {
                if (size != null) {
                    val old = previous.sizeOverride
                    device.executeShellCommand(if (old == null) "wm size reset" else "wm size ${old.first}x${old.second}")
                }
                if (density != null) {
                    device.executeShellCommand(previous.densityOverride?.let { "wm density $it" } ?: "wm density reset")
                }
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

    internal fun applied(windows: String, size: Pair<Int, Int>, density: Int, userRotation: String, expectedUserRotation: String): Boolean {
        val display = windows.substringAfter("mDisplayId=0 ", "").substringBefore("Display areas")
        val config = display.lineSequence().firstOrNull { it.trimStart().startsWith("overrideConfig=") } ?: return false
        val degrees = Regex("mDisplayRotation=ROTATION_(0|90|180|270)\\b").find(config)?.groupValues?.get(1)?.toInt() ?: return false
        val actualRotation = degrees / 90
        val (width, height) = if (actualRotation % 2 == 0) size else size.second to size.first
        return userRotation == expectedUserRotation &&
            " cur=${width}x$height " in display && " ${density}dpi " in config &&
            "deferred=false mLayoutNeeded=false" in display
    }
}
