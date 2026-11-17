package com.kimjisub.launchpad.tool

import android.media.MediaFormat
import android.media.MediaPlayer
import com.kimjisub.launchpad.audio.AudioPacketSource
import com.kimjisub.launchpad.audio.openAudioPacketSource
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException

class SoundDurationReaderTest {
    private val broken = File("broken.wav")
    private val valid = File("valid.wav")
    private val source = mockk<AudioPacketSource>(relaxed = true)
    private val format = mockk<MediaFormat>()

    @Before
    fun setUp() {
        mockkStatic(::openAudioPacketSource)
        mockkObject(Log)
        every { Log.err(any(), any()) } returns Unit
        every { source.format } returns format
        // A duration read must never enter the player, including its vendor event handler.
        mockkConstructor(MediaPlayer::class)
        every { anyConstructed<MediaPlayer>().prepare() } throws IllegalStateException("Do not use a player for metadata")
        every { openAudioPacketSource(broken) } throws IOException("Invalid audio")
        every { openAudioPacketSource(valid) } returns source
        every { format.containsKey(MediaFormat.KEY_DURATION) } returns true
        every { format.getLong(MediaFormat.KEY_DURATION) } returns 225_125L
    }

    @After
    fun tearDown() = unmockkAll()

    @Test
    fun anUnreadableFileDoesNotPreventReadingAndClosingTheNextFile() {
        AudioTrackDurationReader().use { reader ->
            assertNull(reader.durationMs(broken))
            assertEquals(225.125, reader.durationMs(valid)!!, 0.0)
        }
        verify(exactly = 1) { openAudioPacketSource(broken) }
        verify(exactly = 1) { openAudioPacketSource(valid) }
        verify(exactly = 1) { source.close() }
    }

    @Test
    fun aTrackWithoutDurationIsUnknownAndClosed() {
        every { format.containsKey(MediaFormat.KEY_DURATION) } returns false
        AudioTrackDurationReader().use { assertNull(it.durationMs(valid)) }
        verify(exactly = 0) { format.getLong(any()) }
        verify(exactly = 1) { source.close() }
    }

    @Test
    fun aNegativeDurationIsUnknownAndClosed() {
        every { format.getLong(MediaFormat.KEY_DURATION) } returns -1L
        AudioTrackDurationReader().use { assertNull(it.durationMs(valid)) }
        verify(exactly = 1) { source.close() }
    }

    @Test
    fun aFileWithoutAnAudioTrackIsUnknown() {
        every { openAudioPacketSource(valid) } returns null
        AudioTrackDurationReader().use { assertNull(it.durationMs(valid)) }
    }

    @Test
    fun aFailedMetadataReadStillClosesTheFile() {
        every { format.getLong(MediaFormat.KEY_DURATION) } throws IllegalStateException("Missing duration")
        AudioTrackDurationReader().use { assertNull(it.durationMs(valid)) }
        verify(exactly = 1) { source.close() }
    }
}
