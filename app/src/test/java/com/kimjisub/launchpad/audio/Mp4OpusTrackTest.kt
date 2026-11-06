package com.kimjisub.launchpad.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Packet positions and OpusHead bytes are FFmpeg's (`ffprobe -show_packets`, the stream extradata). */
class Mp4OpusTrackTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun track(name: String): Mp4OpusTrack = AudioData(fixture(name)).use(::mp4OpusTrack)

    private fun fixture(name: String): File = temporary.newFile(name).also { file ->
        javaClass.getResourceAsStream("/audio-safety/$name")!!.use { input -> file.outputStream().use(input::copyTo) }
    }

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    private val opusHead = "OpusHead".toByteArray()

    @Test fun monoTrackListsEveryPacketWithAFamilyZeroHeader() {
        val track = track("libopus-48000-1.m4a")
        assertArrayEquals(opusHead + bytes(1, 1, 0x38, 0x01, 0x80, 0xbb, 0, 0, 0, 0, 0), track.head)
        assertEquals(1, track.channels)
        assertEquals(312, track.preSkip)
        assertEquals(101, track.sizes.size)
        assertEquals(44L, track.offsets.first()); assertEquals(307, track.sizes.first())
        assertEquals(18974L, track.offsets.last()); assertEquals(318, track.sizes.last())
        assertEquals(19248, track.sizes.sum())
        assertEquals(96960, track.frames.sum())
    }

    @Test fun surroundTrackCarriesItsStreamCountsAndChannelMapping() {
        val track = track("libopus-48000-6.m4a")
        assertArrayEquals(opusHead + bytes(1, 6, 0x38, 0x01, 0x80, 0xbb, 0, 0, 0, 0, 1, 4, 2, 0, 4, 1, 2, 3, 5), track.head)
        assertEquals(6, track.channels)
        assertEquals(312, track.preSkip)
        assertEquals(101, track.sizes.size)
        assertEquals(44L, track.offsets.first()); assertEquals(300, track.sizes.first())
        assertEquals(19362L, track.offsets.last()); assertEquals(311, track.sizes.last())
        assertEquals(19629, track.sizes.sum())
    }

    @Test fun packetsFollowEachOtherInTheMediaData() {
        val track = track("libopus-48000-6.m4a")
        for (i in 1 until track.offsets.size) assertEquals(track.offsets[i - 1] + track.sizes[i - 1], track.offsets[i])
    }

    @Test(expected = InvalidAudio::class)
    fun anAacTrackIsNotOpus() { track("tone.m4a") }

    @Test(expected = InvalidAudio::class)
    fun aTableThatOverrunsTheMediaDataIsRejectedAsInInspection() { track("invalid-stsz-tone.m4a") }
}
