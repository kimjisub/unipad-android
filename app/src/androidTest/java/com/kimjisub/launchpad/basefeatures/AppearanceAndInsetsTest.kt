package com.kimjisub.launchpad.basefeatures

import android.content.pm.ActivityInfo
import androidx.core.graphics.drawable.toBitmap
import android.os.LocaleList
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import com.kimjisub.design.view.ChainView
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.DisplayOverlays
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.activity.MidiSelectActivity
import com.kimjisub.launchpad.activity.SettingsActivity
import com.kimjisub.launchpad.activity.ThemeActivity
import com.kimjisub.launchpad.activity.TransferActivity
import com.kimjisub.launchpad.adapter.ThemeTool
import com.kimjisub.launchpad.manager.PreferenceManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
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
            assertNotNull("Korean language row missing", screen.scrollTo(By.text("한국어"), Direction.DOWN))
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
            assertNotNull("English language row missing", screen.scrollTo(By.text("English"), Direction.DOWN))
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
                    val safe = freeArea(decor)!!
                    val controls = screen.views(decor).filter { it is PadView || it is ChainView }.filter { it.width > 0 && it.height > 0 }
                    assertEquals("Incomplete pad grid", 64, controls.count { it is PadView })
                    controls.forEach { assertTrue("Control ${screen.bounds(it)} outside $safe", safe.contains(screen.bounds(it))) }
                }
                screen.node(By.desc(screen.text(R.string.menu)))
                screen.capture("rotation-$orientation")
            }
            pressPad(0)
        } finally {
            screen.onMain { activity.requestedOrientation = oldOrientation }
        }
    }

    @Test fun mainScreenButtonsStayClearOfSideNavigationBarAndCutout() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        // A landscape phone with 3-button navigation puts the bar on a side, and a punch-hole camera
        // takes the opposite side; both are emulated through the system's own overlays. From API 35 the
        // window spans the display and reports both as insets; below it the window is laid out between them.
        DisplayOverlays.with(screen.device, DisplayOverlays.THREE_BUTTON_NAVIGATION, DisplayOverlays.HOLE_CUTOUT) {
            screen.launch(MainActivity::class.java)
            screen.node(By.text(FeatureScreen.TITLE))
            assertControlsClearOfSideBarAndCutout("main") {
                screen.node(By.desc(screen.text(R.string.store)))
                screen.node(By.desc(screen.text(R.string.import_unipack)))
                screen.node(By.res("main_pack_${FeatureScreen.PACK_ID}"))
            }
        }
    }

    @Test fun settingsScreenStaysClearOfSideNavigationBarAndCutout() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        DisplayOverlays.with(screen.device, DisplayOverlays.THREE_BUTTON_NAVIGATION, DisplayOverlays.HOLE_CUTOUT) {
            screen.launch(SettingsActivity::class.java)
            assertControlsClearOfSideBarAndCutout("settings-info") {
                screen.node(By.text(screen.text(R.string.reconnect_launchpad)))
            }
            screen.clickText(R.string.settings_storage)
            assertControlsClearOfSideBarAndCutout("settings-storage") {
                screen.node(By.text(screen.text(R.string.transfer_button)))
            }
        }
    }

    @Test fun themeScreenStaysClearOfSideNavigationBarAndCutout() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        DisplayOverlays.with(screen.device, DisplayOverlays.THREE_BUTTON_NAVIGATION, DisplayOverlays.HOLE_CUTOUT) {
            screen.launch(ThemeActivity::class.java)
            assertControlsClearOfSideBarAndCutout("theme") {
                screen.node(By.desc(screen.text(R.string.theme_add_title)))
            }
        }
    }

    @Test fun transferScreenStaysClearOfSideNavigationBarAndCutout() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        DisplayOverlays.with(screen.device, DisplayOverlays.THREE_BUTTON_NAVIGATION, DisplayOverlays.HOLE_CUTOUT) {
            launchTransfer(screen.workspace)
            assertControlsClearOfSideBarAndCutout("transfer") {
                screen.node(By.text(FeatureScreen.PACK_ID))
                screen.node(By.text(screen.text(R.string.transfer_start)))
            }
        }
    }

    @Test fun midiSelectScreenStaysClearOfSideNavigationBarAndCutout() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        DisplayOverlays.with(screen.device, DisplayOverlays.THREE_BUTTON_NAVIGATION, DisplayOverlays.HOLE_CUTOUT) {
            screen.launch(MidiSelectActivity::class.java)
            assertControlsClearOfSideBarAndCutout("midi-select") {
                screen.node(By.text(screen.text(R.string.midi_help_title)))
                screen.node(By.text(screen.text(R.string.midi_lp_mk2)))
            }
        }
    }

    @Test fun settingsScreenEndsAboveTabletNavigationBar() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        onTabletWithButtonBar {
            screen.launch(SettingsActivity::class.java)
            assertControlsAboveBottomBar("settings-info") {
                screen.node(By.text(screen.text(R.string.reconnect_launchpad)))
            }
            screen.clickText(R.string.settings_storage)
            assertControlsAboveBottomBar("settings-storage") {
                screen.node(By.text(screen.text(R.string.transfer_button)))
            }
        }
    }

    @Test fun themeScreenEndsAboveTabletNavigationBar() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        onTabletWithButtonBar {
            screen.launch(ThemeActivity::class.java)
            assertControlsAboveBottomBar("theme") {
                screen.node(By.desc(screen.text(R.string.theme_add_title)))
            }
        }
    }

    @Test fun transferScreenEndsAboveTabletNavigationBar() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        withTransferSource { source ->
            onTabletWithButtonBar {
                launchTransfer(source)
                assertControlsAboveBottomBar("transfer") {
                    screen.node(By.text(transferFolderName(0)))
                    screen.node(By.text(screen.text(R.string.transfer_start)))
                }
            }
        }
    }

    @Test fun midiSelectScreenEndsAboveTabletNavigationBar() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        onTabletWithButtonBar {
            screen.launch(MidiSelectActivity::class.java)
            assertControlsAboveBottomBar("midi-select") {
                screen.node(By.text(screen.text(R.string.midi_help_title)))
            }
        }
    }

    @Test fun emptyLibraryGuideStaysClearOfGestureNavigation() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        // At the largest font size the guide no longer fits a landscape phone and scrolls; its end must
        // then stop above the gesture handle.
        val fontScale = screen.device.executeShellCommand("settings get system font_scale").trim()
        try {
            screen.device.executeShellCommand("settings put system font_scale $LARGEST_FONT_SCALE")
            DisplayOverlays.with(screen.device, DisplayOverlays.GESTURAL_NAVIGATION) {
                screen.cleanPacks()
                screen.launch(MainActivity::class.java)
                val guide = By.text(screen.text(R.string.guide_empty_title))
                screen.node(guide)
                screen.await("Gesture navigation did not reach the window") {
                    screen.onMain {
                        val activity = screen.resumed()
                        val safe = freeArea(activity.window.decorView)
                        safe != null && safe.bottom < activity.windowManager.maximumWindowMetrics.bounds.bottom
                    }
                }
                assertEquals(LARGEST_FONT_SCALE.toFloat(), screen.onMain { screen.resumed().resources.configuration.fontScale })
                scrollToEnd(guide)
                screen.node(By.clickable(true).hasDescendant(By.text(screen.text(R.string.guide_import_external))))
                val controls = screen.freshControls()
                val safe = screen.onMain { freeArea(screen.resumed().window.decorView)!! }
                screen.capture("empty-library-gesture-navigation")
                controls.forEach {
                    val bounds = it.visibleBounds
                    assertTrue("Empty library control ${it.contentDescription ?: it.text} $bounds outside $safe", safe.contains(bounds))
                }
            }
        } finally {
            if (fontScale == "null") screen.device.executeShellCommand("settings delete system font_scale")
            else screen.device.executeShellCommand("settings put system font_scale $fontScale")
        }
    }

    @Test fun mainScreenSearchKeyboardCoversLeftPanelWithoutSqueezingIt() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        screen.launch(MainActivity::class.java)
        screen.node(By.text(FeatureScreen.TITLE))
        val panel = By.res("main_left_panel")
        val before = screen.node(panel).visibleBounds
        openSearchKeyboard()
        screen.capture("main-search-keyboard")
        assertEquals("Left panel changed size under the search keyboard", before, screen.node(panel).visibleBounds)
    }

    @Test fun mainScreenSearchResultsScrollAboveKeyboard() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        screen.launch(MainActivity::class.java)
        screen.node(By.text(FeatureScreen.TITLE))
        val keyboardTop = openSearchKeyboard()
        // The list ends with the guiding actions after the last result.
        assertScrollsAboveKeyboard("main-search-results", By.clickable(true).hasDescendant(By.text(screen.text(R.string.guide_import_external))), keyboardTop)
    }

    @Test fun mainScreenNoSearchResultsScrollAboveKeyboard() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        screen.launch(MainActivity::class.java)
        screen.node(By.text(FeatureScreen.TITLE))
        val keyboardTop = openSearchKeyboard()
        screen.node(By.focused(true)).text = NO_MATCH
        // The "no results" message itself may start under the keyboard, out of the node tree.
        screen.await("Search did not filter the pack out") { !screen.device.hasObject(By.text(FeatureScreen.TITLE)) }
        assertScrollsAboveKeyboard("main-no-search-results", By.text(screen.text(R.string.main_search_clear)), keyboardTop)
    }

    @Test fun transferScreenSearchResultsScrollAboveKeyboardOnTablet() {
        if (android.os.Build.VERSION.SDK_INT < 33) throw AssertionError("Base features requires API 33+; no tests are skipped")
        // On a landscape phone the keyboard reaches up to the search field and leaves none of the list
        // in view, so this is checked where some rows stay above it.
        withTransferSource { source ->
            onTabletWithButtonBar {
                launchTransfer(source)
                screen.node(By.text(transferFolderName(0)))
                val keyboardTop = openSearchKeyboard { screen.click(By.clazz("android.widget.EditText")) }
                // Ten of the packs match, still more than the strip above the keyboard shows.
                screen.node(By.focused(true)).text = "${TRANSFER_FOLDER_PREFIX}1"
                screen.await("Search did not filter the packs") { !screen.device.hasObject(By.text(transferFolderName(0))) }
                assertScrollsAboveKeyboard("transfer-search-results", By.text(transferFolderName(19)), keyboardTop)
            }
        }
    }

    /**
     * Opens a search field with [open] (by default the main screen's) and waits for its keyboard to
     * finish opening; returns the keyboard's top on screen.
     */
    private fun openSearchKeyboard(open: () -> Unit = { screen.clickDescription(R.string.transfer_search) }): Int {
        open()
        var keyboard = -1 to 0L
        screen.await("Search keyboard did not finish opening") {
            val height = screen.onMain { keyboardHeight(screen.resumed().window.decorView) }
            val now = android.os.SystemClock.elapsedRealtime()
            if (height != keyboard.first) keyboard = height to now
            height > 0 && now - keyboard.second >= KEYBOARD_SETTLE_MS
        }
        screen.instrumentation.waitForIdleSync()
        return screen.onMain {
            val decor = screen.resumed().window.decorView
            screen.bounds(decor).bottom - keyboardHeight(decor)
        }
    }

    /**
     * Drags the results up, as a finger would, in the strip left between the search field and the
     * keyboard until [target] shows and stops moving, and checks it then sits above the keyboard.
     * Below API 35 the system leaves what the keyboard covers out of the screen's node tree, so
     * [target] may only appear once dragged into view.
     */
    private fun assertScrollsAboveKeyboard(name: String, target: BySelector, keyboardTop: Int) {
        val field = screen.node(By.focused(true)).visibleBounds
        var bounds: android.graphics.Rect? = null
        for (drag in 1..MAX_DRAGS) {
            screen.device.swipe(field.centerX(), keyboardTop - DRAG_MARGIN_PX, field.centerX(), field.bottom + DRAG_MARGIN_PX, DRAG_STEPS)
            screen.instrumentation.waitForIdleSync()
            val moved = screen.device.findObject(target)?.visibleBounds
            if (moved != null && moved == bounds) break
            bounds = moved
        }
        screen.capture("$name-keyboard")
        // Below API 35 the main window is not edge-to-edge, so its content is never told the keyboard's
        // height and the keyboard still covers the list's end there (see the README).
        if (android.os.Build.VERSION.SDK_INT < 35) return
        assertNotNull("$target never came into view above the keyboard from $keyboardTop", bounds)
        assertTrue("$target $bounds stays under the keyboard from $keyboardTop", bounds!!.bottom <= keyboardTop)
    }

    /** Scrolls the list holding [content] to its end; a list that fits its viewport does not scroll. */
    private fun scrollToEnd(content: BySelector) {
        val list = screen.device.findObject(By.pkg(screen.context.packageName).scrollable(true).hasDescendant(content)) ?: return
        scrollToEnd(list)
    }

    private fun scrollToEnd(list: UiObject2) {
        // After switching navigation modes and rotating, the launcher's swipe-up monitor can still
        // consume a drag near the bottom edge. Start in the middle and scroll to the same list end.
        list.setGestureMarginsPercentage(0.1f, 0.1f, 0.1f, 0.5f)
        // Compose may report no scroll event before reaching the end. Check the rendered rows instead.
        var rows = list.findObjects(By.clickable(true)).map { it.visibleBounds }
        for (drag in 1..MAX_DRAGS) {
            list.scroll(Direction.DOWN, 1f)
            screen.instrumentation.waitForIdleSync()
            val moved = list.findObjects(By.clickable(true)).map { it.visibleBounds }
            if (moved == rows) break
            rows = moved
        }
    }

    private fun keyboardHeight(decor: android.view.View) =
        ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0

    /**
     * Turns the resumed screen to each landscape rotation, waits until the side navigation bar and the
     * cutout reach its window, and checks every control [ready] waited for, and every other one on
     * screen, lies outside both.
     */
    private fun assertControlsClearOfSideBarAndCutout(name: String, ready: () -> Unit) {
        for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE)) {
            // Wait for rendered controls and the newly enabled overlays before forcing another
            // window transition, including when the previous test left the screen reversed.
            ready()
            awaitSideBarAndCutout()
            screen.freshControls()
            screen.onMain { screen.resumed().requestedOrientation = orientation }
            val expected = if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) android.view.Surface.ROTATION_90 else android.view.Surface.ROTATION_270
            awaitSideBarAndCutout(expected)
            screen.instrumentation.waitForIdleSync()
            ready()
            val controls = screen.freshControls()
            val safe = screen.onMain { freeArea(screen.resumed().window.decorView)!! }
            assertTrue("No $name controls found", controls.isNotEmpty())
            screen.capture("$name-side-bar-$orientation")
            controls.forEach {
                val bounds = it.visibleBounds
                assertTrue("$name control ${it.contentDescription ?: it.resourceName ?: it.text} $bounds outside $safe", safe.contains(bounds))
            }
        }
    }

    private fun awaitSideBarAndCutout(expectedRotation: Int? = null) {
        screen.await("Side navigation bar and cutout did not reach the window (rotation=$expectedRotation)") {
            screen.onMain {
                val activity = screen.resumed()
                val rotation = activity.display!!.rotation
                val landscape = rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270
                val display = activity.windowManager.maximumWindowMetrics.bounds
                val safe = freeArea(activity.window.decorView)
                landscape && (expectedRotation == null || rotation == expectedRotation) &&
                    safe != null && safe.left > display.left && safe.right < display.right
            }
        }
    }

    /**
     * Runs [block] with three-button navigation on a display whose density makes it a tablet
     * (smallest width [TABLET_SMALLEST_WIDTH_DP]), held in landscape, where the bar, or the taskbar
     * holding its buttons, stays along the bottom. From API 36 a screen this large ignores the app's
     * landscape request and follows the display, so the display itself is turned. The density and
     * rotation in place before are put back; a rotation that cannot be read is put back as free.
     */
    private fun <T> onTabletWithButtonBar(block: () -> T): T {
        val sizes = screen.device.executeShellCommand("wm size")
        val shortest = (Regex("Override size: (\\d+)x(\\d+)").find(sizes) ?: Regex("Physical size: (\\d+)x(\\d+)").find(sizes)!!)
            .destructured.let { (width, height) -> minOf(width.toInt(), height.toInt()) }
        val density = Regex("Override density: (\\d+)").find(screen.device.executeShellCommand("wm density"))?.groupValues?.get(1)
        val rotation = screen.device.executeShellCommand("wm user-rotation").trim().takeIf { it.matches(Regex("free|lock [0-3]")) } ?: "free"
        return DisplayOverlays.with(screen.device, DisplayOverlays.THREE_BUTTON_NAVIGATION) {
            try {
                screen.device.executeShellCommand("wm density ${shortest * 160 / TABLET_SMALLEST_WIDTH_DP}")
                screen.device.executeShellCommand("wm user-rotation lock ${android.view.Surface.ROTATION_90}")
                block()
            } finally {
                screen.device.executeShellCommand("wm user-rotation $rotation")
                screen.device.executeShellCommand(if (density != null) "wm density $density" else "wm density reset")
            }
        }
    }

    /**
     * Waits until the resumed screen is laid out as a tablet with the navigation bar along its bottom,
     * scrolls every scrollable list on it to its end and checks every control [ready] waited for, and
     * every other one on screen, lies above the bar.
     */
    private fun assertControlsAboveBottomBar(name: String, ready: () -> Unit) {
        screen.await("Tablet layout with a bottom navigation bar did not reach the window") {
            screen.onMain {
                val activity = screen.resumed()
                val safe = freeArea(activity.window.decorView)
                // Before API 36 an activity's landscape request can retain the reverse
                // landscape orientation even when the user's rotation is locked to 90 degrees.
                val rotation = activity.display!!.rotation
                activity.resources.configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP &&
                    (rotation == android.view.Surface.ROTATION_90 || rotation == android.view.Surface.ROTATION_270) &&
                    safe != null && safe.bottom < activity.windowManager.maximumWindowMetrics.bounds.bottom
            }
        }
        screen.instrumentation.waitForIdleSync()
        ready()
        screen.device.findObjects(By.pkg(screen.context.packageName).scrollable(true)).forEach(::scrollToEnd)
        val controls = screen.freshControls()
        val safe = screen.onMain { freeArea(screen.resumed().window.decorView)!! }
        assertTrue("No $name controls found", controls.isNotEmpty())
        screen.capture("$name-bottom-bar")
        controls.forEach {
            val bounds = it.visibleBounds
            assertTrue("$name control ${it.contentDescription ?: it.resourceName ?: it.text} $bounds outside $safe", safe.contains(bounds))
        }
    }

    /** A source folder holding more packs than one screen shows, removed afterwards. */
    private fun withTransferSource(block: (File) -> Unit) {
        val source = File(screen.context.cacheDir, "transfer-insets")
        try {
            source.deleteRecursively()
            repeat(TRANSFER_FOLDERS) { assertTrue(File(source, transferFolderName(it)).mkdirs()) }
            block(source)
        } finally {
            source.deleteRecursively()
        }
    }

    private fun launchTransfer(source: File) = screen.launch(TransferActivity::class.java) {
        putExtra(TransferActivity.EXTRA_SOURCE_TYPE, "workspace")
        putExtra(TransferActivity.EXTRA_SOURCE_PATH, source.path)
    }

    private fun transferFolderName(index: Int) = "$TRANSFER_FOLDER_PREFIX%02d".format(index)

    /** The window's area on screen that neither a system bar nor the display cutout covers. */
    private fun freeArea(decor: android.view.View): android.graphics.Rect? {
        val insets = ViewCompat.getRootWindowInsets(decor)
            ?.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()) ?: return null
        return screen.bounds(decor).apply { left += insets.left; top += insets.top; right -= insets.right; bottom -= insets.bottom }
    }

    private companion object {
        const val KEYBOARD_SETTLE_MS = 500L
        const val NO_MATCH = "zz-no-pack-matches"
        const val LARGEST_FONT_SCALE = "2.0"
        const val MAX_DRAGS = 20
        const val TRANSFER_FOLDER_PREFIX = "insets-pack-"
        const val DRAG_MARGIN_PX = 8
        const val DRAG_STEPS = 20
        const val TABLET_SMALLEST_WIDTH_DP = 720
        const val TRANSFER_FOLDERS = 30
    }
}
