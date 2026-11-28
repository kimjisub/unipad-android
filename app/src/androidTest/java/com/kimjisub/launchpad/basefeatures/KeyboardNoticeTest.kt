package com.kimjisub.launchpad.basefeatures

import android.graphics.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Which keyboard notice [FeatureScreen.awaitKeyboardTopOnScreen] closes. The windows are built from
 * Gboard's window on an Android 17 landscape phone with font scale 2.0, where the notice sat at
 * y 166–394 above the suggestion row at 394–502.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardNoticeTest {
    @Test fun keyboardWithoutNoticeIsLeftAlone() {
        assertEquals(KeyboardNotice.None, KeyboardNotice.of(keyboard()))
    }

    @Test fun fontSizeNoticeIsClosedByItsOwnOkButton() {
        val notice = KeyboardNotice.of(keyboard(fontSizeNotice()))

        assertEquals(KeyboardNotice.FontSize(fontSizeOk), notice)
    }

    @Test fun otherNoticeIsLeftOpenEvenWithTheSameCloseButton() {
        val notice = KeyboardNotice.of(keyboard(otherNotice(top = 166, bottom = 394)))

        assertTrue("$notice", notice is KeyboardNotice.Other)
        assertTrue("$notice", "Try glide typing" in (notice as KeyboardNotice.Other).labels)
    }

    @Test fun fontSizeNoticeBesideAnotherNoticeClosesOnlyItsOwn() {
        val other = otherNotice(top = 0, bottom = 166)
        val notice = KeyboardNotice.of(keyboard(other, fontSizeNotice()))

        assertEquals(KeyboardNotice.FontSize(fontSizeOk), notice)
    }

    @Test fun fontSizeNoticeWithoutItsOwnCloseButtonFailsRatherThanClosingAnother() {
        val notice = KeyboardNotice.of(keyboard(otherNotice(top = 0, bottom = 166), fontSizeNotice(withClose = false)))

        assertTrue("$notice", notice is KeyboardNotice.FontSizeUnclosable)
    }

    @Test fun keyboardWithoutGboardSuggestionRowIsLeftAlone() {
        val window = KeyboardElement(Rect(136, 166, 2400, 1080), children = listOf(fontSizeNotice()))

        assertEquals(KeyboardNotice.None, KeyboardNotice.of(window))
    }

    private val fontSizeOk = KeyboardElement(
        Rect(2056, 191, 2258, 370), text = "OK", description = KeyboardNotice.CLOSE, clickable = true,
    )

    private fun fontSizeNotice(withClose: Boolean = true) = KeyboardElement(
        Rect(136, 166, 2400, 394),
        description = "Keyboard font size follows system setting.",
        children = listOfNotNull(
            KeyboardElement(Rect(326, 218, 1271, 342), text = KeyboardNotice.FONT_SIZE_MESSAGE),
            fontSizeOk.takeIf { withClose },
            KeyboardElement(Rect(2258, 196, 2384, 365), description = "Open the settings.", clickable = true),
        ),
    )

    private fun otherNotice(top: Int, bottom: Int) = KeyboardElement(
        Rect(136, top, 2400, bottom),
        children = listOf(
            KeyboardElement(Rect(326, top + 30, 1271, bottom - 30), text = "Try glide typing"),
            KeyboardElement(
                Rect(2056, top + 10, 2258, bottom - 10), text = "OK", description = KeyboardNotice.CLOSE, clickable = true,
            ),
        ),
    )

    /** Gboard's window with [notices] stacked above its suggestion row. */
    private fun keyboard(vararg notices: KeyboardElement) = KeyboardElement(
        Rect(136, notices.minOfOrNull { it.bounds.top } ?: 394, 2400, 1080),
        children = notices.toList() + KeyboardElement(
            Rect(136, 394, 2400, 502),
            viewId = KeyboardNotice.GBOARD_HEADER,
            children = listOf(
                KeyboardElement(Rect(136, 394, 278, 502), description = "Open features menu", clickable = true),
                KeyboardElement(Rect(278, 394, 911, 502), description = "Base", clickable = true),
            ),
        ) + KeyboardElement(Rect(136, 502, 2400, 1080), description = "Keys"),
    )
}
