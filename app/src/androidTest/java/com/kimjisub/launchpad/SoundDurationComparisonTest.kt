package com.kimjisub.launchpad

import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.audio.openAudioPacketSource
import com.kimjisub.launchpad.tool.AudioTrackDurationReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Measures whether replacing the duration API would change existing autoplay waits. */
@RunWith(AndroidJUnit4::class)
class SoundDurationComparisonTest {
    @Test
    fun comparePlayerAndPlaybackExtractor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val folder = File(instrumentation.targetContext.cacheDir, "sound-duration-comparison").apply { mkdirs() }
        val differences = mutableListOf<String>()
        try {
            for (extension in listOf("wav", "mp3", "ogg", "m4a")) {
                val file = File(folder, "silence.$extension")
                instrumentation.context.assets.open("sound-duration/${file.name}").use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                val player = MediaPlayer()
                val oldMs = try {
                    player.setDataSource(file.absolutePath)
                    player.prepare()
                    player.duration
                } finally {
                    player.release()
                }
                val newMs = checkNotNull(openAudioPacketSource(file)).use { source ->
                    source.format.getLong(MediaFormat.KEY_DURATION) / 1000.0
                }
                AudioTrackDurationReader().use { reader ->
                    assertEquals(newMs, checkNotNull(reader.durationMs(file)), 0.0)
                }
                val retriever = MediaMetadataRetriever()
                val metadataMs = try {
                    retriever.setDataSource(file.absolutePath)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                } finally {
                    retriever.release()
                }
                val row = "API ${Build.VERSION.SDK_INT}, $extension: player=$oldMs ms, extractor=$newMs ms, " +
                    "rounded=${newMs.roundToInt()} ms, delta=${newMs.roundToInt() - oldMs} ms, retriever=$metadataMs ms"
                Log.i("SoundDurationComparison", row)
                println(row)
                if (abs(newMs.roundToInt() - oldMs) > 1) differences += row
            }
            assertTrue("Duration changes exceed millisecond rounding:\n${differences.joinToString("\n")}", differences.isEmpty())
        } finally {
            folder.deleteRecursively()
        }
    }
}
