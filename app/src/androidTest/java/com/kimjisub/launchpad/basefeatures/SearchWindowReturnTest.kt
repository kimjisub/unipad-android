package com.kimjisub.launchpad.basefeatures

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.activity.SettingsActivity
import com.kimjisub.launchpad.activity.TransferActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The IME-compatible fullscreen window must stay fullscreen after another window takes focus. */
@RunWith(AndroidJUnit4::class)
class SearchWindowReturnTest : PlaybackScreenTest() {
    @Test fun mainStatusBarStaysHiddenAfterSettings() {
        openMain()
        screen.clickDescription(R.string.setting)
        screen.awaitActivity(SettingsActivity::class.java)
        screen.device.pressBack()
        assertStatusBarHidden("main-after-settings", MainActivity::class.java)
    }

    @Test fun mainStatusBarStaysHiddenAfterFilePicker() {
        openMain()
        screen.clickDescription(R.string.import_unipack)
        returnFromFilePicker(MainActivity::class.java)
        assertStatusBarHidden("main-after-file-picker", MainActivity::class.java)
    }

    @Test fun mainStatusBarStaysHiddenAfterPlaying() {
        openMain()
        screen.openLibraryPack()
        screen.options()
        screen.clickDescription(R.string.quit)
        assertStatusBarHidden("main-after-playing", MainActivity::class.java)
    }

    @Test fun transferStatusBarStaysHiddenAfterFilePicker() {
        openTransfer()
        screen.clickText(R.string.migration_grant_access)
        returnFromFilePicker(TransferActivity::class.java)
        assertStatusBarHidden("transfer-after-file-picker", TransferActivity::class.java)
    }

    @Test fun transferStatusBarStaysHiddenAfterDialog() {
        openTransfer()
        val activity = screen.resumed()
        val dialog = screen.onMain {
            // A second window in the same activity exercises focus return without onResume.
            AlertDialog.Builder(activity).setMessage("Window focus regression")
                .setPositiveButton(android.R.string.ok, null).show()
        }
        try {
            screen.await("Dialog did not take window focus") {
                screen.onMain { dialog.window!!.decorView.hasWindowFocus() && !activity.window.decorView.hasWindowFocus() }
            }
            screen.device.pressBack()
            assertStatusBarHidden("transfer-after-dialog", TransferActivity::class.java)
        } finally {
            screen.onMain { dialog.dismiss() }
        }
    }

    private fun openMain() {
        screen.launch(MainActivity::class.java)
        screen.node(By.text(FeatureScreen.TITLE))
        assertStatusBarHidden("main-before-leaving", MainActivity::class.java)
    }

    private fun openTransfer() {
        screen.launch(TransferActivity::class.java) { putExtra(TransferActivity.EXTRA_SOURCE_TYPE, "backup") }
        screen.node(By.text(screen.text(R.string.migration_grant_access)))
        assertStatusBarHidden("transfer-before-leaving", TransferActivity::class.java)
    }

    private fun returnFromFilePicker(type: Class<out Activity>) {
        val picker = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(android.content.Intent.CATEGORY_OPENABLE).setType("*/*")
        // Resolve from the shell so app package-visibility rules cannot hide the system picker.
        val packageName = screen.device.executeShellCommand(
            "cmd package resolve-activity --brief -a ${picker.action} -c android.intent.category.OPENABLE -t '*/*'"
        ).lineSequence().map(String::trim).last { it.contains('/') }.substringBefore('/')
        screen.await("System file picker did not open") { screen.device.currentPackageName == packageName }
        screen.capture("${type.simpleName}-file-picker")
        screen.device.pressBack()
        screen.awaitActivity(type)
    }

    private fun assertStatusBarHidden(name: String, type: Class<out Activity>) {
        screen.awaitActivity(type)
        var last: Boolean? = null
        var changed = SystemClock.elapsedRealtime()
        screen.await("Status bar visibility did not settle after $name") {
            val focused = screen.onMain { screen.resumed().window.decorView.hasWindowFocus() }
            if (!focused) return@await false
            val visible = statusBarVisible()
            val now = SystemClock.elapsedRealtime()
            if (visible != last) { last = visible; changed = now }
            now - changed >= 500
        }
        // A focused, laid-out activity can still be behind the other window's closing animation.
        // Keep a rendered frame as evidence, and check the bar once more after that transition.
        captureRenderedScreen(name)
        val visible = statusBarVisible()
        assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/$name-windows.txt") {
            it.writeText(screen.device.executeShellCommand("dumpsys window windows")); true
        })
        println("$name statusBarVisible=$visible")
        assertFalse("Status bar covers the search screen after $name", visible)
    }

    private fun statusBarVisible(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT < 30) {
            // Compat visibility on these edge-to-edge windows includes stable bar space even
            // when the bar is hidden. Read the system's actual status-bar window instead.
            val dump = screen.device.executeShellCommand("dumpsys window windows")
            val status = Regex("(?ms)^  Window #\\d+ Window\\{[^\\n]* StatusBar\\}:.*?(?=^  Window #|\\z)")
                .find(dump)?.value ?: error("System status-bar window missing")
            Regex("\\bisVisible=(true|false)\\b").find(status)!!.groupValues[1].toBoolean()
        } else screen.onMain {
            ViewCompat.getRootWindowInsets(screen.resumed().window.decorView)!!
                .isVisible(WindowInsetsCompat.Type.statusBars())
        }
    }

    private fun captureRenderedScreen(name: String) {
        var frame: Bitmap? = null
        try {
            screen.await("Returned screen stayed black after $name") {
                frame?.recycle()
                frame = screen.instrumentation.uiAutomation.takeScreenshot()
                val bitmap = frame ?: return@await false
                // Sample the centre of the app, excluding the system bars. A black transition
                // frame must not stand in for the requested before/after screenshot.
                (2..8).any { x -> (2..8).any { y ->
                    val pixel = bitmap.getPixel(bitmap.width * x / 10, bitmap.height * y / 10)
                    maxOf(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) > 16
                } }
            }
            assertTrue("Rendered screenshot was not saved", TestCaptures.save(screen.device, "basefeatures-captures/$name.png") {
                it.outputStream().use { output -> frame!!.compress(Bitmap.CompressFormat.PNG, 100, output) }
            })
        } finally {
            frame?.recycle()
        }
    }

}
