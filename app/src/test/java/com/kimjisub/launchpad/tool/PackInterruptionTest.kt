package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.PackStaging
import com.kimjisub.launchpad.manager.WorkspaceManager
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.PACK_NAME
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.contentHashes
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.expectedHashes
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.packFiles
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.packZip
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.writePack
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Recorder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The app can be killed at any moment of an install or a move (low memory, swiped away). What a
 * stopped job leaves behind must never show up as a pack, and the next attempt must still produce
 * the complete pack. A job is held at the chosen moment and the next launch is played out against
 * what it left, which is what the disk would hold had the process died there.
 */
class PackInterruptionTest {

	private val env = PackInstallTestEnv()
	private val held = CountDownLatch(1)
	private val release = CountDownLatch(1)

	@Before
	fun setUp() = env.setUp()

	@After
	fun tearDown() {
		release.countDown()
		env.tearDown()
	}

	/** Unpacking stops after `info` and `keySound`, before the sounds, and waits there. */
	private fun stopUnpackingHalfway() {
		mockkConstructor(ZipFile::class)
		every { anyConstructed<ZipFile>().extractAll(any()) } answers {
			val target = File(firstArg<String>()).apply { mkdirs() }
			packFiles("Halfway").filterKeys { it in setOf("info", "keySound") }
				.forEach { (name, data) -> File(target, name).writeBytes(data) }
			held.countDown()
			release.await(TIMEOUT_S, TimeUnit.SECONDS)
			throw IOException("process stopped")
		}
	}

	private fun awaitHeld() = assertTrue("the job never reached the moment it stops", held.await(TIMEOUT_S, TimeUnit.SECONDS))

	/**
	 * Starts the app again on what the stopped job left, as the app's start does: clears the staging
	 * folder, then lists the packs, each with the files it holds.
	 */
	private fun nextLaunch(): Map<String, List<String>> {
		PackStaging.setAsideLeftovers(env.workspace).forEach(FileManager::deleteDirectory)
		return WorkspaceManager.packFolders(env.workspace).associate { it.name to contentHashes(it).keys.sorted() }
	}

	@Test
	fun fileImportStoppedWhileUnpackingLeavesNoPackOnTheNextLaunch() {
		stopUnpackingHalfway()
		env.import(packZip("Song"), Recorder())
		awaitHeld()

		assertEquals(emptyMap<String, List<String>>(), nextLaunch())
	}

	@Test
	fun storeDownloadStoppedWhileUnpackingLeavesNoPackOnTheNextLaunch() {
		stopUnpackingHalfway()
		env.download(URL, Recorder())
		env.gate(URL).open(packZip("Song"))
		awaitHeld()

		assertEquals(emptyMap<String, List<String>>(), nextLaunch())
	}

	@Test
	fun theNextLaunchKeepsEveryFolderThePersonPutInTheWorkspace() {
		writePack(File(env.workspace, PACK_NAME), "Existing")
		File(env.workspace, ".hidden pack").mkdirs()
		File(env.workspace, "empty").mkdirs()
		stopUnpackingHalfway()
		env.import(packZip("Song"), Recorder())
		awaitHeld()

		assertEquals(mapOf(PACK_NAME to expectedHashes("Existing").keys.sorted(), ".hidden pack" to emptyList(), "empty" to emptyList()), nextLaunch())
		assertEquals(listOf(".hidden pack", "empty", PACK_NAME), env.workspaceContents())
	}

	/** A file that cannot be copied while the pack's extra top folder is removed fails the import. */
	@Test
	fun aFileThatCannotBeCopiedOutOfTheTopFolderFailsTheImport() {
		mockkConstructor(ZipFile::class)
		every { anyConstructed<ZipFile>().extractAll(any()) } answers {
			callOriginal()
			val sound = File(firstArg<String>(), "Song/sounds/a.wav")
			sound.setReadable(false)
			assumeFalse("running as root, which reads any file", sound.canRead())
		}
		val recorder = Recorder()

		env.finish(env.import(packZip("Song", topFolder = "Song"), recorder))

		assertNull("a pack missing a.wav was reported as imported", recorder.installedFolder)
		assertNotNull(recorder.error)
		assertEquals(emptyList<String>(), env.workspaceContents())
	}

	/** Moving a pack to another storage is stopped half-way, then started again. */
	@Test
	fun moveStoppedHalfwayCopiesThePackAgainOnTheNextTry() {
		val sdCard = File(env.root, "sd").apply { mkdirs() }
		val source = File(sdCard, PACK_NAME).apply { mkdirs() }
		writePack(source, "Song")
		val stopped = AtomicBoolean(false)
		mockkObject(FileManager)
		every { FileManager.copyDirectory(any(), any()) } answers {
			if (firstArg<File>() == source && stopped.compareAndSet(false, true)) {
				val target = secondArg<File>().apply { mkdirs() }
				File(source, "info").copyTo(File(target, "info"))
				held.countDown()
				release.await(TIMEOUT_S, TimeUnit.SECONDS)
				throw IOException("process stopped")
			}
			callOriginal()
		}
		val helper = SafMigrationHelper(mockk(relaxed = true))

		runBlocking {
			val first = async(Dispatchers.IO) { helper.transferFileToFile(listOf(source), env.workspace, true) { _, _, _ -> } }
			awaitHeld()
			val listedAfterStop = nextLaunch()
			val retry = helper.transferFileToFile(listOf(source), env.workspace, true) { _, _, _ -> }

			assertEquals(
				mapOf(
					"listed after the stop" to emptyMap<String, List<String>>(),
					"copied on the retry" to 1,
					"files at the new place" to expectedHashes("Song").keys.sorted(),
					"left at the old place" to false,
				),
				mapOf(
					"listed after the stop" to listedAfterStop,
					"copied on the retry" to retry.transferred,
					"files at the new place" to contentHashes(File(env.workspace, PACK_NAME)).keys.sorted(),
					"left at the old place" to source.exists(),
				),
			)
			assertEquals(expectedHashes("Song"), contentHashes(File(env.workspace, PACK_NAME)))
			release.countDown()
			first.await()
		}
	}

	private companion object {
		const val URL = "https://test.invalid/song.zip"
		const val TIMEOUT_S = 10L
	}
}
