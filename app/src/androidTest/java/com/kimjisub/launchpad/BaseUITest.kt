package com.kimjisub.launchpad

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.Assert.assertNotNull
import org.junit.Before
import java.io.File

/**
 * Base class for UI Automator tests
 * Provides common setup and helper methods
 */
abstract class BaseUITest {

    protected lateinit var device: UiDevice
    protected lateinit var context: Context

    companion object {
        const val LAUNCH_TIMEOUT = 10000L
        const val PACKAGE_NAME = "com.kimjisub.launchpad.dev" // debug build
    }

    @Before
    fun setup() {
        // Initialize UiDevice instance
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = ApplicationProvider.getApplicationContext()

        // Press Home button to start from a clean state
        device.pressHome()

        // Wait until the home screen appears
        val launcherPackage = device.launcherPackageName
        assertNotNull(launcherPackage)
        device.wait(
            androidx.test.uiautomator.Until.hasObject(By.pkg(launcherPackage).depth(0)),
            LAUNCH_TIMEOUT
        )
    }

    /**
     * Launch the app
     */
    protected fun launchApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE_NAME)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        assertNotNull("Could not find launch intent for the app", intent)
        context.startActivity(intent)
    }

    /**
     * Handle permission dialogs (multiple possible)
     */
    protected fun handlePermissionDialogs(maxAttempts: Int = 5) {
        repeat(maxAttempts) {
            if (!handlePermissionDialog()) {
                return // No more permission dialogs
            }
            Thread.sleep(1000) // Brief wait for the next dialog
        }
    }

    /**
     * Handle a single permission dialog
     * @return true if dialog was found and handled, false otherwise
     */
    protected fun handlePermissionDialog(vararg _keywords: String): Boolean {
        // Find "Allow" button (supporting multiple languages)
        val allowButtons = listOf(
            "허용", "Allow", "ALLOW",
            "앱 사용 중에만 허용", "While using the app",
            "이번만 허용", "Only this time"
        )

        for (buttonText in allowButtons) {
            try {
                // Find button by text
                val allowButton = device.findObject(
                    UiSelector()
                        .textMatches(".*${buttonText}.*")
                        .clickable(true)
                )

                if (allowButton.exists()) {
                    println("Permission dialog found: $buttonText")
                    allowButton.click()
                    Thread.sleep(500)
                    return true
                }

                // Also try by resource ID
                val allowButtonById = device.findObject(
                    UiSelector()
                        .resourceIdMatches(".*permission_allow.*")
                        .clickable(true)
                )

                if (allowButtonById.exists()) {
                    println("Permission dialog found (by ID)")
                    allowButtonById.click()
                    Thread.sleep(500)
                    return true
                }
            } catch (e: Exception) {
                println("Error clicking permission button: ${e.message}")
            }
        }

        return false
    }

    /**
     * Helper function to find a UI element using polling
     * @param selector BySelector to search for
     * @param timeoutMs Maximum time to wait in milliseconds
     * @param pollingIntervalMs Interval between checks in milliseconds
     * @return true if element was found within timeout, false otherwise
     */
    protected fun waitForElementWithPolling(
        selector: androidx.test.uiautomator.BySelector,
        timeoutMs: Long = 5000L,
        pollingIntervalMs: Long = 500L
    ): Boolean {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + timeoutMs

        while (System.currentTimeMillis() < endTime) {
            if (device.hasObject(selector)) {
                return true
            }
            Thread.sleep(pollingIntervalMs)
        }

        return false
    }

    /**
     * Poll to check if any of multiple UI elements exist
     * @param selectors List of BySelectors to search for
     * @param timeoutMs Maximum time to wait in milliseconds
     * @param pollingIntervalMs Interval between checks in milliseconds
     * @return true if any element was found within timeout, false otherwise
     */
    protected fun waitForAnyElementWithPolling(
        selectors: List<androidx.test.uiautomator.BySelector>,
        timeoutMs: Long = 5000L,
        pollingIntervalMs: Long = 500L
    ): Boolean {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + timeoutMs

        while (System.currentTimeMillis() < endTime) {
            for (selector in selectors) {
                if (device.hasObject(selector)) {
                    return true
                }
            }
            Thread.sleep(pollingIntervalMs)
        }

        return false
    }

    /**
     * Save a screenshot
     */
    protected fun takeScreenshot(name: String) {
        try {
            val screenshotDir = File("/sdcard/Pictures/unipad_tests")
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs()
            }

            val screenshotFile = File(screenshotDir, "${name}_API${Build.VERSION.SDK_INT}.png")
            device.takeScreenshot(screenshotFile)
            println("Screenshot saved: ${screenshotFile.absolutePath}")
        } catch (e: Exception) {
            println("Failed to save screenshot: ${e.message}")
        }
    }
}
