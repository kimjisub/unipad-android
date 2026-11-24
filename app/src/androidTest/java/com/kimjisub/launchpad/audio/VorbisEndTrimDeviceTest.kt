package com.kimjisub.launchpad.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * A Vorbis stream ending in short blocks may trim more than its last block
 * (`vorbis-end-trim/README.md`). The device's own decoder must play it for exactly the
 * length the inspection reserved, not drop it and not keep the trimmed padding.
 */
@RunWith(AndroidJUnit4::class)
class VorbisEndTrimDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val dir = TemporaryFolder(instrumentation.targetContext.cacheDir)

    @Test
    fun shortBlockTailDecodesToItsInspectedLength() {
        val file = dir.newFile("short-tail.ogg")
        instrumentation.context.assets.open("vorbis-end-trim/short-tail.ogg").use { input ->
            file.outputStream().use(input::copyTo)
        }
        val facts = (AudioFileInspector.inspect(file) as AudioInspection.Readable).facts
        assertEquals(17_640L, facts.frames)

        val decoded = OboeAudioEngine.decodeOnly(file) ?: throw AssertionError("short-tail.ogg did not decode")
        assertEquals(44_100, decoded.sampleRate)
        assertEquals(1, decoded.channels)
        assertEquals(facts.frames.toInt(), decoded.numFrames)
        val peak = (decoded.numFrames / 4 until decoded.numFrames * 3 / 4).maxOf { abs(decoded.pcm[it].toInt()) }
        assertTrue("peak $peak is silence", peak > 1_000)
    }
}
