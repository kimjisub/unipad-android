package com.kimjisub.launchpad.audio

import android.media.AudioFormat
import android.media.MediaFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class LegacyAacChannelOrderTest {
    // AudioSpecificConfig of audio-multichannel/quad.m4a, including its quad PCE.
    private val quad = byteArrayOf(0x12, 0x00, 0x05, 0x04, 0x04, 0x00, 0x21, 0x10,
        0x04, 0x4c, 0x61, 0x76, 0x63, 0x56, 0xe5.toByte(), 0x00)

    private fun remaps(
        mime: String = MediaFormat.MIMETYPE_AUDIO_AAC,
        name: String = "OMX.google.aac.decoder", channels: Int = 4, sdk: Int = 24,
        config: ByteBuffer? = ByteBuffer.wrap(quad),
    ) = decoderEmitsLegacyQuadAacOrder(mime, name, channels, sdk, config)

    @Test fun onlyTheOldGoogleAacDecoderWithAQuadPceIsRemapped() {
        for (sdk in 24..27) assertTrue("API $sdk", remaps(sdk = sdk))
        for (sdk in listOf(23, 28, 29, 35, 37)) assertFalse("API $sdk", remaps(sdk = sdk))
        for (name in listOf("c2.android.aac.decoder", "OMX.vendor.aac.decoder", "OMX.google.opus.decoder")) {
            assertFalse(name, remaps(name = name))
        }
        for (channels in listOf(1, 2, 3, 5, 6, 8)) assertFalse("$channels channels", remaps(channels = channels))
        assertFalse(remaps(mime = MediaFormat.MIMETYPE_AUDIO_OPUS))
        assertFalse(remaps(mime = MediaFormat.MIMETYPE_AUDIO_RAW))
    }

    @Test fun standardFourChannelAacAndOtherPceLayoutsAreLeftAlone() {
        assertFalse(remaps(config = ByteBuffer.wrap(byteArrayOf(0x12, 0x20)))) // AAC config 4: FC, FL, FR, BC.
        assertFalse(remaps(config = ByteBuffer.wrap(quad.copyOf().also { it[4] = 0x40 }))) // Side pair, no back pair.
        assertFalse(remaps(config = ByteBuffer.wrap(quad.copyOf().also { it[6] = 0x01 }))) // Single front element.
        assertFalse(remaps(config = ByteBuffer.wrap(quad.copyOf().also { it[0] = 0x2a }))) // HE-AAC.
    }

    @Test fun missingAndTruncatedConfigurationsNeverEnableTheWorkaround() {
        assertFalse(remaps(config = null))
        // The PCE ends after its four comment bytes; a following sync extension is optional.
        for (length in 0 until 13) {
            assertFalse("$length bytes", remaps(config = ByteBuffer.wrap(quad.copyOf(length))))
        }
        assertTrue(remaps(config = ByteBuffer.wrap(quad.copyOf(13))))
    }

    @Test fun checkingAConfigurationRespectsItsBufferWindowWithoutConsumingIt() {
        val config = ByteBuffer.wrap(byteArrayOf(99) + quad + byteArrayOf(99)).asReadOnlyBuffer()
        config.position(1)
        config.limit(1 + quad.size)
        assertTrue(remaps(config = config))
        assertEquals(1, config.position())
        assertEquals(1 + quad.size, config.limit())
    }

    @Test fun reorderingRestoresEverySpeakerAndLeavesUnusedSamplesAlone() {
        // Decoder order: FR, BL, FL, BR. Two frames plus an unused buffer tail.
        val pcm = shortArrayOf(2000, 3000, 1000, 4000, -20, -30, -10, -40, 99)
        val mask = legacyQuadAacOrderToChannelMask(pcm, 8)
        assertEquals(AudioFormat.CHANNEL_OUT_QUAD, mask)
        assertArrayEquals(shortArrayOf(1000, 2000, 3000, 4000, -10, -20, -30, -40, 99), pcm)
        assertArrayEquals(shortArrayOf(2000, 3000, -20, -30), stereoPcm(pcm, 4, mask, 8))
    }

    @Test(expected = IllegalArgumentException::class)
    fun anIncompleteFrameCannotBeReordered() { legacyQuadAacOrderToChannelMask(shortArrayOf(1, 2, 3)) }
}
