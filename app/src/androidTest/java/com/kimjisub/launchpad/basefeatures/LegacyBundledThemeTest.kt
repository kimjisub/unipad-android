package com.kimjisub.launchpad.basefeatures

import android.widget.ImageView
import androidx.core.graphics.drawable.toBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.ThemeActivity
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.loadTheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LegacyBundledThemeTest : PlaybackScreenTest() {
    @Test fun legacySelectionStillDisplaysAndPlaysAfterExtractedFilesAreGone() {
        val prefs = PreferenceManager(screen.context)
        val original = prefs.selectedTheme
        assertFalse("This check needs no extracted bundled skin",
            File(screen.context.getExternalFilesDir(null), "themes/sskin").exists())
        try {
            // Model a screen reading the old selection while background migration is pending.
            prefs.selectedTheme = "zip://sskin"
            val expected = loadTheme(screen.context, "asset://sskin", fullLoad = true)
            screen.launch(ThemeActivity::class.java).use {
                screen.node(By.text(expected.name)).click()
                assertFalse("The saved bundled skin was not shown as selected",
                    screen.device.hasObject(By.text(screen.text(R.string.apply))))
                screen.capture("legacy-bundled-theme-selected")
            }
            screen.openPlay()
            val actual = screen.onMain {
                val pad = screen.views(screen.resumed().window.decorView).filterIsInstance<PadView>().first()
                pad.findViewById<ImageView>(com.kimjisub.design.R.id.background).drawable
            }
            assertTrue("The legacy selection did not reach rendered pad pixels",
                expected.btn!!.toBitmap().sameAs(actual.toBitmap()))
            // The worker may have persisted the equivalent asset ID while the screen was opening.
            assertTrue("Playback replaced the user's skin with the default",
                prefs.selectedTheme in setOf("zip://sskin", "asset://sskin"))
            screen.capture("legacy-bundled-theme-play")
        } finally {
            prefs.selectedTheme = original
        }
    }
}
