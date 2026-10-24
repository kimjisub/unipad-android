package com.kimjisub.launchpad.audio

import android.graphics.Point
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.audio.OboeAudioEngine.DecodedAudio
import com.kimjisub.launchpad.basefeatures.FeatureScreen
import com.kimjisub.launchpad.basefeatures.Fingers
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real screen dispatch, decoder and Oboe output. The host, not this test, judges recorded sound. */
@RunWith(AndroidJUnit4::class)
class EmulatorOutputScreenTest {
    @Test fun drivesScreenForExternalOutput() {
        val screen = FeatureScreen()
        val arguments = InstrumentationRegistry.getArguments()
        val session = arguments.getString("audioOutputSession")
        require(session == null || session.matches(Regex("[a-f0-9]{32}")))
        val blocked = arguments.getString("audioBlockPlayback") == "true"
        val loadingOrder = arguments.getString("audioLoadingOrder") == "true"
        require(!loadingOrder || session != null)
        val decodeGate = CountDownLatch(if (loadingOrder) 1 else 0)
        val loadingKeySound = "1 1 1 pcm.wav\n1 1 1 left.wav\n"
        // Deliberate fault for the detector's negative run: keep the real output stream
        // and decoder, but suppress play requests. This exists only in the test APK.
        val engine = object : SoundRunner.Engine by SoundRunner.OboeEngine {
            override fun decode(file: File): DecodedAudio? {
                check(decodeGate.await(90, TimeUnit.SECONDS)) { "Host did not release the loading gate" }
                return SoundRunner.OboeEngine.decode(file)
            }
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
            if (loadingOrder) {
                File(pack, "keySound").writeText(loadingKeySound)
                screen.launch(PlayActivity::class.java) { putExtra("path", pack.path) }
                screen.await("Controller was not ready during audio loading") {
                    screen.onMain { screen.vm().uiLoaded && screen.vm().soundLoadingActive && screen.vm().soundRunner != null }
                }
            } else screen.openPlay(pack)
            fun perform(command: JSONObject): JSONObject {
                val pads = screen.padBounds()
                assertTrue("Expected 64 screen pads", pads.size == 64)
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
            var prepared = false
            val deadline = SystemClock.elapsedRealtime() + 180000
            while (SystemClock.elapsedRealtime() < deadline) {
                val preparation = File(exchange, "prepare-$number")
                if (loadingOrder && !prepared && preparation.exists()) {
                    require(preparation.readText().trim() == "loading")
                    val event = JSONObject().put("runtime_key_sound", File(pack, "keySound").readText())
                        .put("loading_before", screen.onMain { screen.vm().soundLoadingActive })
                    assertTrue(event.getBoolean("loading_before"))
                    screen.onMain {
                        val controller = PlayActivity::class.java.getDeclaredField("midiController")
                            .apply { isAccessible = true }.get(screen.resumed()) as MidiController
                        controller.onPadTouch(0, 0, true, 127)
                        controller.onPadTouch(0, 0, false, 0)
                    }
                    event.put("silent_press_count", 1)
                    decodeGate.countDown()
                    screen.await("Audio did not finish loading") { screen.onMain { !screen.vm().soundLoadingActive } }
                    event.put("loading_after", screen.onMain { screen.vm().soundLoadingActive })
                        .put("next_sound", screen.onMain { screen.vm().unipack.soundGet(0, 0, 0)?.file?.name })
                    val temp = File(exchange, "prepared-$number.tmp")
                    temp.writeText(event.toString())
                    assertTrue(temp.renameTo(File(exchange, "prepared-$number.json")))
                    prepared = true
                }
                val command = File(exchange, "command-$number")
                if (!command.exists()) { SystemClock.sleep(10); continue }
                val input = command.readText().trim()
                if (input == "finish") return
                check(!loadingOrder || prepared) { "Sound input arrived before loading preparation" }
                val event = perform(JSONObject(input))
                // Publish atomically: the host never reads a half-written event.
                val temp = File(exchange, "event-$number.tmp")
                temp.writeText(event.toString())
                assertTrue(temp.renameTo(File(exchange, "event-$number.json")))
                number++
            }
            throw AssertionError("Host stopped sending output-test commands")
        } finally {
            decodeGate.countDown()
            try { fingers.cancelRemaining(); screen.close() } finally { unloadKoinModules(binding) }
        }
    }
}
