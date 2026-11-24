package com.kimjisub.launchpad.network.accounting

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** The legacy GET counts a started transfer. Resolve its uncounted HEAD instead. */
internal class StoreDownloadSource(
    private val legacyHost: String = "us-central1-unipad-e41ab.cloudfunctions.net",
) {

    companion object {
        private val sharedClient by lazy {
            OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build()
        }
    }

    suspend fun resolve(url: String): String {
        val parsed = url.toHttpUrlOrNull() ?: return url
        if (parsed.host != legacyHost ||
            parsed.encodedPath != "/downloadUniPackLegacy") return url
        return sharedClient.newCall(Request.Builder().url(parsed).head().build()).awaitResponse().use { response ->
            val destination = response.header("Location")?.let(parsed::resolve)
            if (response.code !in 300..399 || destination == null || destination.scheme != "https" ||
                (destination.host == legacyHost && destination.encodedPath in
                    setOf("/downloadUniPackLegacy", "/increaseDownloadCount"))) throw IOException("Pack download location unavailable")
            destination.toString()
        }
    }
}
