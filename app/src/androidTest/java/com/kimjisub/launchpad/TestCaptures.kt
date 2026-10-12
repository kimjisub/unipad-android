package com.kimjisub.launchpad

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

/** Shell-owned evidence survives Gradle uninstalling the target and test applications. */
internal object TestCaptures {
    const val DIRECTORY = "/data/local/tmp/unipad_tests"

    fun screenshot(device: UiDevice, relativePath: String): Boolean =
        save(device, relativePath) { device.takeScreenshot(it) }

    /** Stage through app-writable storage, then copy as shell before the app can be uninstalled. */
    fun save(device: UiDevice, relativePath: String, write: (File) -> Boolean): Boolean {
        require(relativePath.matches(Regex("[A-Za-z0-9_./-]+")) &&
            !File(relativePath).isAbsolute && relativePath.split('/').all {
            it.isNotEmpty() && it != "." && it != ".."
        }) { "Capture path must stay inside $DIRECTORY" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val staging = File.createTempFile("ui-capture-", ".tmp", requireNotNull(context.getExternalFilesDir(null)))
        val target = File(DIRECTORY, relativePath)
        return try {
            if (!write(staging)) return false
            // UiAutomation executes argument lists, so each command needs no shell quoting or operators.
            device.executeShellCommand("mkdir -p ${target.parent}")
            device.executeShellCommand("rm -f ${target.path}")
            device.executeShellCommand("cp ${staging.path} ${target.path}")
            val size = device.executeShellCommand("stat -c %s ${target.path}").trim().toLongOrNull()
            val saved = size != null && size > 0 && size == staging.length()
            if (saved) println("Capture saved: ${target.path}")
            saved
        } finally {
            staging.delete()
        }
    }

}
