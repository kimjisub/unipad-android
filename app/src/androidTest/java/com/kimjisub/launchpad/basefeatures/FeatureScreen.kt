package com.kimjisub.launchpad.basefeatures

import android.app.Activity
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
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.kimjisub.design.view.PadView
import com.kimjisub.launchpad.R
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
    fun node(selector: BySelector): UiObject2 = device.wait(Until.findObject(selector), 10000)
        ?: run { capture("missing-node-${SystemClock.uptimeMillis()}"); throw AssertionError("Missing $selector") }
    /** Locale changes recreate Settings; reacquire its scroll container for every gesture. */
    fun scrollTo(selector: BySelector, direction: Direction): UiObject2 {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < deadline) {
            device.findObject(selector)?.let { return it }
            try {
                device.findObject(By.scrollable(true))?.scroll(direction, 0.8f)
            } catch (_: StaleObjectException) {
                // The screen was replaced during this gesture; the next iteration finds it again.
            }
            SystemClock.sleep(25)
        }
        capture("missing-scroll-node-${SystemClock.uptimeMillis()}")
        throw AssertionError("Missing $selector after scrolling $direction")
    }
    fun clickText(id: Int) = node(By.text(text(id))).click()
    fun clickDescription(id: Int) = node(By.desc(text(id))).click()
    fun await(message: String, check: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (check()) return
            SystemClock.sleep(25)
        }
        assertTrue(message, check())
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
        return scenario
    }
    fun openPlay(folder: File = pack()) {
        launch(PlayActivity::class.java) { putExtra("path", folder.path) }
        await("Pack/audio loading did not finish") { onMain { !vm().unipackLoading && !vm().soundLoadingActive && vm().uiLoaded } }
        node(By.desc(text(R.string.menu)))
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
        val folder = File(context.getExternalFilesDir(null), "basefeatures-captures").apply { mkdirs() }
        assertTrue("Screenshot was not saved", device.takeScreenshot(File(folder, "$name.png")))
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
        const val SHARE_ID = "Base Features Synthetic Pack #basefeatures-share"
        const val STORE_ID = "basefeatures-store"
        val IDS = setOf(PACK_ID, SHARE_ID, STORE_ID, TestUniPack.FOLDER_NAME, "basefeatures-file")
    }
}
