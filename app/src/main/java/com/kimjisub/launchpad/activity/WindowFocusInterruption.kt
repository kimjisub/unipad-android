package com.kimjisub.launchpad.activity

/** Gives a rotation's short focus transition time to finish before treating it as a covering window. */
internal class WindowFocusInterruption(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    interrupt: () -> Unit,
) {
    private var pending = false
    private val interruption = Runnable {
        pending = false
        interrupt()
    }

    fun onFocusChanged(hasFocus: Boolean) {
        if (hasFocus) cancelPending()
        else if (!pending) {
            pending = true
            schedule(interruption, 500L)
        }
    }

    fun cancelPending() {
        cancel(interruption)
        pending = false
    }
}
