package com.kimjisub.launchpad.basefeatures

import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.snapshot.IndexedNode
import com.google.firebase.database.snapshot.NodeUtilities
import com.kimjisub.launchpad.api.BaseApiService
import com.kimjisub.launchpad.api.file.FileApi
import com.kimjisub.launchpad.api.unipad.UniPadApi
import com.kimjisub.launchpad.network.Networks.FirebaseEvents
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.CopyOnWriteArrayList

/** Application interceptor returns real Retrofit bodies/ZIP streams without opening a socket. */
class FakeNetwork(zip: ByteArray) {
    val requests = CopyOnWriteArrayList<String>()
    val catalog = FakeEvents("store", mapOf("fixture" to mapOf(
        "code" to FeatureScreen.STORE_ID, "title" to "Offline Store Fixture", "producerName" to "Synthetic",
        "isAutoPlay" to true, "isLED" to true, "URL" to "https://fixture.invalid/store.zip"
    )))
    val count = FakeEvents("storeCount", 1L)
    private val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        requests += url
        val path = request.url.encodedPath
        val body = when {
            url == "https://api.unipad.io/unishare/BASEFEATURES" ->
                """{"_id":"basefeatures-share","title":"${FeatureScreen.TITLE}","producer":"Synthetic"}"""
                    .toResponseBody("application/json".toMediaType())
            url == "https://api.unipad.io/unishare/basefeatures-share/download" || url == "https://fixture.invalid/store.zip" ->
                zip.toResponseBody("application/zip".toMediaType())
            else -> throw AssertionError("Unexpected network request: $url ($path)")
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("Fixture").body(body).build()
    }).build()
    private val retrofit = Retrofit.Builder().baseUrl("https://api.unipad.io/").client(client)
        .addConverterFactory(GsonConverterFactory.create(BaseApiService.gson)).build()
    val module = module {
        single<FileApi.FileService> { retrofit.create(FileApi.FileService::class.java) }
        single<UniPadApi.UniPadApiService> { retrofit.create(UniPadApi.UniPadApiService::class.java) }
        single<FirebaseEvents>(named("store")) { catalog }
        single<FirebaseEvents>(named("storeCount")) { count }
    }

    /** Only synthesizes Firebase snapshots; never attaches a listener to Firebase itself. */
    class FakeEvents(private val key: String, private val data: Any) : FirebaseEvents {
        private var child: ChildEventListener? = null
        private var value: ValueEventListener? = null
        var attached = false
            private set
        override fun setEventListener(childEventListener: ChildEventListener) = apply { child = childEventListener }
        override fun setEventListener(valueEventListener: ValueEventListener) = apply { value = valueEventListener }
        override fun attachEventListener(bool: Boolean) = apply {
            attached = bool
            if (bool) {
                val ref = FirebaseDatabase.getInstance().getReference(key)
                // Firebase has no public snapshot builder. This test-only adapter constructs its
                // SDK snapshot without registering a network listener.
                val snapshot = DataSnapshot::class.java.getDeclaredConstructor(
                    com.google.firebase.database.DatabaseReference::class.java, IndexedNode::class.java
                ).apply { isAccessible = true }.newInstance(ref, IndexedNode.from(NodeUtilities.NodeFromJSON(data)))
                if (child != null) snapshot.children.forEach { child!!.onChildAdded(it, null) }
                value?.onDataChange(snapshot)
            }
        }
    }
}
