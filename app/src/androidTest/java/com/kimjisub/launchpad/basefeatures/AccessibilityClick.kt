package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import androidx.test.uiautomator.StaleObjectException

/** Accessibility actions address the view rather than coordinates from a previous rotation. */
internal class AccessibilityClick(
    private val now: () -> Long = SystemClock::elapsedRealtime,
    private val pause: (Long) -> Unit = SystemClock::sleep,
) {
    /** Null means not found; false is a rejected action, which must fail without sending it again. */
    fun perform(label: String, click: () -> Boolean?) {
        val deadline = now() + 10000
        var attempts = 0
        var stale = 0
        var lastStale: StaleObjectException? = null
        do {
            attempts++
            val accepted = try {
                click()
            } catch (failure: StaleObjectException) {
                lastStale = failure
                stale++
                println("$label: accepted=not-sent attempt=$attempts stale=$stale")
                null
            }
            if (accepted != null) {
                println("$label: accepted=$accepted attempt=$attempts stale=$stale")
                if (accepted) return
                throw AssertionError("$label was not accepted", lastStale)
            }
            val remaining = deadline - now()
            if (remaining > 0) pause(minOf(25L, remaining))
        } while (now() < deadline)
        throw AssertionError("$label timed out after 10000 ms (attempts=$attempts stale=$stale)", lastStale)
    }
}
