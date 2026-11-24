package com.kimjisub.launchpad.network.accounting

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class DownloadAccountingTest {
    private var time = 1_000L
    private val store = MemoryStore()
    private val api = RecordingApi(store)
    private fun engine() = DownloadAccounting(api, store) { time }
    private fun receipt(id: String = "receipt", expires: Long = time + DownloadAccounting.RETENTION_MS) =
        DownloadReceipt(id, expires)

    @Test fun savedDownloadIsPersistedBeforeSendingAndCountedOnce() = runTest {
        val accounting = engine()
        accounting.completed("pack", receipt())
        assertTrue(api.sent.isEmpty())
        accounting.retryPending()
        assertEquals(listOf("pack" to "receipt"), api.sent)
        assertTrue(store.rows.isEmpty())
        accounting.retryPending()
        assertEquals(1, api.sent.size)
    }

    @Test fun receiptAloneNeverSendsCompletionOrPersistsPendingDownload() = runTest {
        assertNotNull(engine().issue("pack"))
        engine().retryPending()
        assertTrue(api.sent.isEmpty())
        assertTrue(store.rows.isEmpty())
    }

    @Test fun failedOrDisabledIssuanceLeavesNoQueue() = runTest {
        api.issueResult = null
        assertNull(engine().issue("pack"))
        api.failIssue = true
        assertNull(engine().issue("pack"))
        assertTrue(store.rows.isEmpty())
        assertTrue(api.sent.isEmpty())
    }

    @Test fun responseLossSurvivesRestartAndRetriesTheSameReceiptAfterOneMinute() = runTest {
        api.failComplete = true
        engine().completed("pack", receipt())
        engine().retryPending()
        engine().retryPending()
        assertEquals(1, api.sent.size)
        assertEquals(1, store.rows.single().attempts)
        time += 59_999
        engine().retryPending()
        assertEquals(1, api.sent.size)
        time++
        api.failComplete = false
        api.result = "already_counted"
        engine().retryPending()
        assertEquals(listOf("pack" to "receipt", "pack" to "receipt"), api.sent)
        assertEquals(0, api.issueCalls)
        assertTrue(store.rows.isEmpty())
    }

    @Test fun dailyBudgetAndRetryTimesSurviveRestart() = runTest {
        api.result = "deferred"
        engine().completed("pack", receipt())
        repeat(6) { engine().retryPending(); time += 60_000 }
        assertEquals(3, api.sent.size)
        time = 1_000 + DownloadAccounting.DAY_MS
        engine().retryPending()
        assertEquals(4, api.sent.size)
        assertEquals(1, store.rows.single().attempts)
    }

    @Test fun terminalResponsesRemoveButDisabledDeferredAndUnknownRetain() = runTest {
        for (result in listOf("counted", "already_counted", "expired", "invalid", "disabled", "deferred", "unknown")) {
            store.rows = emptyList()
            api.result = result
            engine().completed("pack", receipt(result))
            engine().retryPending()
            assertEquals(result, result in setOf("disabled", "deferred", "unknown"), store.rows.isNotEmpty())
        }
    }

    @Test fun serverExpiryIsAnExclusiveBoundaryAndNeverCreatesANewReceipt() = runTest {
        api.result = "deferred"
        engine().completed("pack", receipt(expires = time + 60_000))
        time += 60_000
        engine().retryPending()
        assertTrue(store.rows.isEmpty())
        assertTrue(api.sent.isEmpty())
        assertEquals(0, api.issueCalls)
    }

    @Test fun retentionEndsAtTheEarlierOfServerExpiryAndSevenDaysAfterSaving() = runTest {
        val accounting = engine()
        accounting.completed("pack", receipt(expires = time + 2 * DownloadAccounting.RETENTION_MS))
        assertEquals(time + DownloadAccounting.RETENTION_MS, store.rows.single().expiresAt)
        time += DownloadAccounting.RETENTION_MS
        accounting.retryPending()
        assertTrue(store.rows.isEmpty())
        assertTrue(api.sent.isEmpty())
    }

    @Test fun overflowEvictsOldestWithoutSendingOrReissuing() = runTest {
        val accounting = engine()
        repeat(1_001) { accounting.completed("pack-$it", receipt("r-$it")); time++ }
        assertEquals(1_000, store.rows.size)
        assertEquals("r-1", store.rows.first().receipt)
        assertEquals("r-1000", store.rows.last().receipt)
        assertTrue(api.sent.isEmpty())
    }

    @Test fun movingClockBackwardDoesNotResetRetryBudgetOrReissue() = runTest {
        api.result = "deferred"
        engine().completed("pack", receipt())
        engine().retryPending()
        time -= DownloadAccounting.DAY_MS
        engine().retryPending()
        assertEquals(1, api.sent.size)
        assertEquals(0, api.issueCalls)
        assertEquals("receipt", store.rows.single().receipt)
    }

    @Test fun failingPersistenceNeverSendsAnUnrecordedCompletionOrThrowsIntoTheDownload() = runTest {
        store.failSave = true
        engine().completed("pack", receipt())
        engine().retryPending()
        assertTrue(api.sent.isEmpty())
    }

    @Test fun fiveSecondTimeoutRetainsUncertainCompletionAndDoesNotBlockIssuance() = runTest {
        api.slow = true
        assertNull(engine().issue("pack"))
        engine().completed("pack", receipt())
        engine().retryPending()
        assertEquals(1, store.rows.size)
        assertEquals(1, store.rows.single().attempts)
    }

    @Test fun overlappingFlushesDoNotSendTheSameReceiptConcurrently() = runTest {
        api.completeDelay = 10
        engine().let { accounting ->
            accounting.completed("pack", receipt())
            val a = async { accounting.retryPending() }
            val b = async { accounting.retryPending() }
            a.await(); b.await()
        }
        assertEquals(1, api.sent.size)
    }

    private class MemoryStore : PendingDownloadStore {
        var rows = emptyList<PendingDownload>()
        var failSave = false
        override fun load() = rows
        override fun save(downloads: List<PendingDownload>) {
            if (failSave) throw IOException("storage unavailable")
            rows = downloads.toList()
        }
    }

    private class RecordingApi(private val store: MemoryStore) : DownloadAccountingApi {
        var issueCalls = 0
        var issueResult: DownloadReceipt? = DownloadReceipt("receipt", Long.MAX_VALUE)
        var failIssue = false
        var failComplete = false
        var slow = false
        var completeDelay = 0L
        var result = "counted"
        val sent = mutableListOf<Pair<String, String>>()
        override suspend fun issue(code: String): DownloadReceipt? {
            issueCalls++
            if (slow) delay(10_000)
            if (failIssue) throw IOException("offline")
            return issueResult
        }
        override suspend fun complete(code: String, receipt: String): String {
            assertTrue(store.rows.any { it.receipt == receipt && it.attempts > 0 })
            sent += code to receipt
            if (slow) delay(10_000)
            if (failComplete) throw IOException("response lost")
            delay(completeDelay)
            return result
        }
    }
}
