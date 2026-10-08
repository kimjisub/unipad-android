package com.kimjisub.launchpad.manager

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PackStagingStartupTest {
    private val workspace = Files.createTempDirectory("staging_startup").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val release = CountDownLatch(1)

    @After fun tearDown() = runBlocking {
        release.countDown()
        scope.coroutineContext[Job]!!.children.toList().joinAll()
        scope.cancel()
        workspace.deleteRecursively()
        Unit
    }

    @Test fun newImportFolderWaitsForCleanupAndSurvivesIt() = runBlocking {
        val old = File(workspace, ".unipad-staging/old").apply { mkdirs() }
        File(old, "partial").writeText("left by a stopped import")
        val started = CountDownLatch(1)
        val cleanup = PackStaging.startCleanup(scope, setAside = {
            started.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            PackStaging.setAsideLeftovers(workspace)
        }, deleteLeftovers = { it.forEach(FileManager::deleteDirectory) })
        assertTrue(started.await(10, TimeUnit.SECONDS))
        // Undispatched entry reaches create before returning: no sleep or scheduling guess.
        val importing = async(start = CoroutineStart.UNDISPATCHED) {
            PackStaging.create(workspace).also { File(it, "sound").writeText("new import") }
        }
        try {
            assertFalse("Import created a folder before startup cleanup finished", importing.isCompleted)
        } finally {
            release.countDown()
        }
        cleanup.join()
        val fresh = importing.await()
        scope.coroutineContext[Job]!!.children.toList().joinAll()
        assertEquals("new import", File(fresh, "sound").readText())
        assertFalse(old.exists())
        assertEquals(listOf(".unipad-staging"), workspace.list()!!.sorted())
    }

    @Test fun newImportDoesNotWaitForDeletionOfSetAsideFolders() = runBlocking {
        val old = File(workspace, ".unipad-staging/old").apply { mkdirs() }
        File(old, "partial").writeText("old")
        val deleting = CountDownLatch(1)
        val cleanup = PackStaging.startCleanup(scope, setAside = { PackStaging.setAsideLeftovers(workspace) }, deleteLeftovers = { leftovers ->
            deleting.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            leftovers.forEach(FileManager::deleteDirectory)
        })
        assertTrue(deleting.await(10, TimeUnit.SECONDS))
        val importing = async(start = CoroutineStart.UNDISPATCHED) {
            PackStaging.create(workspace).also { File(it, "sound").writeText("new") }
        }
        try {
            val fresh = withTimeoutOrNull(1000) { importing.await() }
            assertNotNull("Import waited for deletion after leftovers were set aside", fresh)
            assertEquals("new", File(fresh!!, "sound").readText())
        } finally {
            release.countDown()
        }
        cleanup.join()
        scope.coroutineContext[Job]!!.children.toList().joinAll()
        assertEquals("new", File(importing.await(), "sound").readText())
    }

    @Test fun failedCleanupStillLetsTheNextImportCreateItsFolder() = runBlocking {
        val error = java.io.IOException("storage unavailable")
        val caught = CompletableDeferred<Throwable>()
        val cleanup = PackStaging.startCleanup(scope + CoroutineExceptionHandler { _, e -> caught.complete(e) },
            setAside = { throw error }, deleteLeftovers = { error("must not delete") })
        cleanup.join()
        assertSame(error, caught.await())
        assertTrue(withTimeout(10000) { PackStaging.create(workspace) }.isDirectory)
    }

    @Test fun cancelledImportWaitingForCleanupNeverCreatesAFolder() = runBlocking {
        val started = CountDownLatch(1)
        val cleanup = PackStaging.startCleanup(scope, setAside = {
            started.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            emptyList()
        }, deleteLeftovers = {})
        assertTrue(started.await(10, TimeUnit.SECONDS))
        val importing = async(start = CoroutineStart.UNDISPATCHED) { PackStaging.create(workspace) }
        importing.cancelAndJoin()
        release.countDown()
        cleanup.join()
        assertTrue(importing.isCancelled)
        assertEquals(emptyList<String>(), workspace.list()!!.toList())
    }

    @Test fun cleanupCancelledBeforeStartingDoesNotLeaveImportsWaiting() = runBlocking {
        val cancelled = CoroutineScope(SupervisorJob().apply { cancel() } + Dispatchers.IO)
        PackStaging.startCleanup(cancelled, setAside = { error("must not run") }, deleteLeftovers = {})
        assertTrue(withTimeout(10000) { PackStaging.create(workspace) }.isDirectory)
    }
}
