package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Physical WAV lengths go through the real selector; only the platform decoder is replaced. */
class SoundRunnerSelectionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    @After fun cleanUp() { scope.cancel() }

    private class Engine(val failures: Map<String, Throwable> = emptyMap(), val undecodable: Set<String> = emptySet()) : SoundRunner.Engine {
        val decoded = mutableListOf<String>()
        var loads = 0
        override fun start() = true
        override fun stop() {}
        override fun decode(file: File): OboeAudioEngine.DecodedAudio? {
            synchronized(decoded) { decoded += file.name }
            failures[file.name]?.let { throw it }
            if (file.name in undecodable) return null
            return OboeAudioEngine.DecodedAudio(shortArrayOf(1), 1, 1, 48_000)
        }
        override fun load(decoded: OboeAudioEngine.DecodedAudio) = loads++
        override fun unloadSound(soundId: Int) {}
        override fun unloadAll() {}
        override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int) = 0
        override fun stopVoice(stopKey: Int) {}
        override fun stopAllVoices() {}
    }
    private class Listener : SoundRunner.LoadingListener {
        var report: SoundRunner.LoadReport? = null
        var ended = false
        var failure: Throwable? = null
        override fun onStart(soundCount: Int) {}
        override fun onProgressTick() {}
        override fun onReport(report: SoundRunner.LoadReport) { this.report = report }
        override fun onEnd() { ended = true }
        override fun onException(throwable: Throwable) { failure = throwable }
    }
    private fun wave(folder: File, name: String, frames: Int, rate: Int = 48_000, channels: Int = 2) {
        val size = frames * channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(size + 36).put("WAVEfmt ".toByteArray()).putInt(16)
        header.putShort(1).putShort(channels.toShort()).putInt(rate).putInt(rate * channels * 2)
        header.putShort((channels * 2).toShort()).putShort(16).put("data".toByteArray()).putInt(size)
        RandomAccessFile(File(folder, name), "rw").use { it.write(header.array()); it.setLength(44L + size) }
    }
    private fun pack(lines: String, setup: (File) -> Unit): UniPackFolder {
        val root = temporary.newFolder()
        File(root, "info").writeText("title=Selection\nproducerName=Test\nbuttonX=8\nbuttonY=8\nchain=2\n")
        File(root, "keySound").writeText(lines)
        setup(File(root, "sounds").apply { mkdir() })
        return UniPackFolder(root).apply { load(); loadDetail() }
    }
    private fun load(pack: UniPackFolder, engine: Engine): Listener {
        val listener = Listener()
        SoundRunner(pack, ChainObserver(), listener, scope, engine)
        runBlocking { withTimeout(10_000) { job.children.toList().joinAll() } }
        return listener
    }

    @Test fun reservesBeforeDecodingAndSkipsOnlyTheOverBudgetFile() {
        val pack = pack("1 1 1 a.wav\n1 1 1 b.wav\n1 1 1 c.wav\n1 1 1 d.wav\n2 1 1 a.wav\n") {
            for (name in listOf("a.wav", "b.wav", "c.wav")) wave(it, name, 28_800_000)
            wave(it, "d.wav", 220_500, 44_100)
        }
        val engine = Engine()
        val result = load(pack, engine)
        assertTrue(result.ended)
        assertNull(result.failure)
        assertEquals(listOf("a.wav", "b.wav", "d.wav"), engine.decoded)
        assertEquals(3, engine.loads)
        assertEquals(115_680_000L, result.report!!.reservedCost)
        assertEquals(listOf(SoundRunner.FailureReason.LIMIT), result.report!!.failures.map { it.reason })
        assertTrue(pack.soundTable!![0][0][0]!![2].id < 0)
        assertEquals(pack.soundTable!![0][0][0]!![0].id, pack.soundTable!![1][0][0]!![0].id)
    }

    @Test fun anUnreadableFileNeverReachesTheDecoder() {
        val pack = pack("1 1 1 bad.wav\n1 1 2 good.wav\n") {
            File(it, "bad.wav").writeText("not audio")
            wave(it, "good.wav", 48_000)
        }
        val engine = Engine()
        assertTrue(load(pack, engine).ended)
        assertEquals(listOf("good.wav"), engine.decoded)
        assertTrue(pack.soundTable!![0][0][0]!![0].id < 0)
    }

    @Test fun decodeMemoryFailureDoesNotPreventTheFollowingSmallSoundFromLoading() {
        val pack = pack("1 1 1 a.wav\n1 1 2 b.wav\n") {
            wave(it, "a.wav", 48_000); wave(it, "b.wav", 48_000)
        }
        val result = load(pack, Engine(mapOf("a.wav" to OutOfMemoryError("allocation"))))
        assertTrue("a per-file allocation failure must not abort the pack", result.ended)
        assertNull(result.failure)
        assertTrue(result.report!!.compatibilityFailed)
        assertEquals(listOf(SoundRunner.FailureReason.MEMORY), result.report!!.failures.map { it.reason })
        assertTrue(pack.soundTable!![0][0][0]!![0].id < 0)
        assertTrue(pack.soundTable!![0][0][1]!![0].id >= 0)
    }


    @Test fun aDecodeFailureDoesNotGiveItsReservationToTheNextLargeFile() {
        val pack = pack("1 1 1 a.wav\n1 1 1 b.wav\n1 1 1 c.wav\n1 1 1 d.wav\n") {
            for (name in listOf("a.wav", "b.wav", "c.wav")) wave(it, name, 28_800_000)
            wave(it, "d.wav", 220_500, 44_100)
        }
        val engine = Engine(undecodable = setOf("a.wav"))
        assertTrue(load(pack, engine).ended)
        assertEquals(listOf("a.wav", "b.wav", "d.wav"), engine.decoded)
        assertEquals(2, engine.loads)
    }

    @Test fun usesChainThenXThenYThenPadLineOrder() {
        val pack = pack("2 1 1 last.wav\n1 2 1 third.wav\n1 1 2 second.wav\n1 1 1 first.wav\n") {
            for (name in listOf("first.wav", "second.wav", "third.wav", "last.wav")) wave(it, name, 480)
        }
        val engine = Engine()
        assertTrue(load(pack, engine).ended)
        assertEquals(listOf("first.wav", "second.wav", "third.wav", "last.wav"), engine.decoded)
    }

    @Test fun aPhysicalSevenHundredSecondFileIsExcludedWhileAFiveSecondFilePlays() {
        val pack = pack("1 1 1 long.wav\n1 1 2 short.wav\n") {
            wave(it, "long.wav", 33_600_000)
            wave(it, "short.wav", 240_000)
        }
        val engine = Engine()
        assertTrue(load(pack, engine).ended)
        assertEquals(listOf("short.wav"), engine.decoded)
    }

    @Test fun aLightOnlyPackStillOpens() {
        val result = load(pack("") {}, Engine())
        assertTrue(result.ended)
        assertNull(result.failure)
    }
}
