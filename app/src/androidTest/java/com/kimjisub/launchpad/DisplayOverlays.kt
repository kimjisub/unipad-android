package com.kimjisub.launchpad

import androidx.test.uiautomator.UiDevice

/**
 * Switches the system's emulated navigation mode or display cutout for the length of a block, then
 * puts back whatever each category had before. These are device-wide settings: one left on runs
 * every later test on the same emulator under it.
 */
object DisplayOverlays {
    private const val NAVIGATION = "com.android.internal.systemui.navbar."
    private const val CUTOUT = "com.android.internal.display.cutout.emulation."
    const val THREE_BUTTON_NAVIGATION = NAVIGATION + "threebutton"
    const val GESTURAL_NAVIGATION = NAVIGATION + "gestural"
    const val HOLE_CUTOUT = CUTOUT + "hole"

    fun <T> with(device: UiDevice, vararg overlays: String, block: () -> T): T {
        val previous = overlays.map(::categoryOf).associateWith { enabledIn(device, it) }
        try {
            overlays.forEach {
                val result = device.executeShellCommand("cmd overlay enable-exclusive --category $it")
                check(result.isBlank()) { "Could not enable $it: $result" }
            }
            return block()
        } finally {
            previous.forEach { (category, overlay) ->
                if (overlay != null) device.executeShellCommand("cmd overlay enable-exclusive --category $overlay")
                else enabledIn(device, category)?.let { device.executeShellCommand("cmd overlay disable $it") }
            }
        }
    }

    private fun categoryOf(overlay: String) = listOf(NAVIGATION, CUTOUT).first { overlay.startsWith(it) }

    private fun enabledIn(device: UiDevice, category: String): String? =
        device.executeShellCommand("cmd overlay list").lines().map { it.trim() }
            .filter { it.startsWith("[x]") }.map { it.substringAfter("]").trim() }
            .firstOrNull { it.startsWith(category) }
}
