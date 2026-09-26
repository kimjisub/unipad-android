package com.kimjisub.launchpad

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Diagnostic Tests
 * Tests for UI hierarchy diagnostics and debugging
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticTest : BaseUITest() {

    /**
     * Prints what UI Automator sees on the main screen, to investigate lookups that fail.
     * The main screen is Compose, so elements are listed by text and content description
     * (there are no view ids).
     */
    @Test
    fun testDiagnoseUIHierarchy() {
        launchToMainScreen()

        println("=== UI hierarchy diagnostic start ===")

        val markers = mapOf(
            "store button" to By.desc(str(R.string.store)),
            "import button" to By.desc(str(R.string.import_unipack)),
            "settings button" to By.desc(str(R.string.setting)),
            "test pack row" to By.text(TestUniPack.TITLE),
        )
        markers.forEach { (name, selector) -> println("$name found: ${device.hasObject(selector)}") }

        val clickableElements = device.findObjects(By.pkg(PACKAGE_NAME).clickable(true))
        println("Number of clickable elements: ${clickableElements.size}")
        clickableElements.forEachIndexed { index, element ->
            println("  [$index] class: ${element.className}, desc: ${element.contentDescription}, bounds: ${element.visibleBounds}")
        }
        println("Labels on screen: ${visibleLabels()}")

        val dump = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString()
        println("UI dump: ${dump.length} chars")
        val labeled = Regex("""(text|content-desc)="[^"]""")
        dump.lines().filter { it.contains(PACKAGE_NAME) && labeled.containsMatchIn(it) }
            .forEach { println("  ${it.trim()}") }

        takeScreenshot("ui_hierarchy_diagnosis")
        assertTrue(
            "Main screen markers missing: ${markers.filterValues { !device.hasObject(it) }.keys}",
            markers.values.all { device.hasObject(it) }
        )
        println("=== UI hierarchy diagnostic complete ===")
    }
}
