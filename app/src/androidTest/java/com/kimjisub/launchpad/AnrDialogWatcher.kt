package com.kimjisub.launchpad

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import kotlin.concurrent.thread

/**
 * Presses Wait on the system "isn't responding" dialog for as long as it is open.
 * On a stalled emulator a background job can miss its deadline; the dialog then covers the app and
 * every following screen wait runs out behind it. Waiting leaves the app running, so the test still
 * judges the app's own screens and fails on its own deadlines if the app never recovers.
 */
class AnrDialogWatcher : AutoCloseable {

    private val worker = thread(name = "anr-dialog-watcher", isDaemon = true) {
        try {
            while (!Thread.currentThread().isInterrupted) {
                pressWaitIfShown()
                Thread.sleep(POLL_INTERVAL_MS)
            }
        } catch (_: InterruptedException) {
        }
    }

    private fun pressWaitIfShown() {
        try {
            // The flags UiDevice connected with: other flags would reconnect UI automation under the test.
            val automation = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation(Configurator.getInstance().uiAutomationFlags)
            val roots = automation.windows.mapNotNull { it.root } + listOfNotNull(automation.rootInActiveWindow)
            for (root in roots) {
                val wait = root.findAccessibilityNodeInfosByViewId(WAIT_BUTTON_ID).firstOrNull() ?: continue
                if (wait.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.w(TAG, "Pressed Wait on the system not-responding dialog")
                    return
                }
            }
        } catch (e: RuntimeException) {
            // UI automation is gone while the instrumentation shuts down; the next poll retries otherwise.
            Log.d(TAG, "Not-responding dialog check failed: ${e.message}")
        }
    }

    override fun close() {
        worker.interrupt()
        worker.join(STOP_TIMEOUT_MS)
    }

    private companion object {
        const val TAG = "AnrDialogWatcher"
        const val WAIT_BUTTON_ID = "android:id/aerr_wait"
        const val POLL_INTERVAL_MS = 1000L
        const val STOP_TIMEOUT_MS = 2000L
    }
}
