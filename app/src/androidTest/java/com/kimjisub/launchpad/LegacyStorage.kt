package com.kimjisub.launchpad

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals

/**
 * Below Android 11 the app asks for storage access when it starts, and only with that access lists
 * the Documents/Unipad folder as a workspace. A test about something else starts from the state of
 * a user who has already allowed it.
 */
internal object LegacyStorage {
    private val PERMISSIONS = listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    fun grant(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        for (permission in PERMISSIONS) {
            automation.grantRuntimePermission(context.packageName, permission)
            assertEquals("Storage access was not granted: $permission",
                PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(permission))
        }
    }
}
