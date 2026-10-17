package com.kimjisub.launchpad.basefeatures

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Rect
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
    /** A window/locale transition can leave UiAutomation's previous tree cached during transitions. */
    private fun freshNode(selector: BySelector): UiObject2? = withResponsiveInput {
        if (android.os.Build.VERSION.SDK_INT >= 34) instrumentation.uiAutomation.clearCache()
        device.findObject(selector)
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
    fun openPlay(folder: File = pack()) {
        launch(PlayActivity::class.java) { putExtra("path", folder.path) }
        await("Pack/audio loading did not finish") { onMain { !vm().unipackLoading && !vm().soundLoadingActive && vm().uiLoaded } }
        node(By.desc(text(R.string.menu)))
    }
    /**
     * Presses Home and waits for the launcher. pressHome() waits only 1 second for an accessibility
     * event, even if Home succeeded, so this requires the key injection and the launcher in front
     * under the usual deadline. The launcher is resolved through the device shell: target package
     * visibility can hide it and make UiDevice.launcherPackageName return Settings' fallback home.
     */
    fun goHome() {
        val launcher = device.executeShellCommand(
            "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
        ).lineSequence().map(String::trim).lastOrNull { it.contains('/') }?.substringBefore('/')
        assertNotNull("Default launcher missing", launcher)
        assertTrue("Home key injection failed", device.pressKeyCode(android.view.KeyEvent.KEYCODE_HOME))
        await("Home did not show the launcher") { device.currentPackageName == launcher }
    }
    /** Brings [activity]'s task back in front, as choosing it in recent apps does. */
    fun bringToFront(activity: Activity) {
        context.getSystemService(ActivityManager::class.java).appTasks
            .single { it.taskInfo?.taskId == activity.taskId }.moveToFront()
    }
    fun padBounds(): List<Rect> = onMain {
        views(resumed().window.decorView).filterIsInstance<PadView>().map(::bounds)
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
    fun capture(name: String) {
        val folder = "basefeatures-captures"
        assertTrue("Screenshot was not saved", TestCaptures.screenshot(device, "$folder/$name.png"))
        if (name.startsWith("missing-")) {
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
        const val TITLE = "Base Features Synthetic Pack"
        const val STORE_ID = "basefeatures-store"
        // Earlier versions of this suite installed a pack here; a device they stopped on still has it.
        private const val OLD_SHARE_ID = "Base Features Synthetic Pack #basefeatures-share"
        val IDS = setOf(PACK_ID, STORE_ID, OLD_SHARE_ID, TestUniPack.FOLDER_NAME, "basefeatures-file")
    }
}
