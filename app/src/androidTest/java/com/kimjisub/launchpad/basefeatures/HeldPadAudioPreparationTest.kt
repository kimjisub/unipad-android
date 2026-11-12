package com.kimjisub.launchpad.basefeatures

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.launchpad.audio.OboeAudioEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The held-pad fixture must decode identical silence once without merging pad identities. */
@RunWith(AndroidJUnit4::class)
class HeldPadAudioPreparationTest : HeldPadTest() {
    @Test fun identicalSilenceKeepsEveryFileIdentityWithoutRepeatedDecoding() {
        val sounds = File(screen.pack(), "sounds")
        val first = checkNotNull(audio.decode(File(sounds, "loop-1-1.wav")) { false })
        val second = checkNotNull(audio.decode(File(sounds, "loop-1-2.wav")) { false })
        assertNotSame("Each file needs its own load token", first, second)
        assertSame("Identical fixture silence must reuse the decoded PCM", first.pcm, second.pcm)

        // Decode everything before loading: equal PCM must never overwrite another file's name.
        val files = sounds.listFiles()!!.filter { it.name.startsWith("loop-") || it.name == "silence2.wav" }
        assertEquals("All 64 pad loops and the second chain must remain", 65, files.size)
        val decoded = files.map { file -> file to checkNotNull(audio.decode(file) { false }) }
        decoded.forEach { (_, sound) -> assertSame(first.pcm, sound.pcm) }
        val ids = decoded.map { (file, sound) ->
            audio.load(sound).also { assertEquals(it, audio.loaded.getValue(file.name)) }
        }
        assertEquals("Every fixture file must retain a distinct sound ID", 65, ids.toSet().size)
        assertEquals(files.map { it.name }.toSet(), audio.loaded.keys)
        decoded.forEachIndexed { index, (file, _) -> assertEquals(ids[index], audio.loaded.getValue(file.name)) }
    }

    @Test fun changedContentAtTheSamePathIsDecodedAgain() {
        val file = File(screen.pack(), "sounds/loop-1-1.wav")
        val original = checkNotNull(audio.decode(file) { false })
        // The fixture is PCM16 WAV with a 44-byte header. Change its first sample, not its name.
        file.writeBytes(file.readBytes().also { it[44] = 1 })
        val changed = checkNotNull(audio.decode(file) { false })
        assertNotSame("Changed bytes must not reuse the silence PCM", original.pcm, changed.pcm)
        assertFalse("The real decoder must observe the changed sample", original.pcm.contentEquals(changed.pcm))
    }

    @Test fun cachedSilenceStillHonorsInterruption() {
        var polls = 0
        assertThrows(OboeAudioEngine.DecodeInterruptedException::class.java) {
            audio.decode(File(screen.pack(), "sounds/loop-1-1.wav")) { ++polls == 2 }
        }
        assertEquals("Cancellation must reach the reuse path after its initial check", 2, polls)
        assertTrue("Interrupted preparation must not load a sound", audio.loaded.isEmpty())
    }
}
