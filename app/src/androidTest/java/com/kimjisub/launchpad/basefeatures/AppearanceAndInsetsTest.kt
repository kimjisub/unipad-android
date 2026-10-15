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
import com.kimjisub.launchpad.activity.MainActivity
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
            var settings = screen.launch(SettingsActivity::class.java)
            screen.node(By.text(screen.text(R.string.reconnect_launchpad)))
            localeManager.applicationLocales = LocaleList(Locale.KOREAN)
            screen.awaitActivity(SettingsActivity::class.java, "ko")
            assertNotNull("Korean language row missing", screen.scrollTo(By.text("한국어"), androidx.test.uiautomator.Direction.DOWN))
            assertEquals("ko", screen.onMain { screen.resumed().resources.configuration.locales[0].language })
            screen.capture("settings-korean")
            localeManager.applicationLocales = LocaleList(Locale.ENGLISH)
            screen.awaitActivity(SettingsActivity::class.java, "en")
            // Locale handling may retain scroll state. Reopen without saved state to exercise
            // the language row below the top of a newly created Settings screen deterministically.
            val previousSettings = screen.resumed()
            settings.close()
            settings = screen.launch(SettingsActivity::class.java)
            val reopenedSettings = screen.resumed()
            assertNotSame("Settings did not create a new screen", previousSettings, reopenedSettings)
            assertTrue("Previous Settings was not destroyed", screen.onMain { previousSettings.isDestroyed })
            val topRow = screen.node(By.text(screen.text(R.string.reconnect_launchpad)))
            assertNotNull("Settings scroll container missing", screen.device.findObject(By.scrollable(true)))
            assertFalse("Language row must start below the fresh viewport", screen.device.hasObject(By.text("English")))
            screen.capture("settings-english-fresh-top")
            android.util.Log.i("LanguageRowRegression", "fresh_settings=true locale=en top_row=${topRow.text} language_visible=false")
            assertNotNull("English language row missing", screen.scrollTo(By.text("English"), androidx.test.uiautomator.Direction.DOWN))
            android.util.Log.i("LanguageRowRegression", "language_row_found=true direction=DOWN")
            screen.capture("settings-english-language-row")
            assertEquals("en", screen.onMain { screen.resumed().resources.configuration.locales[0].language })
            screen.clickText(R.string.settings_theme)
            screen.node(By.desc(screen.text(R.string.theme_add_title)))
            val choices = ThemeTool.getThemePackList(screen.context)
            val selected = choices.first { it.id != theme }
            screen.node(By.text(selected.name)).click()
            screen.clickText(R.string.apply)
            screen.await("Selected skin was not persisted after Apply") { prefs.selectedTheme == selected.id }
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

    @Test fun mainScreenButtonsStayClearOfSideNavigationBarAndCutout() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        // A landscape phone with 3-button navigation puts the bar on a side, and a punch-hole camera
        // takes the opposite side; both are emulated through the system's own overlays.
        val previous = enabledOverlays()
        try {
            for (overlay in listOf(THREE_BUTTON_NAVIGATION, HOLE_CUTOUT)) {
                val result = screen.device.executeShellCommand("cmd overlay enable-exclusive --category $overlay")
                assertTrue("Could not enable $overlay: $result", result.isBlank())
            }
            screen.launch(MainActivity::class.java)
            screen.node(By.text(FeatureScreen.TITLE))
            for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)) {
                screen.onMain { screen.resumed().requestedOrientation = orientation }
                val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) android.view.Surface.ROTATION_90 else android.view.Surface.ROTATION_270
                screen.await("Side navigation bar and cutout did not reach the rotated window") {
                    screen.onMain {
                        val activity = screen.resumed()
                        val insets = ViewCompat.getRootWindowInsets(activity.window.decorView) ?: return@onMain false
                        val bar = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                        val cutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
                        activity.display!!.rotation == expected && bar.left + bar.right > 0 && cutout.left + cutout.right > 0
                    }
                }
                screen.instrumentation.waitForIdleSync()
                val safe = screen.onMain {
                    val decor = screen.resumed().window.decorView
                    val insets = ViewCompat.getRootWindowInsets(decor)!!
                        .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                    screen.bounds(decor).apply { left += insets.left; top += insets.top; right -= insets.right; bottom -= insets.bottom }
                }
                screen.node(By.desc(screen.text(R.string.store)))
                screen.node(By.desc(screen.text(R.string.import_unipack)))
                screen.node(By.res("main_pack_${FeatureScreen.PACK_ID}"))
                val controls = screen.device.findObjects(By.pkg(screen.context.packageName).clickable(true))
                assertTrue("No main screen controls found", controls.isNotEmpty())
                screen.capture("main-side-bar-$orientation")
                controls.forEach {
                    val bounds = it.visibleBounds
                    assertTrue("Control ${it.contentDescription ?: it.resourceName ?: it.text} $bounds outside $safe", safe.contains(bounds))
                }
            }
        } finally {
            restoreOverlays(previous)
        }
    }

    private fun enabledOverlays(): Map<String, String?> {
        val enabled = screen.device.executeShellCommand("cmd overlay list").lines()
            .filter { it.trim().startsWith("[x]") }.map { it.substringAfter("]").trim() }
        return listOf(NAVIGATION_CATEGORY, CUTOUT_CATEGORY).associateWith { category -> enabled.firstOrNull { it.startsWith(category) } }
    }

    private fun restoreOverlays(previous: Map<String, String?>) {
        previous.forEach { (category, overlay) ->
            if (overlay != null) screen.device.executeShellCommand("cmd overlay enable-exclusive --category $overlay")
            else enabledOverlays()[category]?.let { screen.device.executeShellCommand("cmd overlay disable $it") }
        }
    }

    private companion object {
        const val NAVIGATION_CATEGORY = "com.android.internal.systemui.navbar."
        const val CUTOUT_CATEGORY = "com.android.internal.display.cutout.emulation."
        const val THREE_BUTTON_NAVIGATION = NAVIGATION_CATEGORY + "threebutton"
        const val HOLE_CUTOUT = CUTOUT_CATEGORY + "hole"
    }
}
