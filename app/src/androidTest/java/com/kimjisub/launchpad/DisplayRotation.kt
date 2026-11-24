package com.kimjisub.launchpad

import android.app.UiAutomation
import android.os.SystemClock
import android.view.Surface
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice

/**
 * Freezes the display rotation for tests that resize the display.
 *
 * Android 10 writes a rotation requested within about two seconds after `wm size`, then puts the
 * previous rotation back 1.6–2.5 s later, with or without an orientation request from the app.
 * The rotation can even be reported as complete before it is put back. A request therefore counts
 * only once the display still has the rotation [RESTORE_WINDOW_MS] after it was made.
 */
internal object DisplayRotation {
    private const val TURN_TIMEOUT_MS = 5000L
    private const val RESTORE_WINDOW_MS = 3000L
    private const val POLL_MS = 100L

    /**
     * Freezes the display at [rotation], a `Surface.ROTATION_*` value, and returns how many requests
     * it took, or null when the display did not keep it after [attempts] requests. Callers decide
     * whether that is a failure: some displays legitimately ignore the request.
     */
    fun freeze(device: UiDevice, rotation: Int, attempts: Int = 3): Int? {
        val automation = InstrumentationRegistry.getInstrumentation()
            .getUiAutomation(Configurator.getInstance().uiAutomationFlags)
        for (request in 1..attempts) {
            val requestedAt = SystemClock.elapsedRealtime()
            automation.setRotation(freezeValue(rotation))
            if (kept(device, rotation, requestedAt)) return request
        }
        return null
    }

    private fun kept(device: UiDevice, rotation: Int, requestedAt: Long): Boolean {
        var reached = false
        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - requestedAt
            val current = device.displayRotation == rotation
            if (reached && !current) return false
            reached = reached || current
            if (reached && elapsed >= RESTORE_WINDOW_MS) return true
            if (!reached && elapsed >= TURN_TIMEOUT_MS) return false
            SystemClock.sleep(POLL_MS)
        }
    }

    private fun freezeValue(rotation: Int) = when (rotation) {
        Surface.ROTATION_90 -> UiAutomation.ROTATION_FREEZE_90
        Surface.ROTATION_180 -> UiAutomation.ROTATION_FREEZE_180
        Surface.ROTATION_270 -> UiAutomation.ROTATION_FREEZE_270
        else -> UiAutomation.ROTATION_FREEZE_0
    }
}
