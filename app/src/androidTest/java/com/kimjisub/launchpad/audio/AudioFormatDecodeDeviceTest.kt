package com.kimjisub.launchpad.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Decodes each sound format packs use with the device's own codecs.
 *
 * Every fixture in `assets/audio-formats` is the same 0.3 s, 440 Hz, half-scale mono tone at
 * 44.1 kHz, so a decoder that returns silence, noise or the wrong rate fails the same checks.
 * `mp3-named.wav` is an MP3 (with an ID3v2 tag) under a `.wav` name, as some official packs ship.
 */
@RunWith(AndroidJUnit4::class)
class AudioFormatDecodeDeviceTest {
    private lateinit var dir: File

    @Before
    fun copyFixtures() {
        val context = InstrumentationRegistry.getInstrumentation()
        dir = kotlin.io.path.createTempDirectory(context.targetContext.cacheDir.toPath(), "audio-formats-").toFile()
        val assets = context.context.assets
        for (name in assets.list(FIXTURES).orEmpty()) {
            assets.open("$FIXTURES/$name").use { input -> File(dir, name).outputStream().use(input::copyTo) }
        }
    }

    @After
    fun removeFixtures() {
        dir.deleteRecursively()
    }

    @Test
    fun decodesPcmWav() = assertTone("tone.wav")

    @Test
    fun decodesMp3() = assertTone("tone.mp3")

    @Test
    fun decodesAacInM4a() = assertTone("tone.m4a")

    @Test
    fun decodesMp3NamedWavByItsContent() = assertTone("mp3-named.wav")

    private fun assertTone(name: String) {
        val file = File(dir, name)
        assertTrue("Fixture missing: $name", file.isFile)
        val decoded = OboeAudioEngine.decodeOnly(file) ?: throw AssertionError("$name did not decode")

        assertEquals("$name sample rate", SAMPLE_RATE, decoded.sampleRate)
        assertEquals("$name channels", 1, decoded.channels)
        val seconds = decoded.numFrames.toDouble() / decoded.sampleRate
        assertTrue("$name lasts $seconds s", seconds in 0.28..0.40)

        // The middle half skips the encoder delay and padding lossy formats add at the ends.
        val middle = decoded.pcm.copyOfRange(decoded.numFrames / 4, decoded.numFrames * 3 / 4)
        val peak = middle.maxOf { abs(it.toInt()) }
        assertTrue("$name peak $peak is not the half-scale tone", peak in 12_000..20_000)
        val crossings = middle.indices.drop(1).count { (middle[it - 1] < 0) != (middle[it] < 0) }
        val hertz = crossings / 2.0 / (middle.size.toDouble() / decoded.sampleRate)
        assertTrue("$name plays at $hertz Hz", abs(hertz - TONE_HZ) < 10)
    }

    private companion object {
        const val FIXTURES = "audio-formats"
        const val SAMPLE_RATE = 44_100
        const val TONE_HZ = 440.0
    }
}
