package com.kimjisub.launchpad.basefeatures

import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.kimjisub.launchpad.AppLanguage
import com.kimjisub.launchpad.DisplaySettings
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.MainActivity
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** Check the real empty library, including the translated button names inserted into its steps. */
class FirstRunGuideLocalizationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var screen: FeatureScreen

    @Before fun setUp() {
        screen = FeatureScreen()
        screen.assertDedicatedWorkspace()
        screen.cleanPacks()
    }

    @After fun tearDown() = screen.close()

    @Test fun indonesian() = check("in")
    @Test fun spanish() = check("es")
    @Test fun portuguese() = check("pt-BR")
    @Test fun english() = check("en")
    @Test fun korean() = check("ko")

    private fun check(language: String) {
        val oldLanguage = AppLanguage.get()
        try {
            AppLanguage.set(language)
            DisplaySettings.withPhoneDisplay(screen.device, 1080 to 2400, 420) {
                screen.launch(MainActivity::class.java)
                val context = screen.onMain { screen.resumed() }
                val english = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                    setLocale(Locale.ENGLISH)
                })
                val keys = listOf(R.string.guide_empty_title, R.string.guide_empty_step_get,
                    R.string.guide_empty_step_play, R.string.guide_empty_step_pads, R.string.guide_empty_link,
                    R.string.update_check)
                val texts = listOf(
                    context.getString(R.string.guide_empty_title),
                    context.getString(R.string.guide_empty_step_get,
                        context.getString(R.string.guide_download_new), context.getString(R.string.guide_import_external)),
                    context.getString(R.string.guide_empty_step_play, context.getString(R.string.unipack_play)),
                    context.getString(R.string.guide_empty_step_pads),
                    context.getString(R.string.guide_empty_link),
                )
                // Save the initial screen before scrolling to verify every sentence and link.
                compose.onNodeWithText(texts.first()).assertIsDisplayed()
                screen.capture("first-guide-$language-default")
                if (language != "en") keys.forEach { key ->
                    assertNotEquals("Guide must not fall back to English: $key", english.getString(key), context.getString(key))
                }
                texts.forEach { text -> compose.onNodeWithText(text).performScrollTo().assertIsDisplayed() }
                listOf(R.string.guide_download_new, R.string.guide_import_external).forEach { key ->
                    compose.onNodeWithText(context.getString(key)).performScrollTo().assertIsDisplayed()
                }
            }
        } finally {
            AppLanguage.set(oldLanguage)
        }
    }
}
