package com.kimjisub.launchpad

import android.os.SystemClock
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
            awaitApplied(device, size ?: previous.size, density ?: previous.density, rotation)
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
                val locked = previous.rotation.removePrefix("lock ").toIntOrNull()
                awaitApplied(device, previous.size, previous.density, locked)
            } catch (t: Throwable) {
                failure?.addSuppressed(t) ?: throw t
            }
        }
    }

    /** Reads the applied window configuration, rather than the overrides the shell just accepted. */
    private fun awaitApplied(device: UiDevice, size: Pair<Int, Int>, density: Int, rotation: Int?) {
        var windows = ""
        val deadline = SystemClock.elapsedRealtime() + APPLY_TIMEOUT_MS
        do {
            windows = device.executeShellCommand("dumpsys window displays")
            if (applied(windows, size, density, rotation)) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        throw AssertionError("Display did not reach size=$size density=$density rotation=$rotation within ${APPLY_TIMEOUT_MS}ms: $windows")
    }

    internal fun applied(windows: String, size: Pair<Int, Int>, density: Int, rotation: Int?): Boolean {
        val display = windows.substringAfter("mDisplayId=0 ", "").substringBefore("Display areas")
        val config = display.lineSequence().firstOrNull { it.trimStart().startsWith("overrideConfig=") } ?: return false
        val degrees = Regex("mDisplayRotation=ROTATION_(0|90|180|270)\\b").find(config)?.groupValues?.get(1)?.toInt() ?: return false
        val actualRotation = degrees / 90
        val (width, height) = if (actualRotation % 2 == 0) size else size.second to size.first
        return (rotation == null || rotation == actualRotation) &&
            " cur=${width}x$height " in display && " ${density}dpi " in config &&
            "deferred=false mLayoutNeeded=false" in display
    }
}
