package com.kimjisub.launchpad.audio

import android.graphics.Point
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.basefeatures.FeatureScreen
import com.kimjisub.launchpad.basefeatures.Fingers
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.io.File

/** Real screen dispatch, decoder and Oboe output. The host, not this test, judges recorded sound. */
@RunWith(AndroidJUnit4::class)
class EmulatorOutputScreenTest {
    @Test fun drivesScreenForExternalOutput() {
        val screen = FeatureScreen()
        val arguments = InstrumentationRegistry.getArguments()
        val session = arguments.getString("audioOutputSession")
        require(session == null || session.matches(Regex("[a-f0-9]{32}")))
        val blocked = arguments.getString("audioBlockPlayback") == "true"
        // Deliberate fault for the detector's negative run: keep the real output stream
        // and decoder, but suppress play requests. This exists only in the test APK.
        val engine = object : SoundRunner.Engine by SoundRunner.OboeEngine {
            override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int =
                if (blocked) 0 else SoundRunner.OboeEngine.play(soundId, volumeL, volumeR, loop)
        }
        val binding = module { single<SoundRunner.Engine> { engine } }
        val fingers = Fingers(screen)
        loadKoinModules(binding)
        try {
            screen.assertDedicatedWorkspace()
            screen.cleanPacks()
            val pack = screen.pack().apply { mkdirs() }
            val assets = screen.instrumentation.context.assets
            for (name in listOf("info", "keySound")) {
                assets.open("audio-output/$name").use { input -> File(pack, name).outputStream().use(input::copyTo) }
            }
            File(pack, "sounds").mkdirs()
            assets.list("audio-output")!!.filter { it != "info" && it != "keySound" }.forEach { name ->
                assets.open("audio-output/$name").use { input -> File(pack, "sounds/$name").outputStream().use(input::copyTo) }
            }
            screen.openPlay(pack)
            val pads = screen.padBounds()
            assertTrue("Expected 64 screen pads", pads.size == 64)
            fun perform(command: JSONObject): JSONObject {
                val inputs = command.getJSONArray("pads")
                val indices = (0 until inputs.length()).map { inputs.getInt(it) }
                require(indices.distinct().size == indices.size && indices.all { it in pads.indices })
                val holdMs = command.getLong("hold_ms")
                require(holdMs in 0..2000)
                val event = JSONObject().put("input", command).put("blocked", blocked)
                    .put("pressed_epoch_ms", System.currentTimeMillis())
                val ids = indices.map { index ->
                    val pad = pads[index]
                    fingers.down(Point(pad.centerX(), pad.centerY()))
                }
                event.put("pressed_end_epoch_ms", System.currentTimeMillis())
                SystemClock.sleep(holdMs)
                event.put("released_epoch_ms", System.currentTimeMillis())
                ids.asReversed().forEach(fingers::up)
                event.put("released_end_epoch_ms", System.currentTimeMillis())
                return event.put("finished_epoch_ms", System.currentTimeMillis())
            }
            if (session == null) {
                // Ordinary connected suite checks fixture loading and actual screen input.
                // It makes no claim about external sound without the host recorder.
                perform(JSONObject("""{"pads":[0],"hold_ms":40}"""))
                return
            }
            val exchange = File(screen.context.filesDir, "audio-output/$session").apply { mkdirs() }
            File(exchange, "ready").writeText("ready")
            var number = 0
            val deadline = SystemClock.elapsedRealtime() + 180000
            while (SystemClock.elapsedRealtime() < deadline) {
                val command = File(exchange, "command-$number")
                if (!command.exists()) { SystemClock.sleep(10); continue }
                val input = command.readText().trim()
                if (input == "finish") return
                val event = perform(JSONObject(input))
                // Publish atomically: the host never reads a half-written event.
                val temp = File(exchange, "event-$number.tmp")
                temp.writeText(event.toString())
                assertTrue(temp.renameTo(File(exchange, "event-$number.json")))
                number++
            }
            throw AssertionError("Host stopped sending output-test commands")
        } finally {
            try { fingers.cancelRemaining(); screen.close() } finally { unloadKoinModules(binding) }
        }
    }
}
