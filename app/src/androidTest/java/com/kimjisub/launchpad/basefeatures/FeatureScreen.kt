package com.kimjisub.launchpad.basefeatures

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Condition
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.ActivityReadiness
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.TestUniPack
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.WorkspaceManager
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import org.junit.Assert.*
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.regex.Pattern
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Separate from the legacy UI helpers: selectors use the installed target's package/resources. */
class FeatureScreen {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context: Context = instrumentation.targetContext
    val device: UiDevice = UiDevice.getInstance(instrumentation)
    val workspace = File(context.getExternalFilesDir(null), "UniPack")
    val repo: UnipackRepository = GlobalContext.get().get()
    private val scenarios = mutableListOf<ActivityScenario<out Activity>>()

    fun text(id: Int) = context.getString(id)
    /** Animated play controls never become idle; readiness is checked by the caller's condition. */
    private fun <T> withResponsiveInput(block: () -> T): T {
        val configurator = Configurator.getInstance()
        val previous = configurator.waitForIdleTimeout
        configurator.waitForIdleTimeout = minOf(previous, 500L)
        return try { block() } finally { configurator.waitForIdleTimeout = previous }
    }
    /** A window/locale transition can leave UiAutomation's previous tree cached; API 33 has no cache to clear. */
    private fun clearNodeCache() {
        if (android.os.Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
    }
    private fun freshNode(selector: BySelector): UiObject2? = withResponsiveInput {
        clearNodeCache()
        device.findObject(selector)
    }
    /**
     * Clickable controls of the resumed screen, read once accessibility places that screen's window where
     * it is. A rotation the activity handles itself changes no view, so nodes read around it can still
     * carry the previous rotation's screen coordinates.
     */
    fun freshControls(): List<UiObject2> {
        var controls = emptyList<UiObject2>()
        var seen = emptyList<Rect>()
        var frames = emptyList<Rect>()
        var window = Rect()
        await({ "Visible window regions $frames must lie inside view $window; accessibility roots $seen must match it" }) {
            withResponsiveInput {
                window = onMain { bounds(resumed().window.decorView) }
                clearNodeCache()
                val windows = instrumentation.uiAutomation.windows
                val appWindows = windows.filter { it.root?.packageName == context.packageName }
                frames = appWindows.map { Rect().also(it::getBoundsInScreen) }
                seen = appWindows.mapNotNull { it.root }.filter { it.refresh() }.map { root ->
                    // API 33 has no UiAutomation.clearCache(), but a node can refresh itself
                    // from the view. Keep the native window and view geometry checks as well.
                    Rect().also(root::getBoundsInScreen)
                }
                // AccessibilityWindowInfo reports the visible region, which a tablet taskbar may clip.
                // The refreshed root still has to match the entire view, including its bar insets.
                (frames.any { !it.isEmpty && window.contains(it) } && window in seen).also {
                    if (it) controls = device.findObjects(By.pkg(context.packageName).clickable(true))
                }
            }
        }
        return controls
    }
    fun node(selector: BySelector): UiObject2 = device.wait(
        Condition<UiDevice, UiObject2?> { freshNode(selector) }, 10000
    )
        ?: run { capture("missing-node-${SystemClock.uptimeMillis()}"); throw AssertionError("Missing $selector") }
    /** Locale changes recreate Settings; reacquire its scroll container for every gesture. */
    fun scrollTo(selector: BySelector, direction: Direction): UiObject2 {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < deadline) {
            freshNode(selector)?.let { return it }
            try {
                freshNode(By.pkg(context.packageName).scrollable(true))?.scroll(direction, 0.8f)
            } catch (_: StaleObjectException) {
                // The screen was replaced during this gesture; the next iteration finds it again.
            }
            SystemClock.sleep(25)
        }
        capture("missing-scroll-node-${SystemClock.uptimeMillis()}")
        throw AssertionError("Missing $selector after scrolling $direction")
    }
    /** Reacquire only if the window replaced the node before input was injected. */
    fun click(selector: BySelector) = withResponsiveInput {
        val deadline = SystemClock.elapsedRealtime() + 10000
        do {
            try {
                freshNode(selector)?.let { it.click(); return@withResponsiveInput }
            } catch (_: StaleObjectException) {
                // No click was injected; retry against the current window, inside the same budget.
            }
            SystemClock.sleep(25)
        } while (SystemClock.elapsedRealtime() < deadline)
        capture("missing-click-node-${SystemClock.uptimeMillis()}")
        throw AssertionError("Could not click $selector")
    }
    /** Whether [selector] is on screen now; unlike [UiDevice.hasObject] it waits at most 0.5 s for idle and reads a fresh tree. */
    fun has(selector: BySelector): Boolean = freshNode(selector) != null
    fun clickText(id: Int) = click(By.text(text(id)))
    fun clickDescription(id: Int) = click(By.desc(text(id)))
    fun awaitActivity(type: Class<out Activity>, language: String? = null) {
        ActivityReadiness.await(context, device, 10000, type, language)
    }

    fun await(message: String, check: () -> Boolean) = await({ message }, check)
    /** [message] is built only on failure, after the last check, so it can report the final state. */
    fun await(message: () -> String, check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (check()) return
            SystemClock.sleep(25)
        }
        val passed = check()
        if (!passed) {
            capture("missing-ready-${SystemClock.uptimeMillis()}")
            throw AssertionError(message())
        }
    }
    /** Actual IME window position, independent of whether the app received its keyboard insets. */
    fun keyboardTopOnScreen(): Int = instrumentation.uiAutomation.windows
        .firstOrNull { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        ?.let { window -> Rect().also(window::getBoundsInScreen).top }
        ?: device.displayHeight

    /** Returns the same keyboard boundary observed stable for 500 ms, rather than sampling again. */
    fun awaitKeyboardTopOnScreen(): Int {
        var top = device.displayHeight
        var changed = SystemClock.elapsedRealtime()
        await("Search keyboard did not finish opening") {
            val current = keyboardTopOnScreen()
            val now = SystemClock.elapsedRealtime()
            if (current != top) { top = current; changed = now }
            top < device.displayHeight && now - changed >= 500
        }
        return top
    }

    fun <T> onMain(block: () -> T): T {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) return block()
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }
    fun resumed(): Activity = onMain {
        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
    }
    fun vm(): PlayActivityViewModel = onMain { ViewModelProvider(resumed() as PlayActivity)[PlayActivityViewModel::class.java] }

    fun <T : Activity> launch(type: Class<T>, configure: Intent.() -> Unit = {}): ActivityScenario<T> {
        val scenario = ActivityScenario.launch<T>(Intent(context, type).apply(configure))
        scenarios += scenario
        awaitActivity(type)
        return scenario
    }
    fun openPlay(folder: File = pack()): ActivityScenario<PlayActivity> {
        val scenario = launch(PlayActivity::class.java) { putExtra("path", folder.path) }
        await("Pack/audio loading did not finish") { onMain { !vm().unipackLoading && !vm().soundLoadingActive && vm().uiLoaded } }
        node(By.desc(text(R.string.menu)))
        return scenario
    }
    /** Package visibility can hide Home from the target app and make UiDevice resolve Settings instead. */
    private fun launcherPackage(): String {
        val launcher = device.executeShellCommand(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        ).lineSequence().map(String::trim).lastOrNull { it.contains('/') }?.substringBefore('/')
        assertNotNull("Default launcher missing", launcher)
        return launcher!!
    }
    /**
     * Presses Home and waits for the launcher and [left]: what leaving must have done to the app.
     * pressHome() waits only 1 second for an accessibility event, even when Home succeeds, so use
     * key injection and check the resulting state under the usual deadline.
     */
    fun goHome(left: () -> Boolean) {
        val launcher = launcherPackage()
        assertTrue("Home key injection failed", device.pressKeyCode(android.view.KeyEvent.KEYCODE_HOME))
        await("Home did not show the launcher and leave the app") { device.currentPackageName == launcher && left() }
    }
    /** Selects the app's card in the system's recent-apps UI; a background app cannot move its own task. */
    fun bringToFront(activity: Activity) {
        val launcher = launcherPackage()
        val label = context.packageManager.getApplicationLabel(activity.applicationInfo).toString()
        // Instrumented tasks can be described by their application/activity classes instead.
        val componentLabel = "${activity.application.javaClass.name} ${activity.javaClass.name}"
        val cardLabel = Pattern.compile(listOf(label, componentLabel).joinToString("|") { Pattern.quote(it) })
        assertTrue("Recent apps key injection failed", device.pressKeyCode(android.view.KeyEvent.KEYCODE_APP_SWITCH))
        val identity = By.pkg(launcher).desc(cardLabel)
        // Newer launchers label the snapshot inside the clickable task card.
        val card = if (node(identity).isClickable) identity.clickable(true)
        else By.pkg(launcher).clickable(true).hasDescendant(identity)
        capture("recent-apps-before-return-${SystemClock.uptimeMillis()}")
        click(card)
        awaitActivity(activity.javaClass)
        assertSame("Recent apps must return to the same activity", activity, resumed())
    }
    fun padBounds(): List<Rect> = onMain {
        views(resumed().window.decorView).filterIsInstance<PadView>().map(::bounds)
    }
    /**
     * One pad as the display shows it: whether its LED layer holds a light (press feedback, a keyLED
     * or a guide), the colour the pad view itself draws at its centre, and the screen pixel there.
     */
    class PadOnScreen(val index: Int, val center: Point, val lit: Boolean, val drawn: Int, val shown: Int) {
        val onScreen: Boolean get() = channels(drawn).zip(channels(shown)).all { (a, b) -> kotlin.math.abs(a - b) <= PIXEL_TOLERANCE }
        override fun toString() =
            "pad ${index + 1} at ${center.x},${center.y}: view ${if (lit) "lit" else "dark"} ${rgb(drawn)}, screen ${rgb(shown)}"
        private fun channels(color: Int) = listOf(Color.red(color), Color.green(color), Color.blue(color))
        private fun rgb(color: Int) = channels(color).joinToString(",")
    }

    /** Every pad of the resumed screen, in [padBounds] order, compared with [shot] taken after reading the views. */
    fun padsOnScreen(shot: () -> Bitmap?): Pair<Bitmap, List<PadOnScreen>>? {
        val views = onMain {
            views(resumed().window.decorView).filterIsInstance<PadView>().map { pad ->
                val bounds = bounds(pad)
                val led = pad.findViewById<View>(com.kimjisub.design.R.id.led).background
                val lit = led != null && (led as? ColorDrawable)?.let { Color.alpha(it.color) != 0 } != false
                val drawing = Bitmap.createBitmap(pad.width, pad.height, Bitmap.Config.ARGB_8888)
                val drawn = try {
                    pad.draw(Canvas(drawing))
                    drawing.getPixel(pad.width / 2, pad.height / 2)
                } finally {
                    drawing.recycle()
                }
                Triple(Point(bounds.centerX(), bounds.centerY()), lit, drawn)
            }
        }
        val frame = shot() ?: return null
        return frame to views.mapIndexed { index, (center, lit, drawn) ->
            PadOnScreen(index, center, lit, drawn, frame.getPixel(center.x, center.y))
        }
    }

    /**
     * Waits for one screenshot in which exactly the pads in [lit] (indices of [padBounds]) hold a
     * light and every pad shows the colour its own view draws, then saves that screenshot as [name].
     * A press sets the light state and the pad view at once, but the display shows it only once the
     * next frame is drawn: a screenshot taken on the light state alone can still show the pad dark.
     */
    fun capturePads(name: String, lit: Set<Int>) {
        var frame: Bitmap? = null
        var pads = emptyList<PadOnScreen>()
        var screenshots = 0
        val started = SystemClock.elapsedRealtime()
        try {
            await({ "Screen did not show exactly pads $lit lit for $name; differing: ${pads.filter { it.lit != (it.index in lit) || !it.onScreen }}" }) {
                frame?.recycle()
                frame = null
                val read = padsOnScreen { instrumentation.uiAutomation.takeScreenshot() } ?: return@await false
                screenshots++
                frame = read.first
                pads = read.second
                pads.isNotEmpty() && pads.all { it.lit == (it.index in lit) && it.onScreen }
            }
            println("Pads on screen $name after $screenshots screenshots, ${SystemClock.elapsedRealtime() - started} ms: " +
                "lit ${pads.filter { it.index in lit }}")
            val shot = checkNotNull(frame)
            assertTrue("Screenshot was not saved", TestCaptures.save(device, "$CAPTURES/$name.png") { file ->
                file.outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            })
        } finally {
            frame?.recycle()
        }
    }

    fun views(view: View): List<View> = buildList {
        if (view.visibility == View.VISIBLE) {
            add(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) addAll(views(view.getChildAt(i)))
        }
    }
    fun bounds(view: View): Rect {
        val origin = IntArray(2).also(view::getLocationOnScreen)
        return Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
    }
    fun ready(): Boolean = onMain {
        val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).singleOrNull()
        activity is PlayActivity && vm().uiLoaded && !vm().soundLoadingActive && !vm().unipackLoading
    }
    fun openLibraryPack() {
        fun row(): UiObject2 = device.findObjects(By.text(TITLE)).firstNotNullOfOrNull { title ->
            var current: UiObject2? = title
            while (current != null && !current.isClickable) current = current.parent
            current
        } ?: throw AssertionError("No clickable pack row")
        row().click()
        node(By.desc(text(R.string.cd_delete)))
        SystemClock.sleep(600)
        val bounds = row().visibleBounds
        device.click(bounds.left + (50 * context.resources.displayMetrics.density).toInt(), bounds.centerY())
        await("Library pack did not open", ::ready)
    }
    fun tapPad(index: Int) {
        val bounds = padBounds()[index]
        assertTrue(device.click(bounds.centerX(), bounds.centerY()))
    }
    fun options() {
        clickDescription(R.string.menu)
        node(By.text(text(R.string.autoPlay)))
    }
    /** Picks a play option, then waits for the panel to go: while it closes it still takes touches meant for the pads. */
    fun chooseOption(id: Int) {
        options()
        clickText(id)
        await("Options remained on screen") { !has(By.text(text(id))) }
    }
    fun capture(name: String) {
        val folder = CAPTURES
        assertTrue("Screenshot was not saved", TestCaptures.screenshot(device, "$folder/$name.png"))
        if (name.startsWith("missing-")) {
            TestCaptures.save(device, "$folder/$name-windows.txt") {
                it.writeText(device.executeShellCommand("dumpsys window windows"))
                true
            }
            ActivityReadiness.captureState(context, device, folder, name)
        }
    }
    fun pack() = File(workspace, PACK_ID)
    fun install(): File {
        val source = TestUniPack.install(context)
        val target = pack()
        target.deleteRecursively()
        assertTrue(source.renameTo(target))
        File(target, "info").writeText(File(target, "info").readText().replace(TestUniPack.TITLE, TITLE))
        File(target, "sounds/silence2.wav").writeBytes(File(target, "sounds/silence.wav").readBytes())
        File(target, "keySound").writeText(File(target, "keySound").readLines().joinToString("\n") {
            if (it.startsWith("2 ")) it.replace("silence.wav", "silence2.wav") else it
        } + "\n")
        File(target, "keyLED/1 1 1 1").writeText("o 1 1 a 5\nd 1500\nf 1 1\n")
        return target
    }
    fun zip(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            pack().walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach {
                zip.putNextEntry(ZipEntry(it.relativeTo(pack()).invariantSeparatorsPath))
                zip.write(it.readBytes())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
    fun assertDedicatedWorkspace() {
        val unknown = workspace.listFiles().orEmpty().filter { it.isDirectory && it.name !in IDS }
        assertTrue("Use a dedicated test installation; existing packs: ${unknown.map { it.name }}", unknown.isEmpty())
    }
    fun cleanPacks() {
        for (id in IDS) {
            File(workspace, id).deleteRecursively()
            repo.delete(id)
        }
    }
    fun close() {
        scenarios.asReversed().forEach { it.close() }
        scenarios.clear()
        onMain {
            for (stage in Stage.values()) {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(stage).toList()
                    .filter { it.packageName == context.packageName }.forEach { it.finish() }
            }
        }
        instrumentation.waitForIdleSync()
        cleanPacks()
    }
    companion object {
        const val PACK_ID = "basefeatures-pack"
        private const val CAPTURES = "basefeatures-captures"
        // Allows rounding of a colour channel in the screenshot; the default theme's pressed and dark pads differ by 16 or more.
        private const val PIXEL_TOLERANCE = 3
        const val TITLE = "Base Features Synthetic Pack"
        const val STORE_ID = "basefeatures-store"
        // Earlier versions of this suite installed a pack here; a device they stopped on still has it.
        private const val OLD_SHARE_ID = "Base Features Synthetic Pack #basefeatures-share"
        val IDS = setOf(PACK_ID, STORE_ID, OLD_SHARE_ID, TestUniPack.FOLDER_NAME, "basefeatures-file")
    }
}
