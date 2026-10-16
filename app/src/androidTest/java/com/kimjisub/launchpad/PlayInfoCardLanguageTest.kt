package com.kimjisub.launchpad

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pack info card at the top of the Play option panel names a pack without a title and, once
 * expanded, its pad grid and chain count in the app language, not in fixed English words.
 */
@RunWith(AndroidJUnit4::class)
class PlayInfoCardLanguageTest : BaseUITest() {

    @Test
    fun koreanCardNamesAnUntitledPackAndItsChains() = checkCard("ko-KR", "ko", untitled = "제목 없음", size = "8×8  ·  체인 2개")

    @Test
    fun englishCardNamesAnUntitledPackAndItsChains() = checkCard("en-US", "en", untitled = "Untitled", size = "8×8  ·  2 chains")

    private fun checkCard(languageTag: String, label: String, untitled: String, size: String) {
        TestUniPack.install(context, title = "")
        val original = appLanguages()
        try {
            setAppLanguages(languageTag)
            launchToMainScreen()
            pressTestPackPlayFlag()
            // A pack without a title opens with a warning that names the missing field.
            val accept = device.wait(Until.findObject(By.text(str(R.string.accept))), PLAY_TIMEOUT)
            assertNotNull("No missing-title warning to accept", accept)
            accept.click()
            assertTrue("Play screen did not open", waitForPlayScreen())
            openPlayOptions()

            val title = device.wait(Until.findObject(By.text(untitled)), CARD_TIMEOUT_MS)
            takeScreenshot("play_info_card_${label}_untitled")
            assertNotNull("Info card does not show '$untitled' for a pack without a title", title)

            title.click()
            val expanded = device.wait(Until.findObject(By.text(size)), CARD_TIMEOUT_MS)
            takeScreenshot("play_info_card_${label}_expanded")
            assertNotNull("Expanded info card does not show '$size'", expanded)
            quitPlayToMain()
        } finally {
            setAppLanguages(original)
        }
    }

    private fun appLanguages(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        } else AppCompatDelegate.getApplicationLocales().toLanguageTags()

    private fun setAppLanguages(tags: String) = InstrumentationRegistry.getInstrumentation().runOnMainSync {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
        } else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
    }

    private companion object {
        const val CARD_TIMEOUT_MS = 5000L
    }
}
