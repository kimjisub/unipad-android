package com.kimjisub.launchpad.network.accounting

import androidx.annotation.Keep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** A receipt belongs to one download attempt, never to a person or a device. */
data class DownloadReceipt(val receipt: String, val expiresAt: Long)

@Keep
data class PendingDownload(
    val code: String,
    val receipt: String,
    val completedAt: Long,
    val expiresAt: Long,
    val lastAttemptAt: Long? = null,
    val windowStartedAt: Long? = null,
    val attempts: Int = 0,
)

interface DownloadAccountingApi {
    suspend fun issue(code: String): DownloadReceipt?
    suspend fun complete(code: String, receipt: String): String
}

interface PendingDownloadStore {
    fun load(): List<PendingDownload>
    fun save(downloads: List<PendingDownload>)
}

/** All callers use IO. Persistence precedes transmission, including the retry budget. */
class DownloadAccounting(
    private val api: DownloadAccountingApi,
    private val store: PendingDownloadStore,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val queueLock = Mutex()
    private val sending = Mutex()

    suspend fun issue(code: String): DownloadReceipt? = bestEffort {
        withTimeoutOrNull(REQUEST_TIMEOUT_MS) { api.issue(code) }
            ?.takeIf { it.receipt.isNotBlank() && it.receipt.length <= 128 && it.expiresAt > now() }
    }

    /** Only the store's successful installation callback calls this, never imports or playback. */
    suspend fun completed(code: String, receipt: DownloadReceipt, completedAt: Long = now()) {
        bestEffort {
            queueLock.withLock {
                val time = now()
                val rows = retained(store.load(), time)
                val expires = minOf(receipt.expiresAt, completedAt + RETENTION_MS)
                if (expires > time && rows.none { it.receipt == receipt.receipt }) {
                    store.save((rows + PendingDownload(code, receipt.receipt, completedAt, expires))
                        .sortedBy { it.completedAt }.takeLast(MAX_PENDING))
                }
            }
        }
    }

    /** Invoked only while the app is visible. Cancellation never discards an uncertain result. */
    suspend fun retryPending() {
        sending.withLock {
            val candidates = bestEffort {
                queueLock.withLock {
                    val rows = store.load()
                    val kept = retained(rows, now())
                    if (kept != rows) store.save(kept)
                    kept
                }
            } ?: return
            for (candidate in candidates) {
                coroutineContext.ensureActive()
                val attempt = bestEffort {
                    queueLock.withLock queue@ {
                        val time = now()
                        val rows = retained(store.load(), time)
                        val row = rows.firstOrNull { it.receipt == candidate.receipt }
                            ?: return@queue null
                        val window = row.windowStartedAt
                        val attempts = if (window == null || time - window >= DAY_MS) 0 else row.attempts
                        val tooSoon = row.lastAttemptAt?.let { time - it < RETRY_INTERVAL_MS } ?: false
                        if (attempts >= MAX_DAILY_ATTEMPTS || tooSoon) return@queue null
                        val updated = row.copy(lastAttemptAt = time,
                            windowStartedAt = if (attempts == 0) time else window,
                            attempts = attempts + 1)
                        store.save(rows.map { if (it.receipt == row.receipt) updated else it })
                        updated
                    }
                } ?: continue
                val result = bestEffort {
                    withTimeoutOrNull(REQUEST_TIMEOUT_MS) { api.complete(attempt.code, attempt.receipt) }
                }
                if (result in TERMINAL_RESULTS) {
                    bestEffort {
                        queueLock.withLock queue@ {
                            store.save(retained(store.load(), now()).filterNot { it.receipt == attempt.receipt })
                        }
                    }
                }
            }
        }
    }

    private fun retained(rows: List<PendingDownload>, time: Long) = rows
        .filter { it.expiresAt > time && it.expiresAt <= it.completedAt + RETENTION_MS }
        .sortedBy { it.completedAt }.takeLast(MAX_PENDING)

    private suspend fun <T> bestEffort(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // Accounting failure cannot change a pack installation's result.
        null
    }

    companion object {
        const val REQUEST_TIMEOUT_MS = 5_000L
        const val RETRY_INTERVAL_MS = 60_000L
        const val DAY_MS = 24 * 60 * 60 * 1_000L
        const val RETENTION_MS = 7 * DAY_MS
        const val MAX_PENDING = 1_000
        const val MAX_DAILY_ATTEMPTS = 3
        private val TERMINAL_RESULTS = setOf("counted", "already_counted", "expired", "invalid")
    }
}
