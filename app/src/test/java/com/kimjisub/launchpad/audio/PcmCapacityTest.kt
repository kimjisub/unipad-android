package com.kimjisub.launchpad.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmCapacityTest {
    @Test fun longStereoSoundNeedsOnlyOneExactlySizedArray() {
        val frames = 11_010_048L // 42 MiB of stereo 16-bit PCM.
        assertEquals(22_020_096, capacity(frames))
    }

    @Test fun forgedHugeDurationIsBoundedByPhysicalAudio() {
        assertEquals(88_200, capacity(44_100, durationUs = Long.MAX_VALUE))
        assertEquals(16_384, capacity(Long.MAX_VALUE, durationUs = Long.MAX_VALUE))
        assertEquals(16_384, capacity(600L * 384_000, 384_000, 8))
    }

    @Test fun unavailableOrEmptyLengthKeepsSmallFallback() {
        assertEquals(16_384, capacity(44_100, durationUs = 0))
        assertEquals(16_384, capacity(44_100, durationUs = -1))
        assertEquals(16_384, capacity(0))
        assertEquals(16_384, initialPcmCapacity(1_000_000, 44_100, 2,
            AudioInspection.Rejected(AudioExclusion.READ_FAILURE)))
    }

    @Test fun interleavedCapacityUsesPhysicalChannelsWithoutUpsamplingOrStereoPadding() {
        assertEquals(44_100, capacity(44_100, channels = 1))
        assertEquals(48_000 * 6, capacity(48_000, 48_000, 6))
    }

    @Test fun fractionalMicrosecondDurationStillFitsEveryFrame() {
        assertEquals(88_202, capacity(44_101))
    }

    @Test fun understatedDurationKeepsTheExistingGrowthSequenceAndPeak() {
        // Starting an arbitrary 40,000-sample array would make its first copy larger
        // than the old 32,768 + 65,536-sample peak. Keep the old power-of-two grid.
        val initial = capacity(44_100 * 10L, durationUs = 453_515)
        assertEquals(32_768, initial)
        for (samples in listOf(40_001, 65_536, 65_537, 131_073, 882_000)) {
            assertTrue("$samples samples", peak(initial, samples) <= peak(16_384, samples))
        }
    }

    @Test fun mismatchedOrInvalidLayoutDoesNotTrustDuration() {
        assertEquals(16_384, capacity(-1))
        assertEquals(16_384, capacity(100_000, rate = 0, durationUs = 1_000_000))
        assertEquals(16_384, capacity(100_000, channels = Int.MAX_VALUE))
        assertEquals(16_384, initialPcmCapacity(1_000_000, 48_000, 2,
            AudioInspection.Readable(AudioFileFacts(44_100, 2, 44_100))))
    }

    private fun capacity(frames: Long, rate: Int = 44_100, channels: Int = 2,
        durationUs: Long = if (frames in 0..230_400_000L) frames * 1_000_000 / rate else Long.MAX_VALUE) =
        initialPcmCapacity(durationUs, rate, channels,
            AudioInspection.Readable(AudioFileFacts(rate, channels, frames, "wav")))

    private fun peak(initial: Int, samples: Int): Int {
        var size = initial
        var peak = size
        while (size < samples) {
            peak = maxOf(peak, size * 3)
            size *= 2
        }
        return peak
    }
}
