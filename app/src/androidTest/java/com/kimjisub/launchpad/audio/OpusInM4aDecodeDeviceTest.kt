package com.kimjisub.launchpad.audio

import android.media.MediaExtractor
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Decodes the shared Opus-in-MP4 fixtures with the device's own extractor and codec.
 *
 * Both are two-second 48 kHz 440 Hz sines encoded by libopus (see `audio-safety/README.md`):
 * the mono file carries the tone in its only channel, the 5.1 file in its center channel.
 * The shared inspection reserves 101 Opus packets of 960 frames (96,960) for them, so the
 * decoded sound must fit in that reservation; FFmpeg emits 96,648 frames after trimming.
 */
@RunWith(AndroidJUnit4::class)
class OpusInM4aDecodeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val dir = TemporaryFolder(instrumentation.targetContext.cacheDir)

    @Test
    fun decodesMonoOpusInM4a() {
        val decoded = decode("libopus-48000-1.m4a")
        assertEquals("mono channels", 1, decoded.channels)
        assertTone("mono", decoded, side = 0, peak = MONO_PEAK)
    }

    @Test
    fun decodesSurroundOpusInM4aWithItsCenterOnBothSides() {
        val decoded = decode("libopus-48000-6.m4a")
        assertEquals("5.1 must reach the stereo mixer as stereo", 2, decoded.channels)
        assertTone("5.1 left", decoded, side = 0, peak = MONO_PEAK * sqrt(0.5))
        assertTone("5.1 right", decoded, side = 1, peak = MONO_PEAK * sqrt(0.5))
    }

    /**
     * Every Opus packet replaced by bytes no decoder accepts (a code 3 header claiming 63 frames).
     * Android 9 and earlier never reach a decoder because the MP4 inspection rejects the packets;
     * later versions hand them to the decoder. Either way only this sound may be lost.
     */
    @Test
    fun brokenOpusPacketsInM4aDropOnlyThatSound() {
        val name = "libopus-48000-6.m4a"
        val bytes = instrumentation.context.assets.open("audio-safety/$name").use { it.readBytes() }
        // The fixture's only mdat box starts at byte 36; its packets follow the 8-byte header.
        assertEquals("mdat", String(bytes, 40, 4, Charsets.US_ASCII))
        val mdatEnd = 36 + ByteBuffer.wrap(bytes, 36, 4).int
        bytes.fill(0xff.toByte(), 44, mdatEnd)
        val file = File(dir.root, "broken-$name").apply { writeBytes(bytes) }
        val decoded = OboeAudioEngine.decodeOnly(file)
        Log.i(TAG, "broken $name decoded=${decoded?.let { "${it.numFrames} frames" }} on API ${Build.VERSION.SDK_INT}")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) assertNull("$name must be dropped, not decoded", decoded)
    }

    private fun decode(name: String): OboeAudioEngine.DecodedAudio {
        val file = File(dir.root, name)
        instrumentation.context.assets.open("audio-safety/$name").use { input ->
            file.outputStream().use(input::copyTo)
        }
        val decoded = OboeAudioEngine.decodeOnly(file)
            ?: throw AssertionError("$name did not decode; the device extractor sees ${tracks(file)}")
        Log.i(TAG, "$name rate=${decoded.sampleRate} channels=${decoded.channels} frames=${decoded.numFrames}")
        assertEquals("$name sample rate", 48_000, decoded.sampleRate)
        assertTrue(
            "$name decoded ${decoded.numFrames} frames, outside $DECODED_FRAMES",
            decoded.numFrames in DECODED_FRAMES,
        )
        return decoded
    }

    /** What the device's MP4 reader makes of the file, to tell an unread track from a codec failure. */
    private fun tracks(file: File): String {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            (0 until extractor.trackCount).map { extractor.getTrackFormat(it).toString() }.toString()
        } catch (e: Exception) {
            "no tracks: $e"
        } finally {
            extractor.release()
        }
    }

    private fun assertTone(label: String, decoded: OboeAudioEngine.DecodedAudio, side: Int, peak: Double) {
        // The middle half skips the Opus pre-skip and the end trimming.
        val middle = (decoded.numFrames / 4 until decoded.numFrames * 3 / 4)
            .map { decoded.pcm[it * decoded.channels + side] }
        val observedPeak = middle.maxOf { abs(it.toInt()) }
        val crossings = middle.indices.drop(1).count { (middle[it - 1] < 0) != (middle[it] < 0) }
        val hertz = crossings / 2.0 / (middle.size.toDouble() / decoded.sampleRate)
        Log.i(TAG, "$label peak=$observedPeak hz=$hertz")
        assertEquals("$label peak", peak, observedPeak.toDouble(), peak * 0.15)
        assertEquals("$label frequency", TONE_HZ, hertz, 5.0)
    }

    private companion object {
        const val TAG = "OpusInM4aDecode"
        const val TONE_HZ = 440.0

        /** FFmpeg's sine source peaks at 1/8 of full scale (-18 dBFS). */
        const val MONO_PEAK = 32768.0 / 8

        /** At least the two seconds minus one packet, at most the shared reservation. */
        val DECODED_FRAMES = 95_040..96_960
    }
}
