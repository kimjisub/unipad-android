package com.kimjisub.launchpad.network.accounting

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

/** Fixed destinations only. Test builds never fall back to production. */
internal object DownloadAccountingRoute {
    const val PRODUCTION = "https://us-central1-unipad-e41ab.cloudfunctions.net/"
    const val EMULATOR = "http://10.0.2.2:5051/demo-unipad/us-central1/"

    fun select(debug: Boolean, localTest: Boolean, emulator: Boolean, installer: () -> String?): String? {
        if (debug) return if (localTest) EMULATOR else null
        if (emulator) return null
        return try {
            if (installer() == "com.android.vending") PRODUCTION else null
        } catch (_: Exception) {
            null
        }
    }
}

/** No account, device identifiers, cookies, interceptors or request logging. */
class HttpDownloadAccountingApi(private val destination: () -> String?) : DownloadAccountingApi {
    private val client = OkHttpClient.Builder()
        .callTimeout(DownloadAccounting.REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val gson = Gson()

    override suspend fun issue(code: String): DownloadReceipt? {
        val json = post("issueDownloadReceipt", mapOf("code" to code)) ?: return null
        if (json.get("status")?.asString != "issued") return null
        return DownloadReceipt(json.get("receipt").asString, json.get("expiresAt").asLong)
    }

    override suspend fun complete(code: String, receipt: String): String =
        post("completeDownload", mapOf("code" to code, "receipt" to receipt))
            ?.get("status")?.asString ?: "deferred"

    private suspend fun post(function: String, payload: Map<String, String>): JsonObject? {
        val base = destination() ?: return null
        val request = Request.Builder().url(base + function)
            .post(gson.toJson(payload).toRequestBody("application/json".toMediaType())).build()
        return client.newCall(request).awaitResponse().use { response ->
            val source = response.body.source()
            source.request(MAX_RESPONSE_BYTES + 1)
            if (source.buffer.size > MAX_RESPONSE_BYTES) return@use null
            val json = JsonParser.parseString(source.readUtf8())
            if (json.isJsonObject) json.asJsonObject else null
        }
    }

    companion object {
        private const val MAX_RESPONSE_BYTES = 8_192L
    }
}

internal suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!continuation.isCancelled) continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            continuation.resume(response) { _, unconsumed, _ -> unconsumed.close() }
        }
    })
}
