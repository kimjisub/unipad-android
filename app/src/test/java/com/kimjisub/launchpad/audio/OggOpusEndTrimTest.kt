package com.kimjisub.launchpad.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OggOpusEndTrimTest {
    private fun inspected(frames: Long = 14_448, format: String = "opus", rate: Int = 48_000) =
        AudioInspection.Readable(AudioFileFacts(rate, 2, frames, format))

    @Test fun trimsOnlyTrailingPadding() {
        assertEquals(14_448, oggOpusEndFrames(15_048, 48_000, inspected()))
    }

    @Test fun keepsExactOrShorterOutput() {
        assertEquals(14_448, oggOpusEndFrames(14_448, 48_000, inspected()))
        assertEquals(14_000, oggOpusEndFrames(14_000, 48_000, inspected()))
    }

    @Test fun acceptsOneMaximumPacketButReportsAnythingLarger() {
        assertEquals(14_448, oggOpusEndFrames(20_208, 48_000, inspected()))
        val mismatches = mutableListOf<Long>()
        assertEquals(20_209, oggOpusEndFrames(20_209, 48_000, inspected(), mismatches::add))
        assertEquals(listOf(5761L), mismatches)
    }

    @Test fun leavesOtherFormatsAndRatesUnchanged() {
        for (format in listOf("mp3", "ogg", "wav", "m4a", "")) {
            assertEquals(format, 15_048, oggOpusEndFrames(15_048, 48_000, inspected(format = format)))
        }
        assertEquals(15_048, oggOpusEndFrames(15_048, 24_000, inspected()))
        assertEquals(15_048, oggOpusEndFrames(15_048, 48_000, inspected(rate = 24_000)))
        assertEquals(15_048, oggOpusEndFrames(15_048, 48_000, AudioInspection.Rejected(AudioExclusion.READ_FAILURE)))
    }

    @Test fun handlesEmptyAndUnrepresentableLengthsWithoutWrapping() {
        assertEquals(0, oggOpusEndFrames(312, 48_000, inspected(0)))
        for (frames in listOf(-1L, Long.MAX_VALUE, Int.MAX_VALUE.toLong() + 1)) {
            assertEquals(15_048, oggOpusEndFrames(15_048, 48_000, inspected(frames)))
        }
        val mismatches = mutableListOf<Long>()
        assertEquals(14_448, oggOpusEndFrames(14_448, 48_000, inspected(), mismatches::add))
        assertTrue(mismatches.isEmpty())
    }
}
