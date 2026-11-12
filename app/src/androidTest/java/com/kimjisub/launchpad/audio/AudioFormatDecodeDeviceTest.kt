package com.kimjisub.launchpad.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * Decodes each sound format packs use with the device's own codecs.
 *
 * Every fixture in `assets/audio-formats` is the same 0.3 s, 440 Hz, half-scale tone at
 * 44.1 kHz, so a decoder that returns silence, noise or the wrong rate fails the same checks.
 * OGG is stereo; the other fixtures are mono.
 * `mp3-named.wav` is a bare MP3 (frames from the first byte, no tag) under a `.wav` name, as
 * some official packs ship; `mp3-id3-named.wav` is the same with an ID3v2 tag in front.
 */
@RunWith(AndroidJUnit4::class)
class AudioFormatDecodeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val dir = TemporaryFolder(instrumentation.targetContext.cacheDir)

    @Before
    fun copyFixtures() {
        val assets = instrumentation.context.assets
        for (name in assets.list(FIXTURES).orEmpty()) {
            assets.open("$FIXTURES/$name").use { input -> dir.newFile(name).outputStream().use(input::copyTo) }
        }
    }

    @Test
    fun decodesPcmWav() = assertTone("tone.wav")

    @Test
    fun pcmWavUsesAnExactlySizedArray() {
        val file = File(dir.root, "tone.wav")
        val facts = (AudioFileInspector.inspect(file) as AudioInspection.Readable).facts
        val decoded = OboeAudioEngine.decodeOnly(file) ?: throw AssertionError("WAV did not decode")
        assertEquals((facts.frames * facts.channels).toInt(), decoded.pcm.size)
        assertEquals(facts.frames.toInt(), decoded.numFrames)
    }

    @Test
    fun mp3UsesPhysicalCapacityForAllSpellings() {
        for (name in listOf("tone.mp3", "mp3-named.wav", "mp3-id3-named.wav")) {
            val file = File(dir.root, name)
            val facts = (AudioFileInspector.inspect(file) as AudioInspection.Readable).facts
            val allocations = mutableListOf<Pair<Int, Int>>()
            val decoded = OboeAudioEngine.decodeObservingPcm(file) { old, new -> allocations += old to new }
                ?: throw AssertionError("$name did not decode")
            val physical = (facts.frames * facts.channels).toInt()
            assertEquals(name, listOf(0 to physical), allocations)
            assertEquals(name, physical, decoded.pcm.size)
            assertTrue(name, decoded.numFrames * decoded.channels <= physical)
        }
    }

    @Test
    fun opusDoesNotExceedLegacyPcmPeak() {
        val file = dir.newFile("surround.opus")
        instrumentation.context.assets.open("audio-multichannel/surround.opus").use { input ->
            file.outputStream().use(input::copyTo)
        }
        val allocations = mutableListOf<Pair<Int, Int>>()
        val decoded = OboeAudioEngine.decodeObservingPcm(file) { old, new -> allocations += old to new }
            ?: throw AssertionError("Opus did not decode")
        assertEquals(0 to 16_384, allocations.first())
        val growthPeak = allocations.maxOf { (old, new) -> old + new }
        // The raw six-channel array also coexists with the final stereo down-mix.
        val downmixPeak = allocations.last().second + decoded.pcm.size
        assertTrue("Opus PCM peak: growth=$growthPeak downmix=$downmixPeak",
            maxOf(growthPeak, downmixPeak) <= 1_572_864)
        assertEquals(48_000, decoded.sampleRate)
        assertEquals(2, decoded.channels)
    }

    @Test
    fun aacReservesWholePacketsOnOldAndNewDecoders() {
        val fixtures = listOf(
            Triple("audio-formats/tone.m4a", 14_336, 16_384),
            Triple("audio-output/aac.m4a", 98_304, 196_608),
            Triple("audio-multichannel/quad.m4a", 360_448, 786_432),
            Triple("audio-multichannel/surround.m4a", 804_864, 1_572_864),
        )
        for ((index, fixture) in fixtures.withIndex()) {
            val (asset, capacity, legacyPeak) = fixture
            val file = dir.newFile("aac-$index.m4a")
            instrumentation.context.assets.open(asset).use { input -> file.outputStream().use(input::copyTo) }
            val facts = (AudioFileInspector.inspect(file) as AudioInspection.Readable).facts
            val allocations = mutableListOf<Pair<Int, Int>>()
            val decoded = OboeAudioEngine.decodeObservingPcm(file) { old, new -> allocations += old to new }
                ?: throw AssertionError("$asset did not decode")
            assertEquals(asset, listOf(0 to capacity), allocations)
            assertTrue(asset, decoded.numFrames.toLong() * facts.channels <= capacity)
            val peak = if (facts.channels > 2) capacity + decoded.pcm.size else capacity
            assertTrue("$asset PCM peak $peak exceeds $legacyPeak", peak <= legacyPeak)
        }
    }

    @Test
    fun decodesMp3() = assertTone("tone.mp3")

    @Test
    fun decodesOggVorbis() = assertTone("tone.ogg")

    @Test
    fun decodesAacInM4a() = assertTone("tone.m4a")

    @Test
    fun decodesMp3NamedWavByItsContent() = assertTone("mp3-named.wav")

    @Test
    fun decodesTaggedMp3NamedWavByItsContent() = assertTone("mp3-id3-named.wav")

    private fun assertTone(name: String) {
        val file = File(dir.root, name)
        assertTrue("Fixture missing: $name", file.isFile)
        val decoded = OboeAudioEngine.decodeOnly(file) ?: throw AssertionError("$name did not decode")

        assertEquals("$name sample rate", SAMPLE_RATE, decoded.sampleRate)
        assertEquals("$name channels", if (name == "tone.ogg") 2 else 1, decoded.channels)
        val seconds = decoded.numFrames.toDouble() / decoded.sampleRate
        assertTrue("$name lasts $seconds s", seconds in 0.28..0.40)

        // The middle half skips the encoder delay and padding lossy formats add at the ends.
        val middle = (decoded.numFrames / 4 until decoded.numFrames * 3 / 4)
            .map { decoded.pcm[it * decoded.channels] }
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
