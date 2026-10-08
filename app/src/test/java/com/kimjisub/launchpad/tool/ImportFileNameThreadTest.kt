package com.kimjisub.launchpad.tool

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
}
