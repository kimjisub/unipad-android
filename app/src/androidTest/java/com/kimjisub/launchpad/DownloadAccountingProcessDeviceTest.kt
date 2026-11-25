package com.kimjisub.launchpad

import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.kimjisub.launchpad.network.accounting.DownloadAccounting
import com.kimjisub.launchpad.network.accounting.DownloadAccountingApi
import com.kimjisub.launchpad.network.accounting.DownloadReceipt
import com.kimjisub.launchpad.network.accounting.FilePendingDownloadStore
import com.kimjisub.launchpad.network.accounting.HttpDownloadAccountingApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Orchestrator terminates the target process between these ordered methods, preserving app data. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DownloadAccountingProcessDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val metadata = context.getSharedPreferences("accounting-process-test", 0)
    private val file = File(context.noBackupFilesDir, "accounting-process-test.json")
    private val store = FilePendingDownloadStore(file)
    private val code = "accounting-process-test"
    private val demo = InstrumentationRegistry.getArguments().getString("downloadAccountingBackend") == "demo-unipad"

    @Test fun a_responseLostAfterCommitLeavesAnAtomicRetryFile() = runBlocking {
        file.delete()
        assertTrue(metadata.edit().clear().putInt("pid", Process.myPid()).commit())
        if (demo) {
            database("downloadAccounting/config/enabled", "true")
            database("store/$code", """{"code":"$code","downloadCount":50}""")
        }
        val remote = api()
        val lossy = object : DownloadAccountingApi by remote {
            override suspend fun complete(code: String, receipt: String): String {
                assertEquals("counted", remote.complete(code, receipt))
                throw java.io.IOException("Response lost after the server committed")
            }
        }
        val engine = DownloadAccounting(lossy, store)
        val receipt = requireNotNull(engine.issue(code))
        engine.completed(code, receipt)
        engine.retryPending()
        val pending = store.load().single()
        assertEquals(receipt.receipt, pending.receipt)
        assertEquals(1, pending.attempts)
        assertTrue(metadata.edit().putString("receipt", receipt.receipt)
            .putLong("retryAt", requireNotNull(pending.lastAttemptAt) + DownloadAccounting.RETRY_INTERVAL_MS).commit())
        capture("before-process-exit", "pid=${Process.myPid()} receipt=${pending.receipt} attempts=${pending.attempts}")
    }

    @Test fun b_newProcessResendsThePersistedReceiptWithoutCountingAgain() = runBlocking {
        try {
            assertTrue("first phase did not complete", metadata.contains("receipt"))
            assertNotEquals("Orchestrator must terminate the first target process", metadata.getInt("pid", 0), Process.myPid())
            val expectedReceipt = requireNotNull(metadata.getString("receipt", null))
            assertEquals(expectedReceipt, store.load().single().receipt)
            val remote = api()
            val observed = object : DownloadAccountingApi by remote {
                override suspend fun issue(code: String): DownloadReceipt? = error("must not reissue after restart")
                override suspend fun complete(code: String, receipt: String): String {
                    assertEquals(expectedReceipt, receipt)
                    return remote.complete(code, receipt).also { assertEquals("already_counted", it) }
                }
            }
            DownloadAccounting(observed, store) { metadata.getLong("retryAt", 0) }.retryPending()
            assertTrue(store.load().isEmpty())
            val count = if (demo) database("store/$code/downloadCount").trim().toInt()
                else metadata.getInt("count", 0)
            assertEquals(51, count)
            capture("after-process-restart", "oldPid=${metadata.getInt("pid", 0)} pid=${Process.myPid()} receipt=$expectedReceipt count=$count pending=0")
        } finally {
            file.delete()
            metadata.edit().clear().commit()
            if (demo) database("store/$code", "null")
        }
    }

    private fun api(): DownloadAccountingApi = if (demo) {
        HttpDownloadAccountingApi { "http://10.0.2.2:5051/demo-unipad/us-central1/" }
    } else object : DownloadAccountingApi {
        override suspend fun issue(code: String) = DownloadReceipt("process-test-receipt",
            System.currentTimeMillis() + DownloadAccounting.RETENTION_MS)
        override suspend fun complete(code: String, receipt: String): String {
            if (metadata.getBoolean("applied", false)) return "already_counted"
            check(metadata.edit().putBoolean("applied", true).putInt("count", 51).commit())
            return "counted"
        }
    }

    private fun database(path: String, value: String? = null): String {
        val connection = URL("http://10.0.2.2:9051/$path.json?ns=demo-unipad").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000; connection.readTimeout = 5_000
            if (value != null) {
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Authorization", "Bearer owner")
                connection.doOutput = true
                connection.outputStream.use { it.write(value.toByteArray()) }
            }
            assertEquals(200, connection.responseCode)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun capture(name: String, text: String) {
        assertTrue(TestCaptures.save(UiDevice.getInstance(instrumentation), "download-accounting/$name.txt") {
            it.writeText(text + "\n"); true
        })
    }
}
