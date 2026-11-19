package com.kimjisub.launchpad.basefeatures

import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import com.kimjisub.launchpad.ActivityReadiness
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityClickTest {
    @Test fun staleBeforeAcceptanceReacquiresAndStopsAfterOneAcceptedClick() {
        var time = 0L
        var queries = 0
        var accepted = 0
        AccessibilityClick(now = { time }, pause = { time += it }).perform("DocumentsUI roots click") {
            queries++
            if (queries == 1) throw StaleObjectException()
            accepted++
            true
        }
        assertEquals(2, queries)
        assertEquals(1, accepted)
        assertEquals(25L, time)
    }

    @Test fun acceptedClickIsNeverSentAgain() {
        var calls = 0
        AccessibilityClick().perform("accepted click") { calls++; true }
        assertEquals(1, calls)
    }

    @Test fun rejectedClickFailsWithoutResending() {
        var calls = 0
        val failure = assertThrows(AssertionError::class.java) {
            AccessibilityClick().perform("rejected click") { calls++; false }
        }
        assertEquals(1, calls)
        assertTrue(failure.message!!.contains("was not accepted"))
    }

    @Test fun staleTimeoutKeepsOriginalExceptionAndTheSameDeadline() {
        var time = 0L
        var calls = 0
        val original = StaleObjectException()
        val failure = assertThrows(AssertionError::class.java) {
            AccessibilityClick(now = { time }, pause = { time += it }).perform("stale click") {
                calls++
                time = 9990
                throw original
            }
        }
        assertEquals(1, calls)
        assertEquals(10000L, time)
        assertSame(original, failure.cause)
        assertTrue(failure.message!!.contains("timed out after 10000 ms"))
    }

    @Test fun missingNodeDoesNotStartAnotherTenSecondWait() {
        var time = 0L
        var calls = 0
        assertThrows(AssertionError::class.java) {
            AccessibilityClick(now = { time }, pause = { time += it }).perform("missing click") {
                calls++
                time = 9990
                null
            }
        }
        assertEquals(1, calls)
        assertEquals(10000L, time)
    }

    @Test fun unexpectedFailureIsNotRetriedOrReplaced() {
        val original = IllegalStateException("action transport failed")
        var calls = 0
        val failure = assertThrows(IllegalStateException::class.java) {
            AccessibilityClick().perform("broken click") { calls++; throw original }
        }
        assertSame(original, failure)
        assertEquals(1, calls)
    }

    @Test fun rotatingAndReplacingPickerReacquiresBeforeOneAcceptedClick() {
        val screen = FeatureScreen()
        try {
            screen.launch(MainActivity::class.java)
            screen.clickText(R.string.guide_import_external)
            val picker = "com.google.android.documentsui"
            val selector = By.pkg(picker).clazz("android.widget.ImageButton")
                .hasAncestor(By.res(picker, "toolbar"))
            val oldButton = screen.node(selector)
            screen.device.setOrientationLeft()
            // Replace the picker while rotated so the first lookup has the original, invalid window.
            screen.device.pressBack()
            screen.awaitActivity(MainActivity::class.java)
            screen.clickText(R.string.guide_import_external)
            screen.node(selector)
            var queries = 0
            var accepted = 0
            AccessibilityClick().perform("DocumentsUI roots click") {
                val button = if (queries++ == 0) oldButton else screen.node(selector)
                button.accessibilityNodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK).also {
                    if (it) accepted++
                }
            }
            assertEquals("The replaced node must be reacquired", 2, queries)
            assertEquals("Only one click may be accepted", 1, accepted)
            screen.node(By.res(picker, "roots_list"))
            screen.capture("rotated-picker-reacquired")
            ActivityReadiness.captureState(screen.context, screen.device, "basefeatures-captures", "rotated-picker-reacquired")
        } finally {
            screen.device.setOrientationNatural()
            screen.device.unfreezeRotation()
            screen.close()
        }
    }

    @Test fun missingAccessibilityTargetRetainsFailureAndCapturesItsOwnState() {
        val screen = FeatureScreen()
        try {
            screen.launch(MainActivity::class.java)
            val directory = "/data/local/tmp/unipad_tests/basefeatures-captures"
            fun captures() = screen.device.executeShellCommand("ls $directory").lineSequence()
                .filter { it.startsWith("accessibility-click-failed-") }.toSet()
            val before = captures()
            val failure = assertThrows(AssertionError::class.java) {
                screen.accessibilityClick(By.desc("missing-accessibility-target"), "missing target click")
            }
            assertTrue(failure.message!!, failure.message!!.contains("timed out after 10000 ms"))
            val saved = captures() - before
            val image = saved.single { it.endsWith(".png") }
            val name = image.removeSuffix(".png")
            assertTrue("Missing failure hierarchy: $saved", "$name.xml" in saved)
            assertTrue("Missing failure activity state: $saved", "$name-state.txt" in saved)
        } finally { screen.close() }
    }

    @Test fun keyboardTimeoutReportsSelectedInputMethodAndMissingWindow() {
        val screen = FeatureScreen()
        try {
            screen.launch(MainActivity::class.java)
            assertEquals(screen.device.displayHeight, screen.keyboardTopOnScreen())
            val selected = Settings.Secure.getString(screen.context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            val failure = assertThrows(AssertionError::class.java) { screen.awaitKeyboardTopOnScreen() }
            assertTrue(failure.message!!, failure.message!!.contains("defaultInputMethod=$selected"))
            assertTrue(failure.message!!, failure.message!!.contains("imeWindowPresent=false"))
            assertTrue(failure.message!!, failure.message!!.contains("imeWindowVisible=false"))
            println("Expected keyboard diagnostic failure: ${failure.message}")
        } finally { screen.close() }
    }
}
