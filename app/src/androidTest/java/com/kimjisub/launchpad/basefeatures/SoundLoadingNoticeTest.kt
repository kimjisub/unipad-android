package com.kimjisub.launchpad.basefeatures

import androidx.test.uiautomator.By
import com.kimjisub.launchpad.activity.PlayActivity
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

/** Real pack loading and visible notices, with ordinary WAV and unreadable bytes. */
class SoundLoadingNoticeTest {
    private lateinit var screen: FeatureScreen
    @Before fun setUp() {
        screen = FeatureScreen()
        screen.assertDedicatedWorkspace()
        screen.cleanPacks()
        screen.install()
    }
    @After fun tearDown() { if (::screen.isInitialized) screen.close() }

    @Test fun aPartialFailureShowsItsCountAndFileDetails() {
        File(screen.pack(), "sounds/broken.wav").writeText("not an audio file")
        File(screen.pack(), "keySound").writeText("1 1 1 silence.wav\n1 1 2 broken.wav\n")
        screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
        screen.await("Audio did not finish loading") { screen.onMain { !screen.vm().soundLoadingActive && screen.vm().uiLoaded } }
        screen.capture("audio-partial-before-details")
        screen.node(By.textContains("1 sound file cannot be read"))
        screen.click(By.text("Details"))
        screen.node(By.textContains("broken.wav"))
        screen.capture("audio-partial-details")
        screen.click(By.text("Close"))
        screen.node(By.desc(screen.text(com.kimjisub.launchpad.R.string.menu)))
    }

    @Test fun anAllFailedPackOffersRetryAndReturnsToTheSameSelection() {
        File(screen.pack(), "sounds/broken.wav").writeText("not an audio file")
        File(screen.pack(), "keySound").writeText("1 1 1 broken.wav\n")
        screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
        screen.capture("audio-all-failed-before-notice")
        screen.node(By.text("Cannot open this pack’s sounds"))
        screen.node(By.text("Retry"))
        screen.capture("audio-all-failed-notice")
        screen.click(By.text("Retry"))
        screen.node(By.text("Cannot open this pack’s sounds"))
        screen.click(By.text("Pack list"))
    }

    @Test fun retryReinspectsTheFileAndCanOpenTheRecoveredPack() {
        val file = File(screen.pack(), "sounds/broken.wav")
        file.writeText("not an audio file")
        File(screen.pack(), "keySound").writeText("1 1 1 broken.wav\n")
        screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
        screen.node(By.text("Cannot open this pack’s sounds"))
        org.junit.Assert.assertEquals("not an audio file", file.readText())
        // A user repairing the source is external to the app. Retry must not reuse the old rejection.
        file.writeBytes(File(screen.pack(), "sounds/silence.wav").readBytes())
        screen.click(By.text("Retry"))
        screen.node(By.desc(screen.text(com.kimjisub.launchpad.R.string.menu)))
        org.junit.Assert.assertEquals(1, screen.onMain { screen.vm().soundLoadReport!!.loadedFiles })
        org.junit.Assert.assertTrue(screen.onMain { screen.vm().soundLoadReport!!.failures.isEmpty() })
        screen.capture("audio-retry-recovered")
    }

    @Test fun aLargeFailureReportCanOpenDetailsWithoutBuildingEveryRow() {
        File(screen.pack(), "sounds/broken.wav").writeText("not audio")
        File(screen.pack(), "keySound").writeText("1 1 1 silence.wav\n1 1 2 broken.wav\n")
        screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
        screen.node(By.text("1 sound file cannot be read"))
        // Exercise report rendering independently of disk/decoder cost. This is not a physical
        // low-memory device measurement, and no production hook is added for the synthetic report.
        val failures = List(100_000) { index ->
            com.kimjisub.launchpad.unipack.runner.SoundRunner.FileFailure(
                File("missing-$index.wav"), com.kimjisub.launchpad.unipack.runner.SoundRunner.FailureReason.UNREADABLE)
        }
        screen.onMain {
            val vm = screen.vm()
            @Suppress("UNCHECKED_CAST")
            val state = vm.javaClass.getDeclaredField("soundLoadReport\$delegate").apply { isAccessible = true }.get(vm)
                as androidx.compose.runtime.MutableState<com.kimjisub.launchpad.unipack.runner.SoundRunner.LoadReport?>
            state.value = com.kimjisub.launchpad.unipack.runner.SoundRunner.LoadReport(100_001, 1, failures, 0)
        }
        screen.node(By.text("100000 sound files cannot be read"))
        screen.click(By.text("Details"))
        screen.node(By.textContains("missing-0.wav"))
        screen.capture("audio-large-failure-list")
        screen.click(By.text("Close"))
        screen.node(By.desc(screen.text(com.kimjisub.launchpad.R.string.menu)))
    }

    @Test fun multipleReasonsAreCountedOnceAndSurviveRecreation() = checkMixed("en")
    @Test fun koreanShowsTheApprovedReasonsAndReturnsFromDetails() = checkMixed("ko")

    private fun checkMixed(language: String) {
        val original = com.kimjisub.launchpad.AppLanguage.get()
        val decoded = java.util.concurrent.CopyOnWriteArrayList<String>()
        val engine = object : com.kimjisub.launchpad.unipack.runner.SoundRunner.Engine by com.kimjisub.launchpad.unipack.runner.SoundRunner.OboeEngine {
            override fun decode(file: File): com.kimjisub.launchpad.audio.OboeAudioEngine.DecodedAudio? {
                decoded += file.name
                // This verifies cause reporting. It does not reproduce physical device memory pressure.
                if (file.name == "memory.wav") throw OutOfMemoryError("injected allocation failure")
                if (file.name == "decode.wav") return null
                return com.kimjisub.launchpad.unipack.runner.SoundRunner.OboeEngine.decode(file)
            }
        }
        val binding = org.koin.dsl.module { single<com.kimjisub.launchpad.unipack.runner.SoundRunner.Engine> { engine } }
        org.koin.core.context.loadKoinModules(binding)
        try {
            com.kimjisub.launchpad.AppLanguage.set(language)
            val folder = File(screen.pack(), "sounds")
            File(folder, "broken.wav").writeText("not audio")
            for (name in listOf("decode.wav", "memory.wav")) File(folder, name).writeBytes(File(folder, "silence.wav").readBytes())
            wave("long.wav", 700, 60)
            File(screen.pack(), "keySound").writeText("1 1 1 silence.wav\n1 1 2 broken.wav\n1 1 3 long.wav\n1 1 4 decode.wav\n1 1 5 memory.wav\n1 1 6 broken.wav\n")
            val scenario = screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
            val summaries = if (language == "ko") listOf("너무 길거나 큰 소리 1개는 재생하지 않습니다", "소리 1개의 파일을 읽을 수 없습니다", "소리 1개를 재생할 수 없습니다", "기기 메모리가 부족해 소리 1개를 불러오지 못했습니다. 다른 앱을 닫고 팩을 다시 열어 보세요")
                else listOf("1 sound is too long or large and will not play", "1 sound file cannot be read", "1 sound cannot be played", "Device memory is low. 1 sound could not be loaded. Close other apps and reopen the pack.")
            summaries.forEach { screen.node(By.text(it)) }
            org.junit.Assert.assertEquals(listOf("silence.wav", "decode.wav", "memory.wav"), decoded.toList())
            org.junit.Assert.assertEquals(5, screen.onMain { screen.vm().soundLoadReport!!.usedFiles })
            org.junit.Assert.assertEquals(4, screen.onMain { screen.vm().soundLoadReport!!.failures.size })
            screen.capture("audio-multiple-$language")
            scenario.recreate()
            summaries.forEach { screen.node(By.text(it)) }
            screen.click(By.text(if (language == "ko") "자세히 보기" else "Details"))
            for (name in listOf("broken.wav", "long.wav", "decode.wav", "memory.wav")) screen.node(By.textContains(name))
            screen.capture("audio-multiple-details-$language")
            screen.click(By.text(if (language == "ko") "닫기" else "Close"))
            screen.node(By.desc(screen.onMain { screen.resumed().getString(com.kimjisub.launchpad.R.string.menu) }))
            scenario.recreate()
            org.junit.Assert.assertFalse(screen.onMain { screen.vm().soundLoadNoticeVisible })
        } finally {
            screen.close()
            org.koin.core.context.unloadKoinModules(binding)
            com.kimjisub.launchpad.AppLanguage.set(original)
        }
    }

    private fun wave(name: String, actualSeconds: Int, claimedSeconds: Int) {
        val size = actualSeconds * 48_000 * 4
        val header = java.nio.ByteBuffer.allocate(56).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(size + 48).put("WAVEfmt ".toByteArray()).putInt(16)
        header.putShort(1).putShort(2).putInt(48_000).putInt(192_000).putShort(4).putShort(16)
        header.put("fact".toByteArray()).putInt(4).putInt(claimedSeconds * 48_000)
        header.put("data".toByteArray()).putInt(size)
        java.io.RandomAccessFile(File(screen.pack(), "sounds/$name"), "rw").use {
            it.write(header.array()); it.setLength(56L + size)
        }
    }

    @Test fun physicalLengthWinsOverFalseDurationBeforeTheRealDecoderRuns() {
        wave("claimed-700.wav", 5, 700)
        wave("claimed-60.wav", 700, 60)
        File(screen.pack(), "keySound").writeText("1 1 1 claimed-700.wav\n1 1 2 claimed-60.wav\n")
        val decoded = java.util.concurrent.CopyOnWriteArrayList<Pair<String, Int>>()
        val engine = object : com.kimjisub.launchpad.unipack.runner.SoundRunner.Engine by com.kimjisub.launchpad.unipack.runner.SoundRunner.OboeEngine {
            override fun decode(file: File) = com.kimjisub.launchpad.unipack.runner.SoundRunner.OboeEngine.decode(file)?.also {
                decoded += file.name to it.numFrames
            }
        }
        val binding = org.koin.dsl.module { single<com.kimjisub.launchpad.unipack.runner.SoundRunner.Engine> { engine } }
        org.koin.core.context.loadKoinModules(binding)
        try {
            screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
            screen.node(By.text("1 sound is too long or large and will not play"))
            org.junit.Assert.assertEquals(listOf("claimed-700.wav" to 240_000), decoded.toList())
            org.junit.Assert.assertEquals(480_000L, screen.onMain { screen.vm().soundLoadReport!!.reservedCost })
            org.junit.Assert.assertTrue(screen.onMain { screen.vm().unipack.soundTable!![0][0][0]!![0].id >= 0 })
            screen.capture("audio-physical-duration")
            screen.click(By.text("Close"))
            screen.node(By.desc(screen.text(com.kimjisub.launchpad.R.string.menu)))
        } finally {
            screen.close()
            org.koin.core.context.unloadKoinModules(binding)
        }
    }

    @Test fun aLightOnlyPackOpensWithoutASoundFailureNotice() {
        File(screen.pack(), "keySound").writeText("")
        screen.openPlay()
        screen.capture("audio-light-only")
        org.junit.Assert.assertFalse(screen.has(By.text("Cannot open this pack’s sounds")))
    }
}
