package com.kimjisub.launchpad.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModelStore
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PerformancePreferenceTest {
    private val context = mockk<Context>()
    private val storage = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val values = mutableMapOf<String, Boolean>()
    private val models = ViewModelStore()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { context.getSharedPreferences("data", Context.MODE_PRIVATE) } returns storage
        every { storage.getBoolean(any(), any()) } answers { values[firstArg()] ?: secondArg() }
        every { storage.edit() } returns editor
        every { editor.putBoolean(any(), any()) } answers { values[firstArg()] = secondArg(); editor }
    }

    @After fun tearDown() {
        models.clear()
        Dispatchers.resetMain()
    }

    @Test fun missingPreferencesKeepSlideOffAndWatermarkOnWithoutWriting() {
        val prefs = PreferenceManager(context)
        assertFalse(prefs.slideMode)
        assertTrue(prefs.watermark)
        verify(exactly = 0) { storage.edit() }
    }

    @Test fun explicitChoicesSurviveOpeningANewPreferenceManager() {
        for (choice in listOf(false, true)) {
            PreferenceManager(context).apply { slideMode = choice; watermark = choice }
            PreferenceManager(context).apply {
                assertEquals(choice, slideMode)
                assertEquals(choice, watermark)
            }
        }
        verify(exactly = 4) { editor.apply() }
    }

    @Test fun bindingAgainCannotOverwriteTheLatestExplicitWatermarkChoice() {
        values["Watermark"] = false
        val prefs = PreferenceManager(context)
        val vm = PlayActivityViewModel(mockk(relaxed = true), UsageAnalytics { _, _ -> })
        models.put("play", vm)
        vm.bindPreferences(prefs)
        vm.setupCheckBoxListeners()
        assertFalse(vm.scbWatermark.checked)
        verify(exactly = 0) { storage.edit() }
        vm.scbWatermark.setChecked(true)
        assertTrue(PreferenceManager(context).watermark)
        val stale = mockk<PreferenceManager>()
        every { stale.watermark } returns false
        vm.bindPreferences(stale)
        assertTrue(vm.scbWatermark.checked)
        verify(exactly = 0) { stale.watermark }
    }
}
