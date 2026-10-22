package com.kimjisub.launchpad.basefeatures

import android.graphics.Point
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kimjisub.design.view.ChainView
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.viewmodel.PlayMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Raw device evidence, not a normalized conformance verdict. Fingers enter through window dispatch,
 * MIDI through the actual Launchpad driver, chains through their on-screen buttons, and auto play
 * through the live screen's view model and listeners. The native audio output is replaced by
 * RecordingAudio; its actual decoder and the app's sound/LED runners are retained.
 *
 * A successful JUnit check means the recorder completed, never that the corpus case passed. Each
 * saved case remains unverified: real-time snapshots cannot certify the corpus's virtual-clock
 * event ordering, and auto-play option gestures are not injected. Timestamps expose deadlines
 * crossed during window input rather than quietly treating those as the specified 20/50 ms steps.
 */
@RunWith(AndroidJUnit4::class)
class ConformanceScreenObservationTest : PlaybackScreenTest() {
    @Test fun recordManualChainSelection() = record("RUN-G606")
    @Test fun recordFirstFingerReleasedFirst() = record("RUN-G607")
    @Test fun recordScreenReleasedBeforeMidi() = record("RUN-G608")
    @Test fun recordManualReleaseDuringAutoPlay() = record("RUN-G609")
    @Test fun recordAutoPlayReleaseDuringManualHold() = record("RUN-G610")
    @Test fun recordAutoPlayStoppedBeforeWormhole() = record("RUN-G616")
    @Test fun recordSecondFingerReleasedFirst() = record("RUN-G617")
    @Test fun recordMidiReleasedBeforeScreen() = record("RUN-G618")

    private fun record(id: String) {
        val pack = screen.pack()
        val (case, digest) = DeviceConformanceCorpus(screen).install(id, pack)
        assertTrue("Only screen cases belong in this recorder", case.getBoolean("screenInputs"))
        screen.openPlay(pack)
        val vm = screen.vm()
        val fingers = Fingers(screen)
        val pointerIds = mutableMapOf<String, Int>()
        var midi: VirtualLaunchpad? = null
        val snapshots = JSONArray()
        val steps = JSONArray()
        val started = SystemClock.elapsedRealtime()
        val chainEvents = CopyOnWriteArrayList<List<Long>>()
        screen.onMain { vm.chain.addObserver { current, _ ->
            chainEvents += listOf(SystemClock.elapsedRealtime() - started, current.toLong())
        } }
        val report = JSONObject()
            .put("platform", "android")
            .put("id", id)
            .put("fingerprint", case.getString("fingerprint"))
            .put("corpusSha256", digest)
            .put("status", "unverified")
            .put("nativeAudioOutput", false).put("nativeDecoder", true)
            .put("detail", "Real screen snapshots; event order and auto-play option gestures are not certified")
            .put("steps", steps).put("snapshots", snapshots)
        var complete = false
        try {
            screen.onMain {
                vm.scbFeedbackLight.setChecked(false)
                vm.scbLed.setChecked(true)
            }
            val scenario = case.getJSONArray("scenario")
            for (index in 0 until scenario.length()) {
                val step = scenario.getJSONObject(index)
                val delivered = JSONObject().put("step", step).put("elapsedMs", SystemClock.elapsedRealtime() - started)
                steps.put(delivered)
                when (val action = step.getString("do")) {
                    "watchStops" -> Unit // Every snapshot includes the real runner's stop keys.
                    "press", "release" -> {
                        val input = step.optString("input", "pointer:1")
                        val down = action == "press"
                        val x = step.getInt("x")
                        val y = step.getInt("y")
                        when {
                            input.startsWith("pointer:") -> {
                                if (down) {
                                    check(input !in pointerIds) { "Repeated pointer down: $input" }
                                    val bounds = screen.padBounds()[x * vm.unipack.buttonY + y]
                                    val point = Point(bounds.centerX() + if (pointerIds.isEmpty()) -bounds.width() / 4 else bounds.width() / 4, bounds.centerY())
                                    val pointer = fingers.down(point)
                                    pointerIds[input] = pointer
                                    delivered.put("pointerId", pointer).put("screenPoint", JSONArray().put(point.x).put(point.y))
                                } else {
                                    val pointer = pointerIds.remove(input) ?: error("No held pointer: $input")
                                    delivered.put("pointerId", pointer)
                                    fingers.up(pointer)
                                }
                            }
                            input == "midi:1" -> {
                                val controller = midi ?: VirtualLaunchpad(screen).also { midi = it }
                                delivered.put("midiPacket", JSONArray(listOf(9, -112, x * 16 + y, if (down) 127 else 0)))
                                screen.onMain { controller.driver.getSignal(9, -112, x * 16 + y, if (down) 127 else 0) }
                            }
                            else -> error("Unregistered screen input: $input")
                        }
                    }
                    "chain" -> {
                        val chain = step.getInt("c")
                        val bounds = screen.onMain {
                            screen.views(screen.resumed().window.decorView).filterIsInstance<ChainView>()
                                .filter { it.width > 0 }.map(screen::bounds)[chain]
                        }
                        val pointer = fingers.down(Point(bounds.centerX(), bounds.centerY()))
                        delivered.put("pointerId", pointer)
                        fingers.up(pointer)
                    }
                    "autoplay" -> screen.onMain { vm.switchPlayMode(PlayMode.AutoPlay) }
                    "autoplayOff" -> screen.onMain { vm.switchPlayMode(PlayMode.None) }
                    "advance" -> SystemClock.sleep(step.getLong("ms"))
                    "observe" -> {
                        val snapshot = screen.onMain {
                            val lights = JSONArray()
                            for (x in 0 until vm.unipack.buttonX) for (y in 0 until vm.unipack.buttonY) {
                                vm.channelManager.get(x, y)?.let {
                                    lights.put(JSONArray().put(x).put(y).put("%08x".format(it.color)).put(it.code))
                                }
                            }
                            JSONObject().put("elapsedMs", SystemClock.elapsedRealtime() - started)
                                .put("chain", vm.chain.value).put("lights", lights)
                                .put("chainEvents", JSONArray(chainEvents.toList()))
                                .put("plays", JSONArray(audio.plays.toList()))
                                .put("loops", JSONArray(audio.loops.toList()))
                                .put("stoppedVoices", JSONArray(audio.stoppedVoices.toList()))
                                .put("ringingLoops", JSONArray(audio.ringingLoops()))
                                .put("engineStops", audio.stops.get()).put("silences", audio.silences.get())
                                .put("loaded", JSONObject(audio.loaded.toMap()))
                        }
                        snapshots.put(snapshot)
                    }
                    else -> error("Unregistered screen action: $action")
                }
                delivered.put("completedMs", SystemClock.elapsedRealtime() - started)
            }
            screen.capture("conformance-$id")
            complete = true
        } finally {
            // Save partial evidence even if injection or the activity failed, before cleanup changes it.
            report.put("completed", complete)
            try {
                assertTrue("Screen evidence was not saved", TestCaptures.save(screen.device, "conformance-screen/$id.json") {
                    it.writeText(report.toString(2) + "\n")
                    true
                })
            } finally {
                try { fingers.cancelRemaining() } finally { midi?.close() }
            }
        }
    }

}
