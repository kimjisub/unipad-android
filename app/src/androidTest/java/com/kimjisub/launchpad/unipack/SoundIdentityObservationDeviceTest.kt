package com.kimjisub.launchpad.unipack

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.analytics.PackImportSource
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.analytics.UsageEventSink
import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.tool.UniPackImporter
import com.kimjisub.launchpad.unipack.conformance.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Records failures as observations; only import, tracing, and report creation are test assertions. */
@RunWith(AndroidJUnit4::class)
class SoundIdentityObservationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val pretty = Json { prettyPrint = true }

    @Test fun recordInternalAndExternalImportSelections() {
        val assets = instrumentation.context.assets
        val corpus = SoundIdentityCorpus(assets.open("sound-identity/corpus.json").use { it.readBytes() },
            assets.open("sound-identity/corpus.sha256").bufferedReader().use { it.readText() })
        val reports = mutableListOf<JsonObject>()
        val locations = listOf("internal" to context.filesDir,
            "external" to requireNotNull(context.getExternalFilesDir(null)))
        for ((label, storage) in locations) {
            val root = File(storage, "sound-identity-observation").apply { deleteRecursively(); check(mkdirs()) }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val filesystem = identityFileSystem(root, InstrumentationRegistry.getArguments().getString("${label}FileSystemType"))
                val cases = corpus.cases.map { case ->
                    val bytes = corpus.archive(case)
                    val archive = File(root, case.getValue("id").jsonPrimitive.content + ".zip").apply { writeBytes(bytes) }
                    val workspace = File(root, "workspace").apply { mkdirs() }
                    val recorder = Recorder()
                    UniPackImporter(context, Uri.fromFile(archive), { workspace }, recorder,
                        UsageAnalytics(UsageEventSink { _, _ -> }).packImport(PackImportSource.FILE), scope)
                    assertTrue("Import timed out", recorder.done.await(30, TimeUnit.SECONDS))
                    assertNull("Import failed: ${recorder.error}", recorder.error)
                    val installed = requireNotNull(recorder.folder)
                    val pack = UniPackFolder(installed).load().loadDetail()
                    soundIdentityOutcome(case, observeSoundIdentity(bytes, installed, pack, OboeAudioEngine::decodeOnly))
                        .also { logReport("$label-${case.getValue("id").jsonPrimitive.content}", it) }
                }
                reports += buildJsonObject {
                    put("storageLocation", label); put("filesystem", filesystem)
                    put("cases", JsonArray(cases))
                }
            } finally { scope.cancel(); root.deleteRecursively() }
        }
        val report = buildJsonObject {
            put("platform", "android-device"); put("generatedAt", Instant.now().toString())
            put("appCommit", InstrumentationRegistry.getArguments().getString("appCommit", "unspecified"))
            put("corpusSha256", corpus.sha256)
            put("assertions", "harness-only; case outcomes are observations")
            put("decoder", "native decoder; no audio output stream")
            put("filesystems", JsonArray(reports))
        }
        File(context.filesDir, "sound-identity-results.json").writeText(
            pretty.encodeToString(JsonElement.serializer(), report))
        logReport("complete", report)
        assertEquals(2, reports.size)
    }

    // Gradle removes the apps after instrumented tests. Chunked raw logs preserve the full report
    // and every completed case even if a later case fails or the app data is removed.
    private fun logReport(key: String, report: JsonObject) {
        val chunks = report.toString().chunked(900)
        chunks.forEachIndexed { index, chunk ->
            android.util.Log.i("SoundIdentityData", "$key:$index/${chunks.size}:$chunk")
        }
    }

    private class Recorder : UniPackImporter.OnEventListener {
        val done = CountDownLatch(1)
        @Volatile var folder: File? = null
        @Volatile var error: Throwable? = null
        override fun onImportStart() = Unit
        override fun onImportComplete(folder: File, unipack: UniPack, byteSize: Long) {
            this.folder = folder; done.countDown()
        }
        override fun onException(throwable: Throwable) { error = throwable; done.countDown() }
    }
}
