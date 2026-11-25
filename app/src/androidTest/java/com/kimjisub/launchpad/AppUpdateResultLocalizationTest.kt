package com.kimjisub.launchpad

import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.manager.PreferenceAppUpdateStore
import com.kimjisub.launchpad.manager.UpdateStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ErrorCollector
import java.io.IOException
import java.util.Locale

/** Real update result dialogs and their actions, with an offline source that never installs. */
class AppUpdateResultLocalizationTest : BaseUITest() {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val errors = ErrorCollector()

    @Test fun spanishResults() = checkResults("es")
    @Test fun indonesianResults() = checkResults("in")
    @Test fun portugueseResults() = checkResults("pt-BR")

    private fun checkResults(language: String) {
        val oldLanguage = AppLanguage.get()
        val oldScale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("settings put system font_scale 1.0")
            AppLanguage.set(language)
            DisplaySettings.withPhoneDisplay(device, 1080 to 2400, 420) {
                val source = ControlledSource()
                PreferenceAppUpdateStore(context).apply {
                    lastAutoCheckAt = null
                    declinedAt = null
                    installPostponedAt = null
                    downloadRequested = false
                }
                MainActivity.appUpdateSourceForTest = { source }
                launchToMainScreen()
                openNotice()
                assertTrue(waitForText(R.string.update_none))
                compose.waitForIdle()
                takeScreenshot("update_results_${language}_none")
                checkDialogText(R.string.update_none, R.string.update_close)
                closeDialog()

                source.error = IOException("offline")
                openNotice()
                assertTrue(waitForText(R.string.update_check_failed))
                compose.waitForIdle()
                takeScreenshot("update_results_${language}_check_failed")
                checkDialogText(R.string.update_check_failed, R.string.update_check_again, R.string.update_close)
                source.error = null
                device.findObject(By.res("app_update_dialog_confirm")).click()
                assertTrue("retry did not show the successful empty result", waitForText(R.string.update_none))
                closeDialog()

                source.emit(UpdateStatus.Failed)
                assertTrue(waitForText(R.string.update_failed))
                openNotice()
                assertTrue(waitForText(R.string.update_retry))
                compose.waitForIdle()
                takeScreenshot("update_results_${language}_failed_card")
                checkDialogText(R.string.update_failed, R.string.update_retry, R.string.update_check_in_store,
                    R.string.update_later, R.string.update_close)
                closeDialog()
            }
        } finally {
            MainActivity.appUpdateSourceForTest = null
            device.executeShellCommand(if (oldScale == "null") "settings delete system font_scale" else "settings put system font_scale $oldScale")
            AppLanguage.set(oldLanguage)
        }
    }

    private fun waitForText(resource: Int) = device.wait(Until.hasObject(By.text(str(resource))), 10000L)

    private fun openNotice() {
        compose.waitUntil(timeoutMillis = 10000L) {
            device.findObject(By.res("app_update_notice"))?.isEnabled == true
        }
        device.findObject(By.res("app_update_notice")).click()
    }

    private fun closeDialog() {
        device.findObject(By.res("app_update_dialog_dismiss")).click()
        assertTrue(device.wait(Until.gone(By.res("app_update_dialog")), 5000L))
        assertTrue("closing the result did not return to the pack list", waitForMainScreen())
    }

    private fun checkDialogText(vararg resources: Int) {
        val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) })
        resources.forEach { resource ->
            errors.checkSucceeds {
                val translated = str(resource)
                assertFalse("result still uses English: $translated", translated == english.getString(resource))
                val text = compose.onNode(hasText(translated) and hasAnyAncestor(hasTestTag("app_update_dialog")), useUnmergedTree = true)
                text.assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertFalse("result is truncated: $translated", layout.hasVisualOverflow)
                val bounds = text.fetchSemanticsNode().boundsInRoot
                assertTrue("result clipped vertically", bounds.height >= layout.getLineBottom(layout.lineCount - 1) - 1)
                println("Result '$translated': lines=${layout.lineCount}, size=${layout.size}")
            }
        }
    }
}
