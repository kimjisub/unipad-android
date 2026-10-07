package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.contentHashes
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.expectedHashes
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.packZip
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Recorder
import com.kimjisub.launchpad.unipack.UniPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class UniPackDownloadCompletionTest {
	private val env = PackInstallTestEnv()

	@Before fun setUp() = env.setUp()
	@After fun tearDown() = env.tearDown()

	@Test fun cancellationImmediatelyAfterCompletionKeepsTheAnnouncedPack() {
		lateinit var scope: CoroutineScope
		val listener = object : Recorder() {
			override fun onInstallComplete(folder: File, unipack: UniPack) {
				super.onInstallComplete(folder, unipack)
				// The screen closes as soon as it learns that installation finished. A callback
				// checking its cancelled scope throws before returning to the downloader.
				scope.cancel()
				scope.coroutineContext.ensureActive()
			}
		}
		scope = env.download(URL, listener)
		env.gate(URL).awaitRequest()
		env.gate(URL).open(packZip("Completed"))
		env.finish(scope)

		val folder = requireNotNull(listener.installedFolder)
		assertTrue("announced pack was deleted", folder.isDirectory)
		assertEquals(expectedHashes("Completed"), contentHashes(folder))
		assertEquals(listOf(folder.name), env.workspaceContents())
		assertNull(listener.error)
		assertEquals("keep the completed notification", 1, env.notifications.size)
	}

	@Test fun cancellationBeforeCompletionDiscardsTheUnannouncedPackAndNotification() {
		lateinit var scope: CoroutineScope
		val listener = object : Recorder() {
			override fun onImportStart(zip: File) { scope.cancel() }
		}
		scope = env.download(URL, listener)
		env.gate(URL).awaitRequest()
		env.gate(URL).open(packZip("Unannounced"))
		env.finish(scope)

		assertNull(listener.installedFolder)
		assertNull(listener.error)
		assertTrue(env.workspaceContents().isEmpty())
		assertTrue("cancelled progress notification remained", env.notifications.isEmpty())
	}

	@Test fun cancellingBlockedReadRemovesOnlyItsProgressNotification() {
		val completed = Recorder()
		val completedScope = env.download(OTHER_URL, completed, name = "other")
		env.gate(OTHER_URL).awaitRequest()
		env.gate(OTHER_URL).open(packZip("Other"))
		env.finish(completedScope)
		val otherNotifications = env.notifications.toMap()
		assertEquals(1, otherNotifications.size)

		val cancelled = Recorder()
		val scope = env.download(URL, cancelled)
		env.gate(URL).awaitRequest()
		val (reading, _) = env.gate(URL).openBlockedBody(ByteArray(4096), cancelBreaksRead = true)
		assertTrue(reading.await(10, TimeUnit.SECONDS))
		assertEquals(2, env.notifications.size)
		scope.cancel()
		env.finish(scope)

		assertEquals("cancel only this download's notification", otherNotifications, env.notifications.toMap())
		assertNull(cancelled.error)
		assertEquals(listOf("other"), env.workspaceContents())
	}

	@Test fun readErrorRemovesProgressNotification() {
		val listener = Recorder()
		val scope = env.download(URL, listener)
		env.gate(URL).awaitRequest()
		env.gate(URL).openThenBreak(ByteArray(4096), IOException("broken connection"))
		env.finish(scope)

		assertTrue(listener.error is IOException)
		assertNull(listener.installedFolder)
		assertTrue(env.workspaceContents().isEmpty())
		assertTrue("failed progress notification remained", env.notifications.isEmpty())
	}

	@Test fun failingErrorCallbackStillRemovesProgressNotification() {
		val listener = object : Recorder() {
			override fun onException(throwable: Throwable) {
				super.onException(throwable)
				throw kotlinx.coroutines.CancellationException("screen closed while reporting error")
			}
		}
		val scope = env.download(URL, listener)
		env.gate(URL).awaitRequest()
		env.gate(URL).fail()
		env.finish(scope)

		assertNotNull(listener.error)
		assertTrue(env.workspaceContents().isEmpty())
		assertTrue("failed callback skipped notification cleanup", env.notifications.isEmpty())
	}

	companion object {
		private const val URL = "https://test.invalid/download"
		private const val OTHER_URL = "https://test.invalid/other"
	}
}
