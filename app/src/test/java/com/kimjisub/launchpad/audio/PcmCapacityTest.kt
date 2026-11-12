package com.kimjisub.launchpad.audio

import java.nio.ShortBuffer
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Assert.*
import org.junit.Test

class PcmCapacityTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun aacInspectionKeepsAdmissionAndReservesTheWholeFinalPacket() {
        val file = temporary.newFile()
        javaClass.getResourceAsStream("/audio-safety/tone.m4a")!!.use { input -> file.outputStream().use(input::copyTo) }
        val result = AudioFileInspector.inspectForDecode(file)
        assertEquals(AudioFileInspector.inspect(file), result.inspection)
        assertEquals(14_254L, (result.inspection as AudioInspection.Readable).facts.frames)
        assertEquals(14_336L, result.fullPacketFrames)
    }

    @Test fun untrimmedAacOutputFitsWithoutGrowingACopy() {
        val inspection = AudioInspection.Readable(AudioFileFacts(44_100, 1, 14_254, "m4a"))
        val allocations = mutableListOf<Pair<Int, Int>>()
        val buffer = PcmBuffer(initialPcmCapacity(44_100, 1, inspection, 14_336)) { old, new -> allocations += old to new }
        append(buffer, 14_336, 1_024)
        assertEquals(listOf(0 to 14_336), allocations)
        assertEquals(14_336, buffer.sampleCount)
    }

    @Test fun longStereoSoundNeedsOnlyOneExactlySizedArray() {
        assertEquals(22_020_096, capacity(11_010_048))
    }

    @Test fun mp3PaddingDoesNotForceTheSmallFallback() {
        assertEquals(16_128, capacity(16_128, channels = 1, format = "mp3"))
    }

    @Test fun excessivePhysicalClaimsCannotEnlargeTheReservation() {
        assertEquals(88_200, capacity(44_100))
        assertEquals(16_384, capacity(Long.MAX_VALUE))
        assertEquals(16_384, capacity(600L * 384_000, 384_000, 8))
    }

    @Test fun nonAacTracksKeepTheExistingReservation() {
        val file = temporary.newFile()
        javaClass.getResourceAsStream("/audio-safety/libopus-48000-1.m4a")!!.use { input -> file.outputStream().use(input::copyTo) }
        val result = AudioFileInspector.inspectForDecode(file)
        assertEquals(AudioFileInspector.inspect(file), result.inspection)
        assertNull(result.fullPacketFrames)
        assertEquals(96_960, initialPcmCapacity(48_000, 1, result.inspection, result.fullPacketFrames))
    }

    @Test fun wholePacketBoundCannotBypassTheSingleSoundBudget() {
        val inspection = AudioInspection.Readable(AudioFileFacts(44_100, 1, 14_254, "m4a"))
        assertEquals(16_384, initialPcmCapacity(44_100, 1, inspection, Long.MAX_VALUE))
    }

    @Test fun unavailableOrEmptyPhysicalLengthKeepsSmallFallback() {
        assertEquals(16_384, capacity(0))
        assertEquals(16_384, initialPcmCapacity(44_100, 2,
            AudioInspection.Rejected(AudioExclusion.READ_FAILURE)))
    }

    @Test fun layoutAndAdmissionAreCheckedBeforeMultiplication() {
        assertEquals(44_100, capacity(44_100, channels = 1))
        assertEquals(288_000, capacity(48_000, 48_000, 6))
        assertEquals(16_384, capacity(-1))
        assertEquals(16_384, capacity(100_000, rate = 0))
        assertEquals(16_384, capacity(100_000, channels = Int.MAX_VALUE))
        assertEquals(16_384, initialPcmCapacity(48_000, 2,
            AudioInspection.Readable(AudioFileFacts(44_100, 2, 44_100))))
    }

    @Test fun trimmedOpusGranulesCannotPredictDecoderCapacity() {
        assertEquals(16_384, capacity(144_000, 48_000, 6, "opus"))
    }

    @Test fun physicalCapacityFitsWithoutAnyGrowingCopy() {
        val allocations = mutableListOf<Pair<Int, Int>>()
        val buffer = PcmBuffer(capacity(50_000)) { old, new -> allocations += old to new }
        append(buffer, 100_000, 50_000)
        assertEquals(listOf(0 to 100_000), allocations)
        assertEquals(100_000, buffer.sampleCount)
    }

    @Test fun opusOverflowNeverExceedsThePreviousGrowthPeak() {
        val allocations = mutableListOf<Pair<Int, Int>>()
        val buffer = PcmBuffer(capacity(144_000, 48_000, 6, "opus")) { old, new -> allocations += old to new }
        append(buffer, 867_888, 5_760)
        assertEquals(1_572_864, allocations.maxOf { (old, new) -> old + new })
        assertEquals(1_048_576, buffer.pcm!!.size)
    }

    @Test fun growthUsesRealPacketSizesAndPreservesSamples() {
        val allocations = mutableListOf<Pair<Int, Int>>()
        val buffer = PcmBuffer(2) { old, new -> allocations += old to new }
        buffer.append(ShortBuffer.wrap(shortArrayOf(1, 2)))
        buffer.append(ShortBuffer.wrap(shortArrayOf(3, 4, 5)))
        assertEquals(listOf(0 to 2, 2 to 5), allocations)
        assertArrayEquals(shortArrayOf(1, 2, 3, 4, 5), buffer.pcm)
    }

    @Test fun noLargeArrayIsAllocatedUntilFirstPcmOutput() {
        val allocations = mutableListOf<Pair<Int, Int>>()
        val buffer = PcmBuffer(100_000) { old, new -> allocations += old to new }
        buffer.append(ShortBuffer.allocate(0))
        assertTrue(allocations.isEmpty())
        assertNull(buffer.pcm)
        buffer.append(ShortBuffer.wrap(shortArrayOf(7)))
        assertEquals(listOf(0 to 100_000), allocations)
        assertEquals(7, buffer.pcm!![0].toInt())
    }

    private fun capacity(frames: Long, rate: Int = 44_100, channels: Int = 2, format: String = "wav") =
        initialPcmCapacity(rate, channels, AudioInspection.Readable(AudioFileFacts(rate, channels, frames, format)))

    private fun append(buffer: PcmBuffer, total: Int, packet: Int) {
        while (buffer.sampleCount < total) {
            buffer.append(ShortBuffer.allocate(minOf(packet, total - buffer.sampleCount)))
        }
    }
}
