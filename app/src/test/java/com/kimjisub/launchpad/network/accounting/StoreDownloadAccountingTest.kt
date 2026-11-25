package com.kimjisub.launchpad.network.accounting

import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class StoreDownloadAccountingTest {
    @Test(timeout = 15_000) fun delayedReceiptKeepsTheActualInstallationTimeAndRetentionDeadline() {
        val clock = AtomicLong(1_000)
        val issued = CompletableDeferred<DownloadReceipt?>()
        val saved = CountDownLatch(1)
        val recorded = AtomicReference<PendingDownload>()
        val store = object : PendingDownloadStore {
            private var rows = emptyList<PendingDownload>()
            override fun load() = rows
            override fun save(downloads: List<PendingDownload>) {
                rows = downloads.toList()
                downloads.firstOrNull()?.let { row ->
                    if (recorded.compareAndSet(null, row)) saved.countDown()
                }
            }
        }
        val api = object : DownloadAccountingApi {
            override suspend fun issue(code: String) = issued.await()
            override suspend fun complete(code: String, receipt: String) = "deferred"
        }
        val engine = DownloadAccounting(api, store, clock::get)
        val manager = StoreDownloadAccounting(engine, clock::get)
        try {
            val attempt = manager.begin("pack")
            attempt.installed()
            clock.set(5_000)
            issued.complete(DownloadReceipt("late-receipt", clock.get() + DownloadAccounting.RETENTION_MS))
            assertTrue("completion was not persisted", saved.await(10, TimeUnit.SECONDS))
            assertEquals(1_000L, recorded.get().completedAt)
            assertEquals(1_000L + DownloadAccounting.RETENTION_MS, recorded.get().expiresAt)
        } finally { manager.close() }
    }
}
