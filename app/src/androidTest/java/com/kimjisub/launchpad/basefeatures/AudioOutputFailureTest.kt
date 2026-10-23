package com.kimjisub.launchpad.basefeatures

import android.app.LocaleManager
import android.os.LocaleList
import android.view.accessibility.AccessibilityEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Deliberately refuses output startup; it does not simulate or establish a real phone-call failure. */
@RunWith(AndroidJUnit4::class)
class AudioOutputFailureTest {
    private lateinit var screen: FeatureScreen
    private lateinit var doubles: Module
    private val startGate = CountDownLatch(1)
    private lateinit var localeManager: LocaleManager
    private lateinit var originalLocales: LocaleList

    @Before fun setUp() {
        screen = FeatureScreen()
        check(android.os.Build.VERSION.SDK_INT >= 33) { "This suite requires API 33+" }
        localeManager = screen.context.getSystemService(LocaleManager::class.java)
        originalLocales = localeManager.applicationLocales
        screen.assertDedicatedWorkspace()
        screen.cleanPacks()
        screen.install()
        val engine = object : SoundRunner.Engine by RecordingAudio() {
            override fun start(): Boolean {
                check(startGate.await(10, TimeUnit.SECONDS)) { "Output failure was not released" }
                return false
            }
        }
        doubles = module { single<SoundRunner.Engine> { engine } }
        loadKoinModules(doubles)
    }

    @After fun tearDown() {
        startGate.countDown()
        try {
            screen.close()
        } finally {
            unloadKoinModules(doubles)
            localeManager.applicationLocales = originalLocales
        }
    }

    @Test fun outputStartupFailureShowsEnglishNoticeAndClosesPlayback() =
        assertVisibleNotice(Locale.ENGLISH, "Couldn't open audio output.\nTry opening the pack again.")

    @Test fun outputStartupFailureShowsKoreanNoticeAndClosesPlayback() =
        assertVisibleNotice(Locale.KOREAN, "소리 출력을 시작하지 못했습니다.\n팩을 다시 열어 주세요.")

    private fun assertVisibleNotice(locale: Locale, expected: String) {
        localeManager.applicationLocales = LocaleList(locale)
        screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
        val activity = screen.resumed()
        assertEquals(locale.language, screen.onMain { activity.resources.configuration.locales[0].language })
        val notices = CopyOnWriteArrayList<Int>()
        screen.onMain {
            val vm = screen.vm()
            val original = checkNotNull(vm.uiCallback)
            vm.uiCallback = object : PlayActivityViewModel.UiCallback by original {
                override fun showToast(resId: Int) {
                    notices += resId
                    original.showToast(resId)
                }
            }
        }
        // Toasts are noninteractive system windows, so read their displayed accessibility event
        // rather than looking for an app view. Capture immediately while that same toast is visible.
        val shownMessages = CopyOnWriteArrayList<String>()
        val shown = try {
            screen.instrumentation.uiAutomation.executeAndWaitForEvent(
                { startGate.countDown() },
                { event ->
                    val isToast = event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                        event.className?.toString() == "android.widget.Toast"
                    if (isToast) shownMessages += event.text.toString()
                    isToast && event.packageName?.toString() == screen.context.packageName &&
                        event.text.firstOrNull()?.toString() == expected
                },
                10000,
            )
        } catch (error: TimeoutException) {
            screen.capture("audio-output-failure-missing-${locale.language}")
            throw AssertionError("No displayed toast with exact text: $expected; received: $shownMessages", error)
        }
        screen.instrumentation.uiAutomation.waitForIdle(100, 1000)
        screen.capture("audio-output-failure-visible-${locale.language}")
        // Android also appends the application label; the first entry is the displayed message.
        assertEquals(expected, shown.text.first().toString())
        assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/audio-output-failure-${locale.language}-event.txt") {
            it.writeText(shown.toString())
            true
        })
        screen.await("Output failure notice missing") { notices.isNotEmpty() }
        assertEquals("Output failure must request its exact notice", R.string.audio_output_unavailable, notices.single())
        assertEquals(expected, screen.onMain { activity.getString(notices.single()) })
        screen.await("Playback must still close after output startup fails") {
            screen.onMain { activity.isFinishing || activity.isDestroyed }
        }
    }
}
