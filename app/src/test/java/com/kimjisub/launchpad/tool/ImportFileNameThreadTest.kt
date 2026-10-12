package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import io.mockk.every
import io.mockk.mockkConstructor
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

class ImportFileNameThreadTest {
    private val env = PackInstallTestEnv()
    @Before fun setUp() = env.setUp()
    @After fun tearDown() = env.tearDown()

    @Test fun fileNameIsReadOffTheCallingThreadAndStillNamesTheInstalledPack() {
        val caller = Thread.currentThread().name
        val result = PackInstallTestEnv.Recorder()
        env.finish(env.import(PackInstallTestEnv.packZip("Song"), result, fileName = "picked.zip"))
        assertEquals("picked", result.installedFolder?.name)
        assertEquals(1, env.fileNameResolvedOn.size)
        assertFalse("File name was read on the calling thread", env.fileNameResolvedOn.contains(caller))
    }
    @Test fun packSizeIsMeasuredBeforeCompletionOffTheMainDispatcher() {
        val measurements = CopyOnWriteArrayList<Pair<Long, Boolean>>()
        mockkConstructor(UniPackFolder::class)
        every { anyConstructed<UniPackFolder>().getByteSize() } answers {
            val size = callOriginal()
            measurements += size to (Thread.currentThread().name == "pack-test-main")
            size
        }
        var atCompletion: List<Pair<Long, Boolean>> = emptyList()
        var reportedSize: Long? = null
        val result = object : PackInstallTestEnv.Recorder() {
            override fun onImportComplete(folder: File, unipack: UniPack, byteSize: Long) {
                atCompletion = measurements.toList()
                reportedSize = byteSize
                super.onImportComplete(folder, unipack, byteSize)
            }
        }
        env.finish(env.import(PackInstallTestEnv.packZip("Song"), result))
        assertEquals("Pack size must be ready before notifying the screen", 1, atCompletion.size)
        val expected = PackInstallTestEnv.packFiles("Song").values.sumOf { it.size.toLong() }
        assertEquals(expected, atCompletion.single().first)
        assertEquals(expected, reportedSize)
        assertFalse("Pack size was read on the main dispatcher", atCompletion.single().second)
    }
}
