package com.kimjisub.launchpad.network.accounting

import com.google.firebase.perf.network.FirebasePerfOkHttpClient
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import okhttp3.Call
import okhttp3.Callback
import org.junit.rules.ExternalResource

/** Android's profiling bridge needs an Application. Keep the real HTTP call in JVM tests. */
class FirebaseNetworkRule : ExternalResource() {
    override fun before() {
        mockkStatic(FirebasePerfOkHttpClient::class)
        every { FirebasePerfOkHttpClient.enqueue(any(), any()) } answers {
            firstArg<Call>().enqueue(secondArg<Callback>())
        }
    }

    override fun after() { unmockkStatic(FirebasePerfOkHttpClient::class) }
}
