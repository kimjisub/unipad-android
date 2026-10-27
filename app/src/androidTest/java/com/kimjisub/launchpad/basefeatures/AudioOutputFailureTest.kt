package com.kimjisub.launchpad.basefeatures

import android.app.LocaleManager
import android.os.LocaleList
import android.view.accessibility.AccessibilityEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.unipack.runner.SoundRunner
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
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Injects engine failures; it does not simulate or establish a real phone-call failure. */
@RunWith(AndroidJUnit4::class)
class AudioOutputFailureTest {
    private lateinit var screen: FeatureScreen
    private lateinit var doubles: Module
    private val startGate = CountDownLatch(1)
    @Volatile private var failDuringDecode = false
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
        val recording = RecordingAudio()
        val engine = object : SoundRunner.Engine by recording {
            override fun start(): Boolean {
                check(startGate.await(10, TimeUnit.SECONDS)) { "Output failure was not released" }
                return failDuringDecode
            }

            override fun decode(file: File): OboeAudioEngine.DecodedAudio? {
                check(!failDuringDecode) { "Deliberate sound decoding failure" }
                return recording.decode(file)
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

    @Test fun decodingFailureShowsEnglishReasonsAndKeepsRetry() = assertDecodeNotice(Locale.ENGLISH)

    @Test fun decodingFailureShowsKoreanReasonsAndKeepsRetry() = assertDecodeNotice(Locale.KOREAN)

    private fun assertDecodeNotice(locale: Locale) {
        failDuringDecode = true
        localeManager.applicationLocales = LocaleList(locale)
        screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
        val activity = screen.resumed()
        startGate.countDown()
        val korean = locale.language == "ko"
        val title = if (korean) "이 팩의 소리를 열 수 없습니다" else "Cannot open this pack’s sounds"
        screen.node(androidx.test.uiautomator.By.text(title))
        screen.node(androidx.test.uiautomator.By.text(if (korean) "소리 2개를 재생할 수 없습니다" else "2 sounds cannot be played"))
        assertTrue("The failure must stay open for retry", !activity.isFinishing && !activity.isDestroyed)
        screen.capture("audio-decoding-reasons-${locale.language}")
        screen.click(androidx.test.uiautomator.By.text(if (korean) "자세히 보기" else "Details"))
        screen.node(androidx.test.uiautomator.By.textContains("silence.wav"))
        screen.node(androidx.test.uiautomator.By.textContains("silence2.wav"))
        screen.click(androidx.test.uiautomator.By.text(if (korean) "닫기" else "Close"))
        screen.node(androidx.test.uiautomator.By.text(title))
        screen.click(androidx.test.uiautomator.By.text(if (korean) "팩 목록" else "Pack list"))
        screen.await("Pack list action did not close the failed pack") { activity.isFinishing || activity.isDestroyed }
    }

    private fun assertVisibleNotice(locale: Locale, expected: String) {
        failDuringDecode = false
        val captureName = "audio-output-failure"
        localeManager.applicationLocales = LocaleList(locale)
        screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
        val activity = screen.resumed()
        assertEquals(locale.language, screen.onMain { activity.resources.configuration.locales[0].language })
        // Toasts are noninteractive system windows, so read their displayed accessibility event
        // rather than looking for an app view. Capture immediately while that same toast is visible.
        val shownMessages = CopyOnWriteArrayList<String>()
        val shown = try {
            screen.instrumentation.uiAutomation.executeAndWaitForEvent(
                {
                    startGate.countDown()
                    screen.await("Playback must close after the engine fails") {
                        screen.onMain { activity.isFinishing || activity.isDestroyed }
                    }
                    screen.capture("$captureName-closed-${locale.language}")
                },
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
            screen.capture("$captureName-missing-${locale.language}")
            throw AssertionError("No displayed toast with exact text: $expected; received: $shownMessages", error)
        }
        screen.instrumentation.uiAutomation.waitForIdle(100, 1000)
        screen.capture("$captureName-visible-${locale.language}")
        // Android also appends the application label; the first entry is the displayed message.
        assertEquals(expected, shown.text.first().toString())
        assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/$captureName-${locale.language}-event.txt") {
            it.writeText(shown.toString())
            true
        })
        screen.await("Playback must still close after output startup fails") {
            screen.onMain { activity.isFinishing || activity.isDestroyed }
        }
    }
}
