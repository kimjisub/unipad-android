package com.kimjisub.launchpad.audio

import android.media.AudioFormat
import android.media.MediaFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StereoPcmTest {
    @Test fun nativeQuadAndSurroundMasksBecomeOutputMasks() {
        assertEquals(AudioFormat.CHANNEL_OUT_QUAD, decoderChannelMask(4, 0x33))
        assertEquals(AudioFormat.CHANNEL_OUT_5POINT1, decoderChannelMask(6, 0x3f))
    }

    @Test fun javaQuadAndSurroundMasksAreNotShiftedAgain() {
        assertEquals(AudioFormat.CHANNEL_OUT_QUAD, decoderChannelMask(4, AudioFormat.CHANNEL_OUT_QUAD))
        assertEquals(AudioFormat.CHANNEL_OUT_5POINT1, decoderChannelMask(6, AudioFormat.CHANNEL_OUT_5POINT1))
    }

    @Test fun nativeAndJavaSideSurroundMasksKeepTheirSpeakerPositions() {
        val mask = AudioFormat.CHANNEL_OUT_STEREO or AudioFormat.CHANNEL_OUT_FRONT_CENTER or
            AudioFormat.CHANNEL_OUT_LOW_FREQUENCY or AudioFormat.CHANNEL_OUT_SIDE_LEFT or AudioFormat.CHANNEL_OUT_SIDE_RIGHT
        assertEquals(mask, decoderChannelMask(6, mask))
        assertEquals(mask, decoderChannelMask(6, mask ushr 2))
    }

    @Test fun aMaskWithoutBothFrontSpeakersFallsBackToTheChannelCount() {
        assertEquals(0, decoderChannelMask(4, 0xd4))
        assertArrayEquals(shortArrayOf(0, 5000), stereoPcm(shortArrayOf(0, 0, 0, 10000), 4, decoderChannelMask(4, 0xd4)))
    }

    @Test fun anUnsupportedOrMismatchedDecoderMaskUsesTheChannelCount() {
        assertEquals(0, decoderChannelMask(6, Int.MIN_VALUE))
        assertEquals(0, decoderChannelMask(6, AudioFormat.CHANNEL_OUT_QUAD))
        assertEquals(0, decoderChannelMask(6, 0))
    }

    @Test fun surroundDecoderMasksKeepTheCenterInsteadOfUsingDiscreteChannels() {
        for (mask in listOf(0x3f, AudioFormat.CHANNEL_OUT_5POINT1)) {
            assertArrayEquals(shortArrayOf(7071, 7071), stereoPcm(shortArrayOf(0, 0, 10000, 0, 0, 0), 6, decoderChannelMask(6, mask)))
        }
    }

    @Test fun aTrailingStereoSampleKeepsTheExistingBuffer() {
        val pcm = shortArrayOf(1, 2, 3)
        assertSame(pcm, stereoPcm(pcm, 2))
    }

    @Test fun monoSamplesStayUnchanged() {
        val pcm = shortArrayOf(1, -2, Short.MIN_VALUE, Short.MAX_VALUE)
        assertSame(pcm, stereoPcm(pcm, 1))
    }

    @Test fun stereoSamplesStayUnchanged() {
        val pcm = shortArrayOf(1000, -2000, 3000, -4000)
        assertSame(pcm, stereoPcm(pcm, 2))
    }

    @Test fun quadPreservesFramesAndSeparatesTheLeftAndRightSpeakers() {
        assertArrayEquals(shortArrayOf(3000, 5000, -3000, -5000), stereoPcm(
            shortArrayOf(2000, 4000, 4000, 6000, -2000, -4000, -4000, -6000), 4))
    }

    @Test fun quadSideSpeakersUseTheSameStereoMix() {
        val mask = AudioFormat.CHANNEL_OUT_STEREO or AudioFormat.CHANNEL_OUT_SIDE_LEFT or AudioFormat.CHANNEL_OUT_SIDE_RIGHT
        assertArrayEquals(shortArrayOf(3000, 5000), stereoPcm(shortArrayOf(2000, 4000, 4000, 6000), 4, mask))
    }

    @Test fun surroundCenterReachesBothChannelsAndLfeIsOmitted() {
        assertArrayEquals(shortArrayOf(7071, 7071), stereoPcm(shortArrayOf(0, 0, 10000, 20000, 0, 0), 6))
    }

    @Test fun surroundSpeakersReachTheirOwnSide() {
        assertArrayEquals(shortArrayOf(7071, 0, 0, -7071), stereoPcm(shortArrayOf(0, 0, 0, 0, 10000, 0, 0, 0, 0, 0, 0, -10000), 6))
    }

    @Test fun sideSurroundMaskUsesTheDecodedSpeakerOrder() {
        val mask = AudioFormat.CHANNEL_OUT_STEREO or AudioFormat.CHANNEL_OUT_FRONT_CENTER or
            AudioFormat.CHANNEL_OUT_LOW_FREQUENCY or AudioFormat.CHANNEL_OUT_SIDE_LEFT or AudioFormat.CHANNEL_OUT_SIDE_RIGHT
        assertArrayEquals(shortArrayOf(8071, 9071), stereoPcm(shortArrayOf(1000, 2000, 0, 20000, 10000, 10000), 6, mask))
    }

    @Test fun aThreeChannelFileKeepsItsCenter() {
        assertArrayEquals(shortArrayOf(8071, 9071), stereoPcm(shortArrayOf(1000, 2000, 10000), 3))
    }

    @Test fun fiveChannelsUseTheCanonicalCenterThenRearOrder() {
        assertArrayEquals(shortArrayOf(8071, 9071), stereoPcm(shortArrayOf(1000, 2000, 4000, 6000, 6000), 5))
    }

    @Test fun sevenChannelsIncludeTheBackCenter() {
        assertArrayEquals(shortArrayOf(5000, 5000), stereoPcm(shortArrayOf(0, 0, 0, 0, 0, 0, 10000), 7))
    }

    @Test fun eightChannelsIncludeRearAndSideSpeakers() {
        assertArrayEquals(shortArrayOf(7071, 7071), stereoPcm(shortArrayOf(0, 0, 0, 0, 4000, 6000, 6000, 4000), 8))
    }

    @Test fun overfullMixSaturatesInsteadOfWrapping() {
        assertArrayEquals(shortArrayOf(Short.MAX_VALUE, Short.MAX_VALUE, Short.MIN_VALUE, Short.MIN_VALUE), stereoPcm(
            shortArrayOf(32767, 32767, 32767, 0, 32767, 32767, -32768, -32768, -32768, 0, -32768, -32768), 6))
    }

    @Test fun anUnknownLayoutUsesDiscreteChannelsWithoutChangingTheDuration() {
        assertArrayEquals(shortArrayOf(1000, 2000, -1000, -2000), stereoPcm(shortArrayOf(1000, 2000, 3000, -1000, -2000, -3000), 3, 7))
    }

    @Test(expected = IllegalArgumentException::class)
    fun anIncompleteFrameIsRejected() { stereoPcm(shortArrayOf(1, 2, 3), 4) }

    @Test fun opusSurroundInVorbisOrderKeepsItsCenterAndRearsAndDropsLfe() {
        // Left, center, right, rear left, rear right, LFE.
        val pcm = shortArrayOf(1000, 10000, 2000, 3000, 4000, 20000)
        val mask = vorbisOrderToChannelMask(pcm, 6)
        assertEquals(AudioFormat.CHANNEL_OUT_5POINT1, mask)
        assertArrayEquals(shortArrayOf(1000, 2000, 10000, 20000, 3000, 4000), pcm)
        assertArrayEquals(shortArrayOf(10192, 11899), stereoPcm(pcm, 6, mask))
    }

    @Test fun opusCenterOnlySurroundReachesBothSidesEqually() {
        val pcm = shortArrayOf(0, 4096, 0, 0, 0, 0, 0, -4096, 0, 0, 0, 0)
        assertArrayEquals(shortArrayOf(2896, 2896, -2896, -2896), stereoPcm(pcm, 6, vorbisOrderToChannelMask(pcm, 6)))
    }

    @Test fun everyVorbisLayoutPutsLeftAndRightOnTheirOwnSide() {
        for (channels in 3..8) {
            val left = ShortArray(channels).also { it[0] = 10000 }
            val right = ShortArray(channels).also { it[if (channels == 4) 1 else 2] = 10000 }
            val center = ShortArray(channels).also { it[if (channels == 4) 2 else 1] = 10000 }
            val leftMix = stereoPcm(left, channels, vorbisOrderToChannelMask(left, channels))
            val rightMix = stereoPcm(right, channels, vorbisOrderToChannelMask(right, channels))
            assertTrue("$channels left", leftMix[0] > 0 && leftMix[1].toInt() == 0)
            assertTrue("$channels right", rightMix[0].toInt() == 0 && rightMix[1] > 0)
            // Quad has rears where the other layouts have the center: rear left stays left.
            val centerMix = stereoPcm(center, channels, vorbisOrderToChannelMask(center, channels))
            if (channels == 4) assertArrayEquals(shortArrayOf(5000, 0), centerMix)
            else assertArrayEquals("$channels center", shortArrayOf(7071, 7071), centerMix)
        }
    }

    @Test fun vorbisOrderedSevenAndEightChannelsUseSideAndBackSpeakers() {
        val seven = shortArrayOf(0, 0, 0, 0, 0, 10000, 0)
        assertArrayEquals(shortArrayOf(5000, 5000), stereoPcm(seven, 7, vorbisOrderToChannelMask(seven, 7)))
        val eight = shortArrayOf(0, 0, 0, 10000, 0, 0, -10000, 20000)
        assertArrayEquals(shortArrayOf(7071, -7071), stereoPcm(eight, 8, vorbisOrderToChannelMask(eight, 8)))
    }

    @Test fun vorbisReorderingCoversOnlyTheGivenSamples() {
        val pcm = shortArrayOf(1, 2, 3, 9)
        vorbisOrderToChannelMask(pcm, 3, 3)
        assertArrayEquals(shortArrayOf(1, 3, 2, 9), pcm)
    }

    @Test fun onlyGoogleSoftwareOpusDecodersAreRemapped() {
        val opus = MediaFormat.MIMETYPE_AUDIO_OPUS
        assertTrue(decoderEmitsVorbisOrder(opus, "c2.android.opus.decoder", 6))
        assertTrue(decoderEmitsVorbisOrder(opus, "OMX.google.opus.decoder", 3))
        assertTrue(decoderEmitsVorbisOrder(opus, "c2.android.opus.decoder", 8))
        assertFalse("vendor decoder", decoderEmitsVorbisOrder(opus, "c2.vendor.opus.decoder", 6))
        assertFalse("stereo", decoderEmitsVorbisOrder(opus, "c2.android.opus.decoder", 2))
        assertFalse("beyond Vorbis layouts", decoderEmitsVorbisOrder(opus, "c2.android.opus.decoder", 9))
        assertFalse("other codec", decoderEmitsVorbisOrder(MediaFormat.MIMETYPE_AUDIO_AAC, "c2.android.aac.decoder", 6))
    }
}
