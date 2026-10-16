package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.tool.PackInstallTestEnv.Companion.packZip
import com.kimjisub.launchpad.tool.PackInstallTestEnv.Recorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class PackStorageChangesTest {
	private val env = PackInstallTestEnv()
	private val observer = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
	private val snapshots = CopyOnWriteArrayList<List<String>>()

	@Before fun setUp() {
		env.setUp()
		observer.launch(start = CoroutineStart.UNDISPATCHED) {
			PackStorageChanges.changes.collect { snapshots += env.workspaceContents() }
		}
	}

	@After fun tearDown() {
		observer.cancel()
		env.tearDown()
	}

	@Test fun completionNotifiesOnceAfterRemovingTheArchiveAndStagingFolder() {
		val listener = Recorder()
		val scope = env.download(URL, listener)
		env.gate(URL).open(packZip("Completed"))
		env.finish(scope)
		assertNull(listener.error)
		assertEquals(listOf(listOf(requireNotNull(listener.installedFolder).name)), snapshots.toList())
	}

	@Test fun failureNotifiesOnceAfterDiscardingTheExtractedPack() {
		val listener = Recorder()
		val scope = env.download(URL, listener)
		env.gate(URL).open(ByteArray(32))
		env.finish(scope)
		assertNotNull(listener.error)
		assertEquals(listOf(emptyList<String>()), snapshots.toList())
	}

	@Test fun cancellationNotifiesOnceAfterDiscardingThePartialArchive() {
		val scope = env.download(URL, Recorder())
		val (reading, release) = env.gate(URL).openBlockedBody(ByteArray(4096), cancelBreaksRead = true)
		try {
			assertTrue(reading.await(10, TimeUnit.SECONDS))
			assertTrue(env.workspace.walk().any { it.name.endsWith(".zip") })
			scope.cancel()
			env.finish(scope)
			assertEquals(listOf(emptyList<String>()), snapshots.toList())
		} finally {
			release.countDown()
			scope.cancel()
			env.finish(scope)
		}
	}

	@Test fun aScreenThatStartsListeningLaterIsNotGivenAnEarlierChange() {
		PackStorageChanges.notifyChanged()
		val late = CopyOnWriteArrayList<Unit>()
		observer.launch(start = CoroutineStart.UNDISPATCHED) {
			PackStorageChanges.changes.collect { late += it }
		}.cancel()
		assertEquals(emptyList<Unit>(), late.toList())
	}

	companion object { private const val URL = "https://test.invalid/storage-change" }
}
