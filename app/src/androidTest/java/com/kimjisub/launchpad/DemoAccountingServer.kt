package com.kimjisub.launchpad

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.network.accounting.DownloadAccountingRoute
import org.junit.Assert.assertEquals
import java.net.HttpURLConnection
import java.net.URL

/**
 * The local Firebase emulators (project `demo-unipad`) that the download accounting device tests
 * use when run with `downloadAccountingBackend=demo-unipad`; otherwise they use in-process fakes.
 *
 * From Android 17 an app that targets it reaches the emulator's host address (10.0.2.2) only
 * while it holds ACCESS_LOCAL_NETWORK, which only the debug manifest declares. [enabled] grants
 * it before the first request; the grant stays with the package across test processes.
 */
object DemoAccountingServer {
    const val FUNCTIONS = DownloadAccountingRoute.EMULATOR
    private const val DATABASE = "http://10.0.2.2:9051/"
    private const val ANDROID_17 = 37
    private const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

    val enabled: Boolean by lazy {
        val selected = InstrumentationRegistry.getArguments().getString("downloadAccountingBackend") == "demo-unipad"
        if (selected && Build.VERSION.SDK_INT >= ANDROID_17) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, ACCESS_LOCAL_NETWORK)
        }
        selected
    }

    /** Reads a database path, or replaces it with [json] first when given, as the rules' owner. */
    fun database(path: String, json: String? = null): String {
        val connection = URL("$DATABASE$path.json?ns=demo-unipad").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 5_000; connection.readTimeout = 5_000
            if (json != null) {
                connection.requestMethod = "PUT"
                connection.setRequestProperty("Authorization", "Bearer owner")
                connection.doOutput = true
                connection.outputStream.use { it.write(json.toByteArray()) }
            }
            assertEquals(200, connection.responseCode)
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }
}
