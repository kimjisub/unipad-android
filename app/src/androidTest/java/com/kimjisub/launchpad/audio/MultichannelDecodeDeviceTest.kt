package com.kimjisub.launchpad.audio

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.kimjisub.launchpad.TestCaptures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Uses the device decoder, with each speaker isolated in a separate PCM frame. */
@RunWith(AndroidJUnit4::class)
class MultichannelDecodeDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    @get:Rule val dir = TemporaryFolder(instrumentation.targetContext.cacheDir)

    @Test fun quadWavBecomesStereoWithoutChangingFrameCount() = checkSpeakers(4, 0x33)

    @Test fun surroundWavBecomesStereoWithoutChangingFrameCount() = checkSpeakers(6, 0x3f)

    private fun checkSpeakers(channels: Int, waveMask: Int) {
        val frames = 4096
        val pcm = ShortArray(frames * channels) { i ->
            val frame = i / channels
            if (i % channels == (frame / 256) % channels) {
                if (frame < frames / 2) 10000 else -10000
            } else 0
        }
        val name = if (channels == 4) "quad" else "surround"
        val source = dir.newFile("$name.wav")
        writeWave(source, pcm, channels, waveMask)
        val decoded = checkNotNull(OboeAudioEngine.decodeOnly(source)) { "$name did not decode" }
        val observed = "channels=${decoded.channels}\nframes=${decoded.numFrames}\nrate=${decoded.sampleRate}\n" +
            (0 until channels).joinToString("\n") { c ->
                val start = c * 256 * decoded.channels
                "speaker=$c samples=${decoded.pcm.slice(start until start + decoded.channels)}"
            } + "\n"
        assertTrue(TestCaptures.save(device, "audio-downmix/$name-observed.txt") { it.writeText(observed); true })
        assertTrue(TestCaptures.save(device, "audio-downmix/$name-source.wav") { source.copyTo(it, overwrite = true); true })
        assertTrue(TestCaptures.save(device, "audio-downmix/$name-decoded.wav") {
            writeWave(it, decoded.pcm, decoded.channels, if (decoded.channels == 2) 3 else waveMask); true
        })
        assertEquals("$name must reach the stereo mixer as stereo", 2, decoded.channels)
        assertEquals(frames, decoded.numFrames)
        assertEquals(44100, decoded.sampleRate)
        assertEquals(frames * 2, decoded.pcm.size)
        for (frame in 0 until frames) {
            val offset = frame * channels
            val left: Double
            val right: Double
            if (channels == 4) {
                left = 0.5 * (pcm[offset].toInt() + pcm[offset + 2])
                right = 0.5 * (pcm[offset + 1].toInt() + pcm[offset + 3])
            } else {
                left = pcm[offset] + sqrt(0.5) * (pcm[offset + 2].toInt() + pcm[offset + 4])
                right = pcm[offset + 1] + sqrt(0.5) * (pcm[offset + 2].toInt() + pcm[offset + 5])
            }
            assertEquals("$name left frame $frame", left.roundToInt(), decoded.pcm[frame * 2].toInt())
            assertEquals("$name right frame $frame", right.roundToInt(), decoded.pcm[frame * 2 + 1].toInt())
        }
    }

    private fun writeWave(file: File, pcm: ShortArray, channels: Int, mask: Int) {
        val bytes = ByteBuffer.allocate(68 + pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(bytes.capacity() - 8).put("WAVEfmt ".toByteArray())
        bytes.putInt(40).putShort(0xfffe.toShort()).putShort(channels.toShort()).putInt(44100)
        bytes.putInt(44100 * channels * 2).putShort((channels * 2).toShort()).putShort(16)
        bytes.putShort(22).putShort(16).putInt(mask)
        bytes.putInt(1).putShort(0).putShort(0x10).put(byteArrayOf(0x80.toByte(), 0, 0, 0xaa.toByte(), 0, 0x38, 0x9b.toByte(), 0x71))
        bytes.put("data".toByteArray()).putInt(pcm.size * 2)
        pcm.forEach(bytes::putShort)
        file.writeBytes(bytes.array())
    }
}
