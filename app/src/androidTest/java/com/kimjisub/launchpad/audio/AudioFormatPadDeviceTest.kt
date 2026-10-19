package com.kimjisub.launchpad.audio

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.ChainObserver
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Observes and forwards every operation to the real decoder and native engine; no fake PCM or IDs. */
@RunWith(AndroidJUnit4::class)
class AudioFormatPadDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    @get:Rule
    val dir = TemporaryFolder(instrumentation.targetContext.filesDir)

    @Test fun wavReachesItsPad() = checkPad("tone.wav")
    @Test fun mp3ReachesItsPad() = checkPad("tone.mp3")
    @Test fun oggReachesItsPad() = checkPad("tone.ogg")
    @Test fun m4aReachesItsPad() = checkPad("tone.m4a")
    @Test fun bareMp3NamedWavReachesItsPad() = checkPad("mp3-named.wav")
    @Test fun taggedMp3NamedWavReachesItsPad() = checkPad("mp3-id3-named.wav")

    private fun checkPad(name: String) {
        File(dir.root, "info").writeText("title=Audio pad\nproducerName=Test\nbuttonX=8\nbuttonY=8\nchain=1\n")
        File(dir.root, "keySound").writeText("1 2 3 $name\n")
        File(dir.root, "sounds").mkdir()
        val file = File(dir.root, "sounds/$name")
        instrumentation.context.assets.open("audio-formats/$name").use { input -> file.outputStream().use(input::copyTo) }
        val pack = UniPackFolder(dir.root).load().loadDetail()
        assertNull(pack.errorDetail)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val loaded = CountDownLatch(1)
        var error: Throwable? = null
        val engine = ObservedEngine()
        ActivityScenario.launch(MainActivity::class.java).use {
            val runner = SoundRunner(pack, ChainObserver(), object : SoundRunner.LoadingListener {
                override fun onStart(soundCount: Int) { assertEquals(1, soundCount) }
                override fun onProgressTick() = Unit
                override fun onEnd() { loaded.countDown() }
                override fun onException(throwable: Throwable) { error = throwable; loaded.countDown() }
            }, scope, engine)
            try {
                assertTrue("loading timed out", loaded.await(30, TimeUnit.SECONDS))
                assertNull("loading failed", error)
                assertEquals(file.canonicalPath, engine.file?.canonicalPath)
                assertTrue("native sound was not loaded", engine.soundId >= 0)
                assertSame("decoded PCM must reach native load unchanged", engine.decoded, engine.loaded)
                assertTrue(engine.loaded!!.pcm.any { sample -> sample.toInt() != 0 })
                runner.soundOn(0, 0)
                assertEquals("empty pad must stay silent", 0, engine.played.size)
                runner.soundOn(1, 2)
                assertEquals(listOf(engine.soundId), engine.played)
                assertTrue("native engine refused the voice", engine.voice > 0)
                runner.stopAll()
            } finally {
                runner.destroy()
                scope.cancel()
            }
        }
    }

    private class ObservedEngine : SoundRunner.Engine by SoundRunner.OboeEngine {
        var file: File? = null
        var decoded: OboeAudioEngine.DecodedAudio? = null
        var loaded: OboeAudioEngine.DecodedAudio? = null
        var soundId = -1
        var voice = 0
        val played = mutableListOf<Int>()
        override fun decode(file: File): OboeAudioEngine.DecodedAudio? {
            this.file = file
            return SoundRunner.OboeEngine.decode(file).also { decoded = it }
        }
        override fun load(decoded: OboeAudioEngine.DecodedAudio): Int {
            loaded = decoded
            return SoundRunner.OboeEngine.load(decoded).also { soundId = it }
        }
        override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int {
            played.add(soundId)
            assertEquals(1f, volumeL, 0f)
            assertEquals(1f, volumeR, 0f)
            assertEquals(0, loop)
            return SoundRunner.OboeEngine.play(soundId, volumeL, volumeR, loop).also { voice = it }
        }
    }
}
