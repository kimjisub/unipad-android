package com.kimjisub.launchpad.network.accounting

import android.content.Context
import android.os.Build
import android.util.AtomicFile
import com.google.gson.Gson
import com.kimjisub.launchpad.BuildConfig
import com.kimjisub.launchpad.manager.AppVisibility
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.FileNotFoundException

/** Process lifetime storage, but transmission exists only while an app screen is visible. */
class StoreDownloadAccounting private constructor(
    create: () -> DownloadAccounting,
    private val now: () -> Long,
) {
    constructor(context: Context) : this({
        val app = context.applicationContext
        DownloadAccounting(HttpDownloadAccountingApi { destination(app) },
            FilePendingDownloadStore(File(app.noBackupFilesDir, "download-completions.json")))
    }, System::currentTimeMillis)

    internal constructor(accounting: DownloadAccounting, now: () -> Long = System::currentTimeMillis) :
        this({ accounting }, now)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changed = Channel<Unit>(Channel.CONFLATED)
    private val accounting by lazy {
        try { create() } catch (_: Exception) { null }
    }

    init {
        scope.launch {
            AppVisibility.process.foreground.collectLatest { visible ->
                if (visible) {
                    while (isActive) {
                        accounting?.retryPending()
                        withTimeoutOrNull(DownloadAccounting.RETRY_INTERVAL_MS) { changed.receive() }
                    }
                }
            }
        }
    }

    /** Issuance is independent of the file transfer, including when it is slower or fails. */
    fun begin(code: String): Attempt = Attempt(code, scope.async { accounting?.issue(code) })

    inner class Attempt internal constructor(
        private val code: String,
        private val issued: Deferred<DownloadReceipt?>,
    ) {
        fun installed() {
            val completedAt = now()
            scope.launch {
                val receipt = issued.await() ?: return@launch
                accounting?.completed(code, receipt, completedAt)
                changed.trySend(Unit)
            }
        }

        fun cancelled() { issued.cancel() }
    }

    internal fun close() { scope.cancel() }

    companion object {
        @Suppress("DEPRECATION")
        private fun destination(app: Context): String? = DownloadAccountingRoute.select(
            debug = BuildConfig.DEBUG,
            localTest = BuildConfig.DOWNLOAD_ACCOUNTING_EMULATOR,
            emulator = Build.FINGERPRINT.startsWith("generic") ||
                Build.HARDWARE in setOf("ranchu", "goldfish") ||
                Build.MODEL.contains("sdk", ignoreCase = true) ||
                Build.MODEL.contains("emulator", ignoreCase = true),
            installer = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    app.packageManager.getInstallSourceInfo(app.packageName).installingPackageName
                } else app.packageManager.getInstallerPackageName(app.packageName)
            },
        )
    }
}

/** Atomic replacement prevents a process exit from leaving a partly written retry list. */
internal class FilePendingDownloadStore(file: File) : PendingDownloadStore {
    private val atomic = AtomicFile(file)
    private val gson = Gson()

    override fun load(): List<PendingDownload> {
        val stream = try { atomic.openRead() } catch (_: FileNotFoundException) { return emptyList() }
        return stream.use { input ->
            if (input.channel.size() > MAX_QUEUE_BYTES) throw IOException("Completion queue is too large")
            val bytes = input.readBytes()
            if (bytes.size > MAX_QUEUE_BYTES) throw IOException("Completion queue is too large")
            gson.fromJson(bytes.toString(Charsets.UTF_8), Array<PendingDownload>::class.java)
                ?.filter { it.code.isNotBlank() && it.receipt.isNotBlank() } ?: emptyList()
        }
    }

    override fun save(downloads: List<PendingDownload>) {
        val output = atomic.startWrite()
        try {
            output.write(gson.toJson(downloads).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (e: Exception) {
            atomic.failWrite(output)
            throw e
        }
    }

    companion object {
        private const val MAX_QUEUE_BYTES = 1_048_576
    }
}
