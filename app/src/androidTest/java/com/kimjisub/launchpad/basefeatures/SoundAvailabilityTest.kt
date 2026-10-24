package com.kimjisub.launchpad.basefeatures

import android.os.SystemClock
import com.kimjisub.launchpad.TestCaptures
import com.kimjisub.launchpad.activity.PlayActivity
import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.midi.controller.MidiController
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real decoder and Oboe stream. Records requests, not external PCM or physical MIDI input. */
class SoundAvailabilityTest {
    private lateinit var screen: FeatureScreen
    private lateinit var binding: Module
    private val decodeGate = CountDownLatch(1)
    private var holdDecode = false
    private val decodedNames = ConcurrentHashMap<OboeAudioEngine.DecodedAudio, String>()
    private val loadedNames = ConcurrentHashMap<Int, String>()
    private val requests = CopyOnWriteArrayList<String>()

    @Before fun setUp() {
        screen = FeatureScreen()
        screen.assertDedicatedWorkspace()
        screen.cleanPacks()
        screen.install()
        val engine = object : SoundRunner.Engine by SoundRunner.OboeEngine {
            override fun decode(file: File): OboeAudioEngine.DecodedAudio? {
                if (holdDecode) check(decodeGate.await(10, TimeUnit.SECONDS)) { "Decode gate was not released" }
                return SoundRunner.OboeEngine.decode(file)?.also { decodedNames[it] = file.name }
            }
            override fun load(decoded: OboeAudioEngine.DecodedAudio): Int =
                SoundRunner.OboeEngine.load(decoded).also { loadedNames[it] = decodedNames.getValue(decoded) }
            override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int {
                requests += loadedNames.getValue(soundId)
                return SoundRunner.OboeEngine.play(soundId, volumeL, volumeR, loop)
            }
        }
        binding = module { single<SoundRunner.Engine> { engine } }
        loadKoinModules(binding)
    }

    @After fun tearDown() {
        decodeGate.countDown()
        try { if (::screen.isInitialized) screen.close() }
        finally { if (::binding.isInitialized) unloadKoinModules(binding) }
    }

    private fun prepare(wormhole: Boolean) {
        val pack = screen.pack()
        File(pack, "info").apply { writeText(readText().replace(Regex("(?m)^chain=.*$"), "chain=2")) }
        val assets = screen.instrumentation.context.assets
        for ((target, source) in listOf("a.wav" to "pcm.wav", "b.wav" to "left.wav")) {
            assets.open("audio-output/$source").use { input -> File(pack, "sounds/$target").outputStream().use(input::copyTo) }
        }
        File(pack, "keySound").writeText("1 1 1 a.wav 1${if (wormhole) " 2" else ""}\n1 1 1 b.wav\n")
        File(pack, "keyLED").listFiles().orEmpty().forEach { it.delete() }
        File(pack, "keyLED/1 1 1 1").writeText("on 1 1 a 4\nd 60000\n")
        File(pack, "keyLED/1 1 1 2").writeText("on 1 1 a 21\nd 60000\n")
    }

    private fun tapController() = screen.onMain {
        val controller = PlayActivity::class.java.getDeclaredField("midiController")
            .apply { isAccessible = true }.get(screen.resumed()) as MidiController
        controller.onPadTouch(0, 0, true, 127)
        controller.onPadTouch(0, 0, false, 0)
    }

    private fun capture(name: String) {
        val drawn = CountDownLatch(1)
        screen.onMain {
            val decor = screen.resumed().window.decorView
            decor.postOnAnimation { decor.postOnAnimation { drawn.countDown() } }
        }
        assertTrue("Updated screen did not draw", drawn.await(10, TimeUnit.SECONDS))
        val state = screen.onMain {
            val vm = screen.vm()
            "loading=${vm.soundLoadingActive}\nchain=${vm.chain.value + 1}\nnext_sound=${vm.unipack.soundGet(0, 0, 0)?.file?.name}\npack_light=${vm.channelManager.get(0, 0)?.takeIf { it.channel == Channel.LED }?.code}\nrequests=${requests.joinToString()}\n"
        }
        screen.capture(name)
        assertTrue(TestCaptures.save(screen.device, "basefeatures-captures/$name.txt") { it.writeText(state); true })
    }

    private fun checkLoading(wormhole: Boolean) {
        prepare(wormhole)
        holdDecode = true
        screen.launch(PlayActivity::class.java) { putExtra("path", screen.pack().path) }
        screen.await("Controller layout was not ready during audio loading") {
            screen.onMain { screen.vm().uiLoaded && screen.vm().soundLoadingActive && screen.vm().soundRunner != null }
        }
        tapController()
        SystemClock.sleep(250)
        capture(if (wormhole) "availability-loading-chain" else "availability-loading-order")
        assertEquals(emptyList<String>(), requests.toList())
        decodeGate.countDown()
        screen.await("Audio did not finish loading") { screen.onMain { !screen.vm().soundLoadingActive } }
        if (wormhole) {
            assertEquals(1, screen.onMain { screen.vm().chain.value })
        } else {
            tapController()
            screen.await("The following sound was not requested") { requests.isNotEmpty() }
            SystemClock.sleep(50)
            capture("availability-ready-next-sound")
            assertEquals(listOf("b.wav"), requests.toList())
            assertEquals(21, screen.onMain { screen.vm().channelManager.get(0, 0)?.code })
        }
    }

    @Test fun aControllerPressDuringLoadingConsumesItsSoundAndLightTurn() = checkLoading(false)
    @Test fun aControllerPressDuringLoadingStillMovesTheChain() = checkLoading(true)

    private fun checkClosedStream(wormhole: Boolean) {
        prepare(wormhole)
        screen.openPlay()
        screen.onMain { screen.vm().soundRunner!!.pauseStream() }
        tapController()
        SystemClock.sleep(250)
        capture(if (wormhole) "availability-closed-chain" else "availability-closed-order")
        assertEquals(listOf("a.wav"), requests.toList())
        if (wormhole) {
            assertEquals(1, screen.onMain { screen.vm().chain.value })
        } else {
            screen.onMain { screen.vm().soundRunner!!.resumeStream() }
            tapController()
            SystemClock.sleep(50)
            capture("availability-resumed-next-sound")
            assertEquals(listOf("a.wav", "b.wav"), requests.toList())
            assertEquals(21, screen.onMain { screen.vm().channelManager.get(0, 0)?.code })
        }
    }

    @Test fun aControllerPressWithTheStreamClosedConsumesItsSoundAndLightTurn() = checkClosedStream(false)
    @Test fun aControllerPressWithTheStreamClosedStillMovesTheChain() = checkClosedStream(true)
}
