package com.kimjisub.launchpad.audio

import android.media.AudioFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
}
