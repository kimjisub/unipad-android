package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Diagnostic Tests
 * Tests for UI hierarchy diagnostics and debugging
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticTest : BaseUITest() {

    /**
     * Diagnostic test that prints the UI hierarchy
     * Used to investigate why resource ID based searches fail
     */
    @Test
    fun testDiagnoseUIHierarchy() {
        // Launch the app and navigate to the main screen
        launchApp()
        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME)), LAUNCH_TIMEOUT)
        handlePermissionDialogs()

        // Wait for main screen to load
        Thread.sleep(10000)

        println("=== UI hierarchy diagnostic start ===")

        // 1. Verify the app is running by package name
        val appRunning = device.hasObject(By.pkg(PACKAGE_NAME))
        println("App is running: $appRunning")

        // 2. Attempt to search by resource ID
        val hasFloatingMenu = device.hasObject(By.res(PACKAGE_NAME, "floatingMenu"))
        val hasFragmentList = device.hasObject(By.res(PACKAGE_NAME, "fragment_list"))
        val hasFragmentPanel = device.hasObject(By.res(PACKAGE_NAME, "fragment_panel"))

        println("floatingMenu found: $hasFloatingMenu")
        println("fragment_list found: $hasFragmentList")
        println("fragment_panel found: $hasFragmentPanel")

        // 3. Find all clickable elements
        val clickableElements = device.findObjects(By.clickable(true))
        println("Number of clickable elements: ${clickableElements.size}")
        clickableElements.forEachIndexed { index, element ->
            println("  [$index] class: ${element.className}, text: ${element.text}, desc: ${element.contentDescription}, ID: ${element.resourceName}")
        }

        // 4. Search by FloatingActionMenu class
        val fabElements = device.findObjects(By.clazz("com.github.clans.fab.FloatingActionMenu"))
        println("FloatingActionMenu class element count: ${fabElements.size}")
        fabElements.forEachIndexed { index, element ->
            println("  [$index] ID: ${element.resourceName}, clickable: ${element.isClickable}")
        }

        // 5. Search by FrameLayout class
        val frameLayouts = device.findObjects(By.clazz("android.widget.FrameLayout"))
        println("FrameLayout element count: ${frameLayouts.size}")
        frameLayouts.take(10).forEachIndexed { index, element ->
            println("  [$index] ID: ${element.resourceName}, visible: ${element.visibleBounds}")
        }

        // 6. Full UI tree dump
        println("\n=== Full UI tree dump ===")
        try {
            val dumpFile = File("/sdcard/ui_dump.xml")
            device.dumpWindowHierarchy(dumpFile)
            val uiDump = dumpFile.readText()
            println("UI dump file size: ${uiDump.length} chars")
            // Print only lines related to floatingMenu and fragment_list
            val relevantLines = uiDump.lines().filter {
                it.contains("floatingMenu") || it.contains("fragment_list") || it.contains("fragment_panel")
            }
            println("Related UI elements:")
            relevantLines.forEach { println("  $it") }
        } catch (e: Exception) {
            println("UI dump failed: ${e.message}")
        }

        // 7. Re-inspect after FAB click
        println("\n=== Re-inspection after FAB click ===")
        val displayWidth = device.displayWidth
        val displayHeight = device.displayHeight
        val fabX = displayWidth - 80
        val fabY = displayHeight - 80
        device.click(fabX, fabY)
        Thread.sleep(3000)

        val hasFloatingMenuAfter = device.hasObject(By.res(PACKAGE_NAME, "floatingMenu"))
        val hasFragmentListAfter = device.hasObject(By.res(PACKAGE_NAME, "fragment_list"))
        println("floatingMenu found after FAB click: $hasFloatingMenuAfter")
        println("fragment_list found after FAB click: $hasFragmentListAfter")

        takeScreenshot("ui_hierarchy_diagnosis")
        println("=== UI hierarchy diagnostic complete ===")
    }
}
