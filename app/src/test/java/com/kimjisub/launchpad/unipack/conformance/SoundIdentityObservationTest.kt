package com.kimjisub.launchpad.unipack.conformance

import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.tool.PackInstallTestEnv
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.serialization.json.*
import org.junit.Assert.assertNull
import org.junit.Assume.assumeNotNull
import org.junit.Test
import java.io.File
import java.time.Instant

/** An opt-in observation: case outcomes are data and are never asserted to pass. */
class SoundIdentityObservationTest {
    private val pretty = Json { prettyPrint = true }
    @Test fun recordHostImportAndRunnerSelections() {
        val output = System.getenv("SOUND_IDENTITY_OUT")
        assumeNotNull(output)
        val bytes = javaClass.getResourceAsStream("/sound-identity/corpus.json")!!.use { it.readBytes() }
        val digest = javaClass.getResourceAsStream("/sound-identity/corpus.sha256")!!.use { it.readBytes().decodeToString() }
        val corpus = SoundIdentityCorpus(bytes, digest)
        val env = PackInstallTestEnv()
        env.setUp()
        try {
            val filesystem = identityFileSystem(env.workspace)
            val results = corpus.cases.map { case ->
                val archive = corpus.archive(case)
                val recorder = PackInstallTestEnv.Recorder()
                env.finish(env.import(archive, recorder, fileName = case.getValue("id").jsonPrimitive.content + ".zip"))
                assertNull("Import failed: ${recorder.error}", recorder.error)
                val root = requireNotNull(recorder.installedFolder)
                val pack = UniPackFolder(root).load().loadDetail()
                soundIdentityOutcome(case, observeSoundIdentity(archive, root, pack) {
                    // Host selection only; this substitute does not decode audio or open a speaker.
                    OboeAudioEngine.DecodedAudio(ShortArray(1), 1, 1, 44_100)
                })
            }
            val report = buildJsonObject {
                put("platform", "android-host"); put("generatedAt", Instant.now().toString())
                put("appCommit", System.getenv("SOUND_IDENTITY_APP_COMMIT") ?: "unspecified")
                put("corpusSha256", corpus.sha256); put("filesystem", filesystem)
                put("assertions", "harness-only; case outcomes are observations")
                put("decoder", "substitute; native decoder unverified")
                put("cases", JsonArray(results))
            }
            File(requireNotNull(output)).writeText(pretty.encodeToString(JsonElement.serializer(), report))
            println("SOUND-IDENTITY-OBSERVATION $report")
        } finally { env.tearDown() }
    }
}
