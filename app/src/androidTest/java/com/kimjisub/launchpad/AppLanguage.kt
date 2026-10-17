package com.kimjisub.launchpad

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

/**
 * The language picked in the app rather than on the device, as language tags ("" follows the device).
 *
 * Android 13+ keeps it in [LocaleManager] and updates the whole process. Below that AppCompat applies
 * it to each activity only, so the application context keeps the device language; [localized] gives
 * a context whose strings match what the screens show.
 */
object AppLanguage {
    private const val APPLY_TIMEOUT_MS = 5000L
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val app: Context get() = instrumentation.targetContext

    fun get(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else AppCompatDelegate.getApplicationLocales().toLanguageTags()

    /** Sets the app language and, on Android 13+, waits until the process resources use it. */
    fun set(tags: String) {
        instrumentation.runOnMainSync {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
            } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
        }
        val wanted = LocaleList.forLanguageTags(tags).get(0) ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val deadline = SystemClock.uptimeMillis() + APPLY_TIMEOUT_MS
        while (app.resources.configuration.locales[0].language != wanted.language) {
            assertTrue("App language '$tags' was not applied", SystemClock.uptimeMillis() < deadline)
            SystemClock.sleep(100)
        }
    }

    fun localized(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (tags.isEmpty()) return context
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tags))
        }
        return context.createConfigurationContext(configuration)
    }
}
