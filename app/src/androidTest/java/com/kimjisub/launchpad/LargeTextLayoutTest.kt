package com.kimjisub.launchpad

import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.fail
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.kimjisub.launchpad.activity.MainActivity
import org.junit.Assert.assertEquals
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import org.junit.runner.RunWith

/** Real screens at the phone's default and largest accessibility settings, restored after each case. */
@RunWith(AndroidJUnit4::class)
class LargeTextLayoutTest : BaseUITest() {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val errors = ErrorCollector()

    @Test fun englishDefault() = checkScreens("en", 1f, 420)
    @Test fun koreanDefault() = checkScreens("ko", 1f, 420)
    @Test fun englishLarge420() = checkScreens("en", 2f, 420)
    @Test fun koreanLarge420() = checkScreens("ko", 2f, 420)
    @Test fun englishLarge540() = checkScreens("en", 2f, 540)
    @Test fun koreanLarge540() = checkScreens("ko", 2f, 540)

    private fun checkScreens(language: String, scale: Float, density: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val oldLanguage = AppLanguage.get()
        val oldScale = device.executeShellCommand("settings get system font_scale").trim()
        val oldDensity = device.executeShellCommand("wm density")
            .lineSequence().firstOrNull { it.startsWith("Override density:") }?.substringAfter(":")?.trim()
        val prefix = "large_text_${language}_${scale}_${density}"
        try {
            TestUniPack.install(context, title = TITLE, producer = PRODUCER)
            seedPackHistory()
            // A sparse file exercises a multi-digit capacity without allocating its contents.
            java.io.RandomAccessFile(java.io.File(TestUniPack.folder(context), "capacity.bin"), "rw").use {
                it.setLength(1234L * 1024 * 1024)
            }
            device.executeShellCommand("settings put system font_scale $scale")
            device.executeShellCommand("wm density $density")
            AppLanguage.set(language)
            launchToMainScreen()
            instrumentation.runOnMainSync {
                val config = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single().resources.configuration
                assertEquals("Font scale not applied", scale, config.fontScale, 0.01f)
                assertEquals("Display density not applied", density, config.densityDpi)
                assertEquals("Language not applied", language, config.locales[0].language)
                println("Device API ${android.os.Build.VERSION.SDK_INT}, ${device.displayWidth}x${device.displayHeight}, language=$language, fontScale=${config.fontScale}, density=${config.densityDpi}")
            }
            findTestPackRow()
            waitForCapacity("1234 ${str(R.string.mb)}")
            takeScreenshot("${prefix}_main")
            if (scale == 1f) {
                assertEquals("Default pack row height changed", 60f * density / 160f,
                    compose.onNode(hasTestTag("main_pack_${TestUniPack.FOLDER_NAME}")).fetchSemanticsNode().boundsInRoot.height, 1f)
            }
            val producer = compose.onNode(
                hasText(PRODUCER) and hasAnyAncestor(hasTestTag("main_pack_${TestUniPack.FOLDER_NAME}")),
                useUnmergedTree = true,
            )
            errors.checkSucceeds {
                producer.assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                producer.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertTrue("Producer's line is clipped: $layout", layout.getLineBottom(0) <= layout.size.height)
                assertTrue("Producer's visible bounds clip its line", producer.fetchSemanticsNode().boundsInRoot.height >= layout.getLineBottom(0) - 1f)
                null
            }
            checkTotalPanel(prefix, scale)
            openDetailPanel()
            takeScreenshot("${prefix}_detail")
            checkDetailPanel(prefix)
            deselectTestPack(prefix)
            checkFooter(prefix)
            openSettings()
            takeScreenshot("${prefix}_settings")
            errors.checkSucceeds {
                val theme = compose.onNode(hasText(str(R.string.settings_theme)))
                if (scale > 1f) theme.performScrollTo()
                theme.assertIsDisplayed()
                takeScreenshot("${prefix}_settings_scrolled")
                theme.performClick()
                assertTrue("Theme screen did not open", device.wait(Until.hasObject(By.desc(str(R.string.theme_add_title))), 5000L))
                takeScreenshot("${prefix}_theme")
                null
            }
            if (scale == 1f) {
                // Short metadata makes the default-screen pixel comparison independent of marquee timing.
                TestUniPack.install(context)
                seedPackHistory()
                launchToMainScreen()
                findTestPackRow()
                waitForCapacity("0 ${str(R.string.mb)}")
                takeScreenshot("${prefix}_comparison_main")
                openDetailPanel()
                compose.waitForIdle()
                takeScreenshot("${prefix}_comparison_detail")
            }
        } finally {
            device.executeShellCommand(if (oldScale == "null") "settings delete system font_scale"
                else "settings put system font_scale $oldScale")
            device.executeShellCommand("wm density ${oldDensity ?: "reset"}")
            AppLanguage.set(oldLanguage)
            TestUniPack.install(context)
        }
    }

    private fun openDetailPanel() {
        compose.onNode(hasTestTag("main_pack_${TestUniPack.FOLDER_NAME}"))
            .performScrollTo().performClick()
        compose.onNode(hasTestTag("main_detail_${TestUniPack.FOLDER_NAME}"))
            .assertIsDisplayed()
        compose.waitUntil(10000L) {
            compose.onAllNodes(hasText(str(R.string.measuring)), useUnmergedTree = true)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    /**
     * Taps the selected pack again to close its detail panel and checks, straight after, that the panel
     * closed while the main screen stayed in front. Once in a large-text run this tap opened the file
     * picker instead, so a failure records where the tap landed and what was laid out at that moment.
     */
    private fun deselectTestPack(prefix: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Blocking keeps the picker from covering the main screen, so the layout below is what was tapped.
        val picker = instrumentation.addMonitor(
            IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                addDataType("*/*")
            },
            null, true,
        )
        try {
            val row = compose.onNode(hasTestTag("main_pack_${TestUniPack.FOLDER_NAME}")).performScrollTo()
            val rowBounds = row.fetchSemanticsNode().boundsInRoot
            var tap = Offset.Unspecified
            var tapArea = IntSize.Zero
            row.performTouchInput {
                tap = center
                tapArea = visibleSize
                click()
            }
            compose.waitForIdle()
            val detailOpen = compose.onAllNodes(hasTestTag("main_detail_${TestUniPack.FOLDER_NAME}"))
                .fetchSemanticsNodes().isNotEmpty()
            var mainResumed = false
            instrumentation.runOnMainSync {
                mainResumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .any { it is MainActivity }
            }
            if (!detailOpen && mainResumed && picker.hits == 0) return
            takeScreenshot("${prefix}_deselect_failure")
            val layout = compose.onAllNodes(isRoot()).fetchSemanticsNodes()
                .joinToString("\n") { root ->
                    generateSequence(listOf(root)) { level -> level.flatMap { it.children }.ifEmpty { null } }
                        .flatten()
                        .filter { it.config.contains(SemanticsProperties.TestTag) || it.config.contains(SemanticsActions.OnClick) }
                        .joinToString("\n") { "  ${it.config.getOrNull(SemanticsProperties.TestTag)} " +
                            "${it.config.getOrNull(SemanticsProperties.Text) ?: it.config.getOrNull(SemanticsProperties.ContentDescription)} " +
                            "${it.boundsInRoot}" }
                }
            fail("Tapping the selected pack did not return to the main list: detailOpen=$detailOpen, " +
                "mainResumed=$mainResumed, filePickerStarts=${picker.hits}, rowBefore=$rowBounds, " +
                "tap=${rowBounds.topLeft + tap} within $tapArea, laid out:\n$layout")
        } finally {
            instrumentation.removeMonitor(picker)
        }
    }

    private fun waitForCapacity(value: String) {
        compose.waitUntil(10000L) {
            compose.onAllNodes(hasText(value), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun seedPackHistory() {
        val dao = com.kimjisub.launchpad.db.AppDatabase.getInstance(context).unipackDAO()
        dao.delete(TestUniPack.FOLDER_NAME)
        dao.insert(com.kimjisub.launchpad.db.ent.Unipack(
            TestUniPack.FOLDER_NAME, false, 0,
            fixtureDate(2022), fixtureDate(2021),
        ))
    }

    private fun fixtureDate(year: Int): java.util.Date = java.util.Date.from(
        java.time.LocalDate.of(year, 1, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant(),
    )

    private fun checkTotalPanel(prefix: String, scale: Float) {
        errors.checkSucceeds {
            val logo = compose.onNode(hasContentDescription(str(R.string.cd_logo)))
            val version = compose.onNode(hasText(BuildConfig.VERSION_NAME), useUnmergedTree = true)
            logo.assertIsDisplayed()
            assertReadable(version)
            if (scale > 1f) {
                assertTrue("Version overlaps logo", version.fetchSemanticsNode().boundsInRoot.top >=
                    logo.fetchSemanticsNode().boundsInRoot.bottom - 1f)
            }
            null
        }
        val labels = listOf(str(R.string.MPT_playCount), str(R.string.MTP_count), str(R.string.MTP_size),
            "0", "1", "1234 ${str(R.string.mb)}")
        for ((index, text) in labels.withIndex()) {
            errors.checkSucceeds {
                val node = compose.onNode(hasText(text), useUnmergedTree = true)
                scrollIfNeeded(node)
                takeScreenshot("${prefix}_total_$index")
                assertReadable(node)
                null
            }
        }
    }

    private fun checkFooter(prefix: String) {
        for (label in listOf(R.string.guide_download_new, R.string.guide_import_external, R.string.guide_restore_packs)) {
            errors.checkSucceeds {
                val node = compose.onNode(hasText(str(label)), useUnmergedTree = true)
                node.performScrollTo()
                takeScreenshot("${prefix}_footer_${label}")
                assertReadable(node)
                null
            }
        }
    }

    private fun checkDetailPanel(prefix: String) {
        val panel = hasAnyAncestor(hasTestTag("main_detail_${TestUniPack.FOLDER_NAME}"))
        for (label in listOf(R.string.MPP_downloadedDate, R.string.MPP_lastPlayed,
            R.string.MPP_padSize, R.string.MPP_chain, R.string.MPP_soundFiles, R.string.MPP_ledEvents)) {
            errors.checkSucceeds {
                val node = compose.onNode(hasText(str(label)) and panel, useUnmergedTree = true)
                scrollIfNeeded(node)
                takeScreenshot("${prefix}_detail_${label}")
                assertReadable(node)
                null
            }
        }
        // Literal values come from the fixture's 8x8 pads, two chains, 128 sound mappings and one LED file.
        for ((index, value) in listOf("8 × 8", "2", "128", "1", "0").withIndex()) {
            errors.checkSucceeds {
                val node = compose.onNode(hasText(value) and panel, useUnmergedTree = true)
                scrollIfNeeded(node)
                takeScreenshot("${prefix}_property_$index")
                assertReadable(node)
                null
            }
        }
        for (date in listOf("01/01/2021", "01/01/2022")) {
            errors.checkSucceeds {
                val node = compose.onNode(hasText(date) and panel, useUnmergedTree = true)
                scrollIfNeeded(node)
                takeScreenshot("${prefix}_date_${date.replace('/', '_')}")
                assertReadable(node)
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals("Date wraps into fragments", 1, layouts.single().lineCount)
                null
            }
        }
    }

    private fun scrollIfNeeded(node: SemanticsNodeInteraction) {
        val scrollable = generateSequence(node.fetchSemanticsNode().parent) { it.parent }
            .any { it.config.contains(SemanticsActions.ScrollBy) }
        if (scrollable) node.performScrollTo()
    }

    private fun assertReadable(node: SemanticsNodeInteraction) {
        try {
            node.assertIsDisplayed()
        } catch (e: AssertionError) {
            takeScreenshot("not_displayed_${System.currentTimeMillis()}")
            throw AssertionError("${e.message}\n${displayState(node)}", e)
        }
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        println("Text '${layout.layoutInput.text}': size=${layout.size}, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}, overflow=${layout.hasVisualOverflow}")
        assertTrue("Text has omitted lines: ${layout.layoutInput.text}", !layout.multiParagraph.didExceedMaxLines)
        // Paragraph width can retain its wider constraint after Text measures to content width.
        // The rendered line extents and visible bounds identify actual clipping.
        for (line in 0 until layout.lineCount) {
            assertTrue("Text line is clipped horizontally: ${layout.layoutInput.text}",
                layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f)
            assertTrue("Visible bounds clip text horizontally: ${layout.layoutInput.text}",
                node.fetchSemanticsNode().boundsInRoot.width >=
                    layout.getLineRight(line) - layout.getLineLeft(line) - 1f)
            assertTrue("Text line is clipped vertically: ${layout.layoutInput.text}",
                layout.getLineBottom(line) <= layout.size.height + 1f)
        }
        assertTrue("Visible bounds clip text: ${layout.layoutInput.text}",
            node.fetchSemanticsNode().boundsInRoot.height >= layout.getLineBottom(layout.lineCount - 1) - 1f)
    }

    /**
     * What the display check reads. It also fails when no node matches, so this says whether the node
     * still exists, then lists each ancestor's placement, window bounds and scroll, the host view and
     * every node in the left panel.
     */
    private fun displayState(node: SemanticsNodeInteraction): String {
        val clock = compose.mainClock.currentTime
        val semantics = runCatching { node.fetchSemanticsNode() }
        val view = semantics.getOrNull()?.let { (it.root as? androidx.compose.ui.platform.ViewRootForTest)?.view }
        val visible = android.graphics.Rect()
        var viewState = ""
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            viewState = "view=${view?.width}x${view?.height} shown=${view?.isShown} " +
                "visible=${view?.getGlobalVisibleRect(visible)} $visible attached=${view?.isAttachedToWindow}"
        }
        fun describe(n: androidx.compose.ui.semantics.SemanticsNode) =
            "  #${n.id} placed=${n.layoutInfo.isPlaced} attached=${n.layoutInfo.isAttached} " +
                "window=${n.boundsInWindow} size=${n.size} " +
                "scroll=${n.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                    ?.let { "${it.value()}/${it.maxValue()}" }} tag=${n.config.getOrNull(SemanticsProperties.TestTag)} " +
                "text=${n.config.getOrNull(SemanticsProperties.Text)} " +
                "desc=${n.config.getOrNull(SemanticsProperties.ContentDescription)}"
        val chain = semantics.fold(
            { generateSequence(it) { n -> n.parent }.joinToString("\n", transform = ::describe) },
            { "  matching node not found now: ${it.message?.lineSequence()?.firstOrNull()}" },
        )
        val panel = runCatching {
            compose.onNode(hasTestTag("main_left_panel"), useUnmergedTree = true).fetchSemanticsNode()
        }.fold(
            { root -> generateSequence(listOf(root)) { level -> level.flatMap { it.children }.ifEmpty { null } }
                .flatten().joinToString("\n", transform = ::describe) },
            { "  left panel not found: ${it.message?.lineSequence()?.firstOrNull()}" },
        )
        return "clockBefore=$clock clockAfter=${compose.mainClock.currentTime} $viewState\n" +
            "node and ancestors:\n$chain\nleft panel:\n$panel"
    }

    companion object {
        private const val TITLE = "A very long performance pack title for larger text checks"
        private const val PRODUCER = "A very long producer name for larger text checks"
    }
}
