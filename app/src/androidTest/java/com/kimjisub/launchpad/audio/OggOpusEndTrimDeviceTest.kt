package com.kimjisub.launchpad.audio

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class OggOpusEndTrimDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule val dir = TemporaryFolder(instrumentation.targetContext.cacheDir)

    @Test fun decodesMonoToItsGranuleLength() = assertLength(1)
    @Test fun decodesStereoToItsGranuleLength() = assertLength(2)
    @Test fun decodesSurroundToItsGranuleLength() = assertLength(6)

    private fun assertLength(channels: Int) {
        // A misleading extension must not enable or disable the correction.
        val file = File(dir.root, "tone-$channels.wav")
        instrumentation.context.assets.open("ogg-opus-end/tone-$channels.opus").use { input ->
            file.outputStream().use(input::copyTo)
        }
        val facts = (AudioFileInspector.inspect(file) as AudioInspection.Readable).facts
        assertEquals("opus", facts.format)
        assertEquals(14_448L, facts.frames)
        val decoded = requireNotNull(OboeAudioEngine.decodeOnly(file))
        Log.i("OggOpusEndTrim", "inputChannels=$channels rate=${decoded.sampleRate} frames=${decoded.numFrames}")
        assertEquals(48_000, decoded.sampleRate)
        assertEquals(minOf(channels, 2), decoded.channels)
        assertEquals(facts.frames, decoded.numFrames.toLong())
        assertTrue(decoded.pcm.size >= decoded.numFrames * decoded.channels)
        // The tail correction must retain real audio at the start and end of the tone.
        for (frames in listOf(0 until 480, 13_968 until 14_448)) {
            val peak = frames.maxOf { abs(decoded.pcm[it * decoded.channels].toInt()) }
            assertTrue("tone missing at $frames", peak > 500)
        }
    }
}
