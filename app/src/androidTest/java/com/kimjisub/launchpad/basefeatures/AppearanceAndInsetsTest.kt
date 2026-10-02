package com.kimjisub.launchpad.basefeatures

import android.content.pm.ActivityInfo
import androidx.core.graphics.drawable.toBitmap
import android.os.LocaleList
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.kimjisub.design.view.ChainView
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.SettingsActivity
import com.kimjisub.launchpad.activity.ThemeActivity
import com.kimjisub.launchpad.adapter.ThemeTool
import com.kimjisub.launchpad.manager.PreferenceManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class AppearanceAndInsetsTest : PlaybackScreenTest() {
    @Test fun languageAndSkinSelectionPersistAndPlayLoadsSelectedSkin() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        val prefs = PreferenceManager(screen.context)
        val theme = prefs.selectedTheme
        val localeManager = screen.context.getSystemService(android.app.LocaleManager::class.java)
        val oldLocales = localeManager.applicationLocales
        try {
            val settings = screen.launch(SettingsActivity::class.java)
            localeManager.applicationLocales = LocaleList(Locale.KOREAN)
            assertNotNull("Korean language row missing", screen.scrollTo(By.text("한국어"), androidx.test.uiautomator.Direction.DOWN))
            assertEquals("ko", screen.onMain { screen.resumed().resources.configuration.locales[0].language })
            screen.capture("settings-korean")
            localeManager.applicationLocales = LocaleList(Locale.ENGLISH)
            assertNotNull("English language row missing", screen.scrollTo(By.text("English"), androidx.test.uiautomator.Direction.UP))
            screen.clickText(R.string.settings_theme)
            screen.node(By.desc(screen.text(R.string.theme_add_title)))
            val choices = ThemeTool.getThemePackList(screen.context)
            val selected = choices.first { it.id != theme }
            screen.node(By.text(selected.name)).click()
            screen.clickText(R.string.apply)
            assertEquals(selected.id, prefs.selectedTheme)
            screen.device.pressBack()
            screen.clickText(R.string.settings_theme)
            screen.node(By.text(selected.name)).click()
            assertFalse("Applied skin offered Apply again", screen.device.hasObject(By.text(screen.text(R.string.apply))))
            screen.capture("selected-skin")
            screen.device.pressBack()
            settings.close()
            screen.openPlay()
            val expected = com.kimjisub.launchpad.manager.loadTheme(screen.context, selected.id, fullLoad = true)
            assertNotNull(expected.btn)
            // Verify the selected skin reached the rendered pad background, not just its preference.
            val actual = screen.onMain {
                val pad = screen.views(screen.resumed().window.decorView).filterIsInstance<PadView>().first()
                pad.findViewById<android.widget.ImageView>(com.kimjisub.design.R.id.background).drawable
            }
            assertNotNull(actual)
            assertTrue("Selected skin did not reach rendered pad pixels", expected.btn!!.toBitmap().sameAs(actual.toBitmap()))
            screen.capture("play-selected-skin")
        } finally {
            prefs.selectedTheme = theme
            localeManager.applicationLocales = oldLocales
        }
    }

    @Test fun rotationKeepsPackAndChainAndPadsStayClearOfBarsAndCutout() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        screen.openPlay()
        val vm = screen.vm()
        screen.onMain { vm.chain.value = 1 }
        val activity = screen.resumed()
        val oldOrientation = screen.onMain { activity.requestedOrientation }
        try {
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)) {
                screen.onMain { activity.requestedOrientation = orientation }
                screen.await("Requested rotation did not reach window") {
                    val rotation = screen.onMain { activity.display!!.rotation }
                    rotation == if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) android.view.Surface.ROTATION_90 else android.view.Surface.ROTATION_270
                }
                screen.instrumentation.waitForIdleSync()
                assertSame(vm, screen.vm())
                assertEquals(FeatureScreen.TITLE, screen.onMain { vm.unipack.title })
                assertEquals(1, screen.onMain { vm.chain.value })
                screen.onMain {
                    val decor = activity.window.decorView
                    val safe = screen.bounds(decor)
                    val insets = ViewCompat.getRootWindowInsets(decor)!!
                        .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                    safe.left += insets.left; safe.top += insets.top; safe.right -= insets.right; safe.bottom -= insets.bottom
                    val controls = screen.views(decor).filter { it is PadView || it is ChainView }.filter { it.width > 0 && it.height > 0 }
                    assertEquals("Incomplete pad grid", 64, controls.count { it is PadView })
                    controls.forEach { assertTrue("Control ${screen.bounds(it)} outside $safe", safe.contains(screen.bounds(it))) }
                }
                screen.node(By.desc(screen.text(R.string.menu)))
                screen.capture("rotation-$orientation")
            }
            val before = audio.plays.size
            screen.tapPad(0)
            screen.await("Rotated pad did not play") { audio.plays.size == before + 1 }
        } finally {
            screen.onMain { activity.requestedOrientation = oldOrientation }
        }
    }
}
