package com.kimjisub.launchpad.audio

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * `vorbis-end-trim/short-tail.ogg` is a 44.1 kHz mono sine whose 141 audio packets all use the
 * 256-frame short block (long blocks are 2,048). Their overlap gives 17,920 frames; the final
 * page ends the stream at 17,640, trimming 280 frames: more than the last short block, less
 * than a long one. These tests change only that final granule and the page CRC.
 */
class VorbisEndTrimTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun shortBlockTailsMayTrimUpToOneLongBlock() {
        for (trim in listOf(0L, 256L, 257L, 280L, 2_048L)) {
            val facts = AudioFileFacts(44_100, 1, CALCULATED - trim, "ogg")
            assertEquals("trim $trim", AudioInspection.Readable(facts), AudioFileInspector.inspect(withTrim(trim)))
        }
    }

    @Test fun theUnchangedFixtureReservesItsTrimmedLength() {
        val result = AudioFileInspector.inspect(fixture())
        assertEquals(AudioInspection.Readable(AudioFileFacts(44_100, 1, 17_640, "ogg")), result)
        assertEquals(AudioCost((17_640L * 48_000 + 44_099) / 44_100 * 2), AudioSafety.cost((result as AudioInspection.Readable).facts))
    }

    @Test fun trimsBeyondOneLongBlockOrPastThePacketsAreStillRejected() {
        for (trim in listOf(-1L, 2_049L, 6_000L)) {
            assertEquals("trim $trim", AudioInspection.Rejected(AudioExclusion.INVALID_FORMAT), AudioFileInspector.inspect(withTrim(trim)))
        }
    }

    @Test fun aDamagedPageChecksumIsStillRejected() {
        val bytes = fixtureBytes()
        bytes[bytes.lastIndex] = (bytes[bytes.lastIndex].toInt() xor 1).toByte()
        assertEquals(AudioInspection.Rejected(AudioExclusion.INVALID_FORMAT), AudioFileInspector.inspect(write(bytes)))
    }

    private fun fixtureBytes(): ByteArray = javaClass.getResourceAsStream("/vorbis-end-trim/short-tail.ogg")!!.use { it.readBytes() }

    private fun fixture(): File = write(fixtureBytes())

    private fun write(bytes: ByteArray): File = temporary.newFile().apply { writeBytes(bytes) }

    private fun withTrim(trim: Long): File {
        val bytes = fixtureBytes()
        var at = 0
        while (at < bytes.size) {
            val segments = bytes[at + 26].toInt() and 255
            var size = 27 + segments
            repeat(segments) { size += bytes[at + 27 + it].toInt() and 255 }
            if (bytes[at + 5].toInt() and 4 != 0) {
                val granule = CALCULATED - trim
                repeat(8) { bytes[at + 6 + it] = (granule ushr (8 * it)).toByte() }
                writeCrc(bytes, at, size)
            }
            at += size
        }
        return write(bytes)
    }

    private fun writeCrc(bytes: ByteArray, at: Int, size: Int) {
        for (i in 22..25) bytes[at + i] = 0
        var crc = 0
        for (i in 0 until size) {
            crc = crc xor ((bytes[at + i].toInt() and 255) shl 24)
            repeat(8) { crc = (crc shl 1) xor if (crc < 0) 0x04c11db7 else 0 }
        }
        repeat(4) { bytes[at + 22 + it] = (crc ushr (8 * it)).toByte() }
    }

    private companion object {
        const val CALCULATED = 17_920L
    }
}
