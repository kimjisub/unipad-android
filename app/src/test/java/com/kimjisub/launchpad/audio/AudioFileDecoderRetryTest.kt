package com.kimjisub.launchpad.audio

import android.media.MediaCodec
import android.media.MediaFormat
import com.kimjisub.launchpad.tool.Log
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioFileDecoderRetryTest {
    @get:Rule val dir = TemporaryFolder()

    private lateinit var file: File
    private val errors = mutableListOf<String>()
    private val pauses = mutableListOf<Long>()
    private val codecs = mutableListOf<FakeCodec>()
    private var opened = 0

    @Before
    fun setUp() {
        // Not a recognised container: the PCM buffer starts at the fallback capacity.
        file = dir.newFile("s_02.ogg").apply { writeText("decoded by the fake decoder") }
        mockkObject(Log)
        every { Log.err(any()) } answers { errors += firstArg<String>() }
        every { Log.err(any(), any()) } answers { errors += firstArg<String>() }
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun aDecoderReleasedBeforeConfigureIsReplacedByANewOne() {
        val decoder = decoder(FakeCodec(failConfigure = true), FakeCodec())

        val decoded = decoder.decode(file, { false })!!

        assertEquals(PACKETS * FRAMES_PER_PACKET, decoded.numFrames)
        assertArrayEquals(cleanPcm(), decoded.pcm.copyOf(PACKETS * SAMPLES_PER_PACKET))
        assertEquals(2, codecs.size)
        assertTrue(codecs.all { it.released })
        assertEquals("the file starts over on the new decoder", 2, opened)
        assertEquals(listOf(AudioFileDecoder.RETRY_PAUSE_MS), pauses)
        assertTrue(errors.toString(), errors[0].startsWith("decoder setup failed on attempt 1 of 3, retrying: s_02.ogg: java.lang.IllegalStateException"))
        assertEquals("decode recovered: s_02.ogg on attempt 2 of 3", errors[1])
        assertEquals(2, errors.size)
    }

    @Test
    fun cleanupFailureDoesNotHideTheDecoderFailureThatNeedsARetry() {
        val decoder = decoder(
            FakeCodec(failConfigure = true, releaseFailure = IllegalStateException("decoder already released")),
            FakeCodec(),
        )

        val decoded = decoder.decode(file, { false })!!

        assertArrayEquals(cleanPcm(), decoded.pcm.copyOf(PACKETS * SAMPLES_PER_PACKET))
        assertEquals(2, codecs.size)
        assertTrue(codecs.all { it.released })
        assertEquals(listOf(AudioFileDecoder.RETRY_PAUSE_MS), pauses)
        assertTrue(errors.any { it == "decode recovered: s_02.ogg on attempt 2 of 3" })
    }

    @Test
    fun aDecoderLostMidFileDropsItsPartialPcmAndCountsOnlyTheNewBuffer() {
        val broken = FakeCodec(failAtQueue = 2, failure = mockk<MediaCodec.CodecException>(relaxed = true), fill = BROKEN_SAMPLE)
        val decoder = decoder(broken, FakeCodec())
        val allocations = mutableListOf<Pair<Int, Int>>()

        val decoded = decoder.decode(file, { false }) { old, new -> allocations += old to new }!!

        assertEquals("the first decoder had produced PCM before it failed", 1, broken.outputs)
        assertEquals(listOf(0 to FALLBACK_CAPACITY), allocations)
        assertArrayEquals(cleanPcm(), decoded.pcm.copyOf(PACKETS * SAMPLES_PER_PACKET))
        assertTrue(decoded.pcm.none { it == BROKEN_SAMPLE })
        assertTrue(broken.released)
        assertTrue(errors.toString(), errors[0].startsWith("decoder input/output failed on attempt 1 of 3, retrying: s_02.ogg"))
        assertEquals("decode recovered: s_02.ogg on attempt 2 of 3", errors[1])
    }

    @Test
    fun threeBrokenDecodersGiveUpWithTheStageOfTheLastFailure() {
        val decoder = decoder(FakeCodec(failConfigure = true), FakeCodec(failConfigure = true), FakeCodec(failAtQueue = 1))
        val allocations = mutableListOf<Pair<Int, Int>>()

        assertNull(decoder.decode(file, { false }) { old, new -> allocations += old to new })

        assertEquals(3, codecs.size)
        assertTrue(codecs.all { it.released })
        assertEquals(listOf(AudioFileDecoder.RETRY_PAUSE_MS, 2 * AudioFileDecoder.RETRY_PAUSE_MS), pauses)
        assertEquals(emptyList<Pair<Int, Int>>(), allocations)
        assertEquals("decode failed: s_02.ogg (decoder input/output failed on attempt 3 of 3)", errors.last())
        assertEquals(3, errors.size)
    }

    @Test
    fun fileProblemsAreTriedOnce() {
        fun assertTriedOnce(name: String, decoder: AudioFileDecoder) {
            opened = 0
            codecs.clear()
            pauses.clear()
            assertNull(name, decoder.decode(file, { false }))
            assertTrue(name, opened <= 1)
            assertTrue(name, codecs.size <= 1)
            assertEquals(name, emptyList<Long>(), pauses)
        }
        assertTriedOnce("no audio track", AudioFileDecoder(openSource = { opened++; null }, createCodec = ::nextCodec, pause = { pauses += it }))
        assertTriedOnce("unreadable file", AudioFileDecoder(openSource = { opened++; throw IOException("unreadable") }, createCodec = ::nextCodec, pause = { pauses += it }))
        assertTriedOnce("no type", decoder(FakeCodec(), mime = null))
        assertTriedOnce("no frames", decoder(FakeCodec(), packets = 0))
        assertTriedOnce("stalled after the last packet", decoder(FakeCodec(answerEndOfStream = false)))
        assertTriedOnce("no decoder for the type", AudioFileDecoder(openSource = ::openFake, createCodec = { throw IOException("no decoder") }, pause = { pauses += it }))
        assertTriedOnce("extractor state", AudioFileDecoder(openSource = { opened++; FakeSource(PACKETS, readFailure = IllegalStateException("extractor")) }, createCodec = ::nextCodec, pause = { pauses += it }))
        assertTriedOnce("extractor timestamp", AudioFileDecoder(openSource = { opened++; FakeSource(PACKETS, timeFailure = IllegalStateException("extractor timestamp")) }, createCodec = ::nextCodec, pause = { pauses += it }))
        assertTrue(errors.toString(), errors.contains("decode stalled after EOS: s_02.ogg"))
        assertTrue(errors.none { it.contains("retrying") })
    }

    @Test
    fun leavingTheScreenAfterADecoderFailureStopsWithoutRetrying() {
        var leaving = false
        val decoder = decoder(FakeCodec(failConfigure = true, onFailure = { leaving = true }), FakeCodec())

        assertThrows(OboeAudioEngine.DecodeInterruptedException::class.java) { decoder.decode(file, { leaving }) }

        assertEquals(1, codecs.size)
        assertTrue(codecs[0].released)
        assertEquals(emptyList<Long>(), pauses)
    }

    @Test
    fun leavingTheScreenOnTheLastFailureStillReportsInterruption() {
        var leaving = false
        val decoder = decoder(
            FakeCodec(failConfigure = true),
            FakeCodec(failConfigure = true),
            FakeCodec(failAtQueue = 1, onFailure = { leaving = true }),
        )

        assertThrows(OboeAudioEngine.DecodeInterruptedException::class.java) { decoder.decode(file, { leaving }) }

        assertEquals(3, codecs.size)
        assertTrue(codecs.all { it.released })
        assertEquals(listOf(AudioFileDecoder.RETRY_PAUSE_MS, 2 * AudioFileDecoder.RETRY_PAUSE_MS), pauses)
        assertTrue(errors.none { it.startsWith("decode failed") })
    }

    @Test
    fun leavingTheScreenDuringTheRetryReleasesTheNewDecoder() {
        val decoder = decoder(FakeCodec(failConfigure = true), FakeCodec())
        // Answers true from the first poll inside the decode loop of the second attempt.
        val interrupted = { codecs.size == 2 && codecs[1].started }

        assertThrows(OboeAudioEngine.DecodeInterruptedException::class.java) { decoder.decode(file, interrupted) }

        assertEquals(2, codecs.size)
        assertTrue(codecs.all { it.released })
        assertTrue(errors.none { it.startsWith("decode failed") })
    }

    private fun decoder(vararg script: FakeCodec, mime: String? = "audio/vorbis", packets: Int = PACKETS): AudioFileDecoder {
        val queue = ArrayDeque(script.toList())
        return AudioFileDecoder(
            openSource = { opened++; FakeSource(packets, mime) },
            createCodec = { queue.removeFirst().also { codecs += it }.codec },
            pause = { pauses += it },
        )
    }

    private fun nextCodec(@Suppress("UNUSED_PARAMETER") mime: String): MediaCodec = FakeCodec().also { codecs += it }.codec

    private fun openFake(@Suppress("UNUSED_PARAMETER") file: File): AudioPacketSource {
        opened++
        return FakeSource(PACKETS)
    }

    private fun cleanPcm() = ShortArray(PACKETS * SAMPLES_PER_PACKET) { (it / SAMPLES_PER_PACKET * 100 + it % SAMPLES_PER_PACKET + 1).toShort() }

    private class FakeSource(
        private val packets: Int,
        mime: String? = "audio/vorbis",
        private val readFailure: Exception? = null,
        private val timeFailure: Exception? = null,
    ) : AudioPacketSource {
        private var index = 0
        override val format: MediaFormat = mockk<MediaFormat>(relaxed = true).also {
            every { it.getString(MediaFormat.KEY_MIME) } returns mime
            every { it.getInteger(MediaFormat.KEY_SAMPLE_RATE) } returns 44_100
            every { it.getInteger(MediaFormat.KEY_CHANNEL_COUNT) } returns CHANNELS
            every { it.containsKey(any()) } returns false
            every { it.getByteBuffer(any()) } returns null
        }
        override fun readPacket(buffer: ByteBuffer): Int {
            readFailure?.let { throw it }
            if (index >= packets) return -1
            buffer.clear()
            buffer.put(index.toByte())
            return 1
        }
        override val packetTimeUs: Long get() {
            timeFailure?.let { throw it }
            return index * 1_000L
        }
        override fun advance() { index++ }
        override fun close() {}
    }

    /**
     * A decoder that answers one output buffer per queued packet. [failAtQueue] counts queued
     * packets from 1; [fill] replaces every sample it emits, so leftovers of a broken attempt show.
     */
    private class FakeCodec(
        private val failConfigure: Boolean = false,
        private val failAtQueue: Int = 0,
        private val failure: IllegalStateException = IllegalStateException("configure() is valid only at Initialized state; currently at Released state"),
        private val fill: Short? = null,
        private val answerEndOfStream: Boolean = true,
        private val onFailure: () -> Unit = {},
        private val releaseFailure: IllegalStateException? = null,
    ) {
        var released = false
        var started = false
        var outputs = 0
        private var queued = 0
        private val pending = ArrayDeque<Int>()
        private var endQueued = false
        private var output: ByteBuffer = ByteBuffer.allocate(0)

        val codec: MediaCodec = mockk<MediaCodec>(relaxed = true).also { codec ->
            every { codec.configure(any<MediaFormat>(), any<android.view.Surface>(), any<android.media.MediaCrypto>(), any<Int>()) } answers {
                if (failConfigure) fail()
            }
            every { codec.start() } answers { started = true }
            every { codec.name } returns "c2.android.vorbis.decoder"
            every { codec.dequeueInputBuffer(any()) } returns 0
            every { codec.getInputBuffer(any()) } answers { ByteBuffer.allocate(8) }
            every { codec.queueInputBuffer(any(), any(), any(), any(), any()) } answers {
                val flags = arg<Int>(4)
                if (flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    endQueued = true
                } else {
                    if (++queued == failAtQueue) fail()
                    pending += queued - 1
                }
            }
            every { codec.dequeueOutputBuffer(any(), any()) } answers {
                val info = firstArg<MediaCodec.BufferInfo>()
                val packet = pending.removeFirstOrNull()
                when {
                    packet != null -> {
                        output = pcmFor(packet)
                        info.offset = 0
                        info.size = output.capacity()
                        info.flags = 0
                        outputs++
                        0
                    }
                    endQueued && answerEndOfStream -> {
                        output = ByteBuffer.allocate(0)
                        info.offset = 0
                        info.size = 0
                        info.flags = MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        0
                    }
                    else -> MediaCodec.INFO_TRY_AGAIN_LATER
                }
            }
            every { codec.getOutputBuffer(any()) } answers { output }
            every { codec.release() } answers {
                released = true
                releaseFailure?.let { throw it }
            }
        }

        private fun fail(): Nothing {
            onFailure()
            throw failure
        }

        private fun pcmFor(packet: Int): ByteBuffer {
            val bytes = ByteBuffer.allocate(SAMPLES_PER_PACKET * 2).order(ByteOrder.nativeOrder())
            repeat(SAMPLES_PER_PACKET) { bytes.putShort(fill ?: (packet * 100 + it + 1).toShort()) }
            bytes.clear()
            return bytes
        }
    }

    private companion object {
        const val PACKETS = 3
        const val CHANNELS = 2
        const val FRAMES_PER_PACKET = 4
        const val SAMPLES_PER_PACKET = FRAMES_PER_PACKET * CHANNELS
        const val FALLBACK_CAPACITY = 16_384
        const val BROKEN_SAMPLE: Short = 9_999
    }
}
