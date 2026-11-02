package com.kimjisub.launchpad

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayLayoutChecksTest {
    @Test
    fun orientationOverrideRequiresSuccessOrTheExplicitUnsupportedResponse() {
        assertTrue(orientationOverrideSupported(""))
        assertTrue(orientationOverrideSupported("\n"))
        assertFalse(orientationOverrideSupported("Unknown command: set-ignore-orientation-request\n"))
        assertThrows(IllegalStateException::class.java) { orientationOverrideSupported("Permission denied") }
        assertThrows(IllegalStateException::class.java) { orientationOverrideSupported("Error: invalid argument") }
    }

    @Test
    fun contentCentreAcceptsOnlySystemInsetsAndRejectsDisplacedPads() {
        val window = PlayLayoutRect(100, 50, 2560, 1130)
        val safe = PlayLayoutRect(100, 50, 2434, 1130)
        val centred = PlayLayoutRect(751, 74, 1783, 1106)
        fun problem(content: PlayLayoutRect, pads: PlayLayoutRect = centred) =
            padCentreProblem(window, safe, content, pads, density = 2.625f, toleranceDp = 1f)

        assertNull("System navigation bar space is outside app content", problem(safe))
        assertNull("Edge-to-edge content uses the window centre",
            problem(window, PlayLayoutRect(814, 74, 1846, 1106)))
        assertNull("Exactly 1dp remains within tolerance",
            padCentreProblem(window, safe, safe, centred.copy(left = 753, right = 1785), 2f, 1f))
        assertTrue("A 2dp pad displacement must name both references",
            problem(safe, centred.copy(left = 757, right = 1789)).orEmpty().let {
                it.contains("content") && it.contains("window") && it.contains("dp")
            })
        assertNotNull("An arbitrary extra inset must fail even with centred pads",
            problem(safe.copy(left = 120, right = 2414)))
        assertNotNull("Part of a system inset must not hide arbitrary content padding",
            problem(safe.copy(right = 2497)))
        assertNotNull("Translated content must fail", problem(safe.copy(left = 110, right = 2444)))
        assertNotNull("Content outside the window must fail", problem(safe.copy(left = 90)))
        assertNotNull("Empty content must fail", problem(safe.copy(right = 100)))

        val cutoutSafe = PlayLayoutRect(124, 80, 2434, 1080)
        val cutoutPads = PlayLayoutRect(763, 100, 1795, 1060)
        assertNull("System cutout and bar insets may occur on all four edges",
            padCentreProblem(window, cutoutSafe, cutoutSafe, cutoutPads, 2.625f, 1f))
        for (content in listOf(
            cutoutSafe.copy(left = 125), cutoutSafe.copy(top = 81),
            cutoutSafe.copy(right = 2433), cutoutSafe.copy(bottom = 1079),
        )) {
            assertNotNull("An app inset at any edge must fail: $content",
                padCentreProblem(window, cutoutSafe, content, cutoutPads, 2.625f, 1f))
        }
    }
}
