package com.kimjisub.launchpad.basefeatures

import com.kimjisub.launchpad.api.BaseApiService
import com.kimjisub.launchpad.api.file.FileApi
import com.kimjisub.launchpad.network.StoreCatalog
import com.kimjisub.launchpad.network.fb.StoreVO
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList

/** Application interceptor returns real Retrofit bodies/ZIP streams without opening a socket. */
class FakeNetwork(zip: ByteArray) {
    val requests = CopyOnWriteArrayList<String>()
    val catalog = FakeCatalog()
    private val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        requests += url
        val path = request.url.encodedPath
        val body = when (url) {
            "https://fixture.invalid/store.zip" -> zip.toResponseBody("application/zip".toMediaType())
            else -> throw AssertionError("Unexpected network request: $url ($path)")
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture").body(body).build()
    }).build()
    private val retrofit = Retrofit.Builder().baseUrl("https://fixture.invalid/").client(client)
        .addConverterFactory(GsonConverterFactory.create(BaseApiService.gson)).build()
    val module = module {
        single<FileApi.FileService> { retrofit.create(FileApi.FileService::class.java) }
        single<StoreCatalog> { catalog }
    }

    /** Uses the same subscription contract as the production Firebase catalogue. */
    class FakeCatalog : StoreCatalog {
        var attached = false
            private set
        override fun attach(listener: StoreCatalog.Listener) {
            attached = true
            listener.onAdded(StoreVO(
                code = FeatureScreen.STORE_ID, title = "Offline Store Fixture", producerName = "Synthetic",
                isAutoPlay = true, isLED = true, URL = "https://fixture.invalid/store.zip"
            ), "fixture")
            listener.onCount(1L)
        }
        override fun detach() { attached = false }
    }
}
