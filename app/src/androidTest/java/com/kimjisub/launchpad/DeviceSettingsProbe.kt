package com.kimjisub.launchpad

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings

/** Confirms settings in the target app process without adding or selecting JUnit tests. */
class DeviceSettingsProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val deadline = SystemClock.uptimeMillis() + 10_000
        var observed: String
        while (true) {
            val fontScale = targetContext.resources.configuration.fontScale
            val animations = listOf(
                Settings.Global.WINDOW_ANIMATION_SCALE,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                Settings.Global.ANIMATOR_DURATION_SCALE,
            ).associateWith { Settings.Global.getFloat(targetContext.contentResolver, it, 1f) }
            observed = "fontScale=$fontScale animations=$animations"
            if (fontScale == 1f && animations.values.all { it == 1f }) {
                finish(Activity.RESULT_OK, Bundle().apply {
                    putString("stream", "$observed\nDEVICE_SETTINGS_READY\n")
                })
                return
            }
            if (SystemClock.uptimeMillis() >= deadline) break
            SystemClock.sleep(100)
        }
        finish(Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", "Device settings were not applied: $observed\n")
        })
    }
}
