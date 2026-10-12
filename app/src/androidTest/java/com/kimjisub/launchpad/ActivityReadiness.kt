package com.kimjisub.launchpad

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.UiDevice
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Window readiness shared by launcher and feature tests, within their existing deadlines. */
internal object ActivityReadiness {
    private data class WindowState(val laidOut: Boolean, val focused: Boolean)

    fun await(
        context: Context,
        device: UiDevice,
        timeoutMillis: Long,
        type: Class<out Activity>? = null,
        language: String? = null,
    ) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            val state = try {
                onMain(deadline - SystemClock.elapsedRealtime()) {
                    val activity = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).singleOrNull()
                    val decor = activity?.window?.decorView
                    WindowState(
                        activity?.packageName == context.packageName &&
                            (type == null || type.isInstance(activity)) && decor != null &&
                            decor.isShown && decor.isLaidOut && decor.width > 0 && decor.height > 0 &&
                            (language == null || activity.resources.configuration.locales[0].language == language),
                        decor?.hasWindowFocus() == true,
                    )
                }
            } catch (_: TimeoutException) {
                break
            }
            // An app-owned dialog may have focus while the activity below it stays resumed.
            val focused = state.focused || (state.laidOut &&
                InstrumentationRegistry.getInstrumentation().uiAutomation.windows.any { window ->
                    window.isFocused && window.root?.packageName == context.packageName
                })
            if (state.laidOut && focused && SystemClock.elapsedRealtime() <= deadline) return
            SystemClock.sleep(minOf(25L, (deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0)))
        }
        capture(context, device, "missing-ready-${SystemClock.uptimeMillis()}")
        throw AssertionError("${type?.simpleName ?: context.packageName} window did not become ready (language=$language)")
    }

    /** A blocked main thread must not extend the launch or screen-readiness deadline. */
    fun <T> onMain(timeoutMillis: Long, block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task = FutureTask(block)
        Handler(Looper.getMainLooper()).post(task)
        return try {
            task.get(timeoutMillis.coerceAtLeast(0), TimeUnit.MILLISECONDS)
        } finally {
            task.cancel(false)
        }
    }

    fun capture(context: Context, device: UiDevice, name: String) {
        val folder = "activity-readiness-captures"
        runCatching { check(TestCaptures.screenshot(device, "$folder/$name.png")) }
            .onFailure { println("Readiness screenshot failed: $it") }
        captureState(context, device, folder, name)
    }

    fun captureState(context: Context, device: UiDevice, folder: String, name: String) {
        val state = runCatching {
            onMain(1000) {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .joinToString("\n") { activity ->
                        val decor = activity.window.decorView
                        "${activity.javaClass.name} locale=${activity.resources.configuration.locales} " +
                            "focus=${decor.hasWindowFocus()} shown=${decor.isShown} " +
                            "laidOut=${decor.isLaidOut} size=${decor.width}x${decor.height}"
                    }.ifEmpty { "No resumed activity" }
            }
        }.getOrElse { "Could not read resumed activity: $it" }
        val focus = runCatching {
            InstrumentationRegistry.getInstrumentation().uiAutomation.windows.joinToString("\n") {
                "window=${it.id} focused=${it.isFocused} package=${it.root?.packageName}"
            }
        }.getOrElse { "Could not read focused windows: $it" }
        runCatching {
            check(TestCaptures.save(device, "$folder/$name-state.txt") {
                it.writeText("target=${context.packageName}\n$state\n$focus\n")
                true
            })
        }
            .onFailure { println("Readiness state save failed: $it") }
        runCatching {
            check(TestCaptures.save(device, "$folder/$name.xml") {
                device.dumpWindowHierarchy(it)
                true
            })
        }
            .onFailure { println("Readiness hierarchy save failed: $it") }
    }
}
