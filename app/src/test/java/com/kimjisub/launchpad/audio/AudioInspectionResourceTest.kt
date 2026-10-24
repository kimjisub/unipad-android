package com.kimjisub.launchpad.audio

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class AudioInspectionResourceTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun excessiveTopLevelBoxesReturnReadFailure() {
        val file = temporary.newFile()
        file.outputStream().use { out ->
            javaClass.getResourceAsStream("/audio-safety/tone.m4a")!!.use { it.copyTo(out) }
            repeat(4097) { out.write(box("free", byteArrayOf())) }
        }
        assertEquals(AudioInspection.Rejected(AudioExclusion.READ_FAILURE), AudioFileInspector.inspect(file))
    }

    @Test fun excessiveChunkTableReturnsReadFailureBeforeAllocatingExtents() {
        val file = temporary.newFile()
        file.writeBytes(pcmChunks(262_145))
        assertEquals(AudioInspection.Rejected(AudioExclusion.READ_FAILURE), AudioFileInspector.inspect(file))
    }

    @Test fun boundedChunkTablesStillReadEveryPhysicalSample() {
        val file = temporary.newFile()
        file.writeBytes(pcmChunks(4096))
        assertEquals(AudioInspection.Readable(AudioFileFacts(48_000, 1, 4096, "m4a")), AudioFileInspector.inspect(file))
    }

    @Test fun repeatedUnrelatedMpegHeadersReturnReadFailureWithinTheProbeBudget() {
        val file = temporary.newFile()
        file.outputStream().use { out ->
            javaClass.getResourceAsStream("/audio-safety/tone.mp3")!!.use { it.copyTo(out) }
            val header = byteArrayOf(0xff.toByte(), 0xe7.toByte(), 0xe8.toByte(), 0xc0.toByte(), 0)
            repeat(1024) { out.write(header) }
        }
        assertEquals(AudioInspection.Rejected(AudioExclusion.READ_FAILURE), AudioFileInspector.inspect(file))
    }

    private fun words(vararg values: Int) = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out -> values.forEach(out::writeInt) }
    }.toByteArray()

    private fun box(type: String, data: ByteArray): ByteArray = words(data.size + 8) + type.toByteArray() + data

    // One 16-bit PCM sample per chunk, all backed by actual mdat bytes.
    private fun pcmChunks(count: Int): ByteArray {
        val entry = ByteArray(28)
        entry[7] = 1 // data reference index
        entry[17] = 1 // channel count
        entry[19] = 16 // sample precision
        words(48_000 shl 16).copyInto(entry, 24)
        val stsd = box("stsd", words(0, 1) + box("sowt", entry))
        val stts = box("stts", words(0, 1, count, 1))
        val stsz = box("stsz", words(0, 2, count))
        val stsc = box("stsc", words(0, 1, 1, 1, 1))
        val offsets = ByteArrayOutputStream()
        DataOutputStream(offsets).use { out ->
            out.writeInt(0); out.writeInt(count)
            repeat(count) { out.writeInt(8 + it * 2) }
        }
        val stbl = box("stbl", stsd + stts + stsz + stsc + box("stco", offsets.toByteArray()))
        val mdhd = box("mdhd", words(0, 0, 0, 48_000, count))
        val hdlr = box("hdlr", words(0, 0) + "soun".toByteArray())
        val moov = box("moov", box("trak", box("mdia", mdhd + hdlr + box("minf", stbl))))
        return box("mdat", ByteArray(count * 2)) + moov
    }
}
