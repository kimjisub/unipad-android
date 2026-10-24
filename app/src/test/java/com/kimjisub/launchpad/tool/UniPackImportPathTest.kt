package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.packZip
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Recorder
import com.kimjisub.launchpad.unipack.UniPackFolder
import io.mockk.every
import io.mockk.mockkConstructor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UniPackImportPathTest {
	private val env = PackInstallTestEnv()

	@Before
	fun setUp() = env.setUp()

	@After
	fun tearDown() = env.tearDown()

	@Test
	fun dotsInArchiveName_areKeptExceptForTheExtension() {
		val result = Recorder()
		env.finish(env.import(packZip("Pack"), result, fileName = "Song v1.2 (Remix).zip"))
		assertNull(result.error)
		assertEquals("Song v1.2 (Remix)", result.installedFolder?.name)
	}

	@Test
	fun emptyArchiveBaseName_usesUniPack() {
		val result = Recorder()
		env.finish(env.import(packZip("Pack"), result, fileName = ".zip"))
		assertNull(result.error)
		assertEquals("UniPack", result.installedFolder?.name)
	}

	@Test
	fun validationOutOfMemoryReportsFailureAndRemovesAllStagingFiles() {
		val outOfMemory = OutOfMemoryError("info is too large")
		mockkConstructor(UniPackFolder::class)
		every { anyConstructed<UniPackFolder>().load() } throws outOfMemory
		val result = Recorder()

		env.finish(env.import(packZip("Large pack"), result))

		assertTrue("${result.error}", result.error is UniPackImporter.UniPackCriticalErrorException)
		assertEquals(outOfMemory, result.error?.cause)
		assertNull(result.installedFolder)
		assertEquals(emptyList<String>(), env.workspaceContents())
		assertEquals(1, env.notifications.size)
		assertEquals(PackInstallTestEnv.text(R.string.failed), env.notifyLog.last().second.text)
		assertTrue(env.shownNow().none { it.ongoing })
	}
}
