package com.kimjisub.launchpad.unipack.conformance

import android.os.SystemClock
import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.manager.LaunchpadColor
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.AutoPlayRunner
import com.kimjisub.launchpad.unipack.runner.LedRunner
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import com.kimjisub.launchpad.viewmodel.FakeMainDispatcher
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.nio.file.Files
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs a corpus case through Android's real parser and runners and reports what they did in the
 * normalized form the corpus states. The expected side is never visible here.
 *
 * `Deps` names the two calls the self-checks leave out to prove a case fails without them.
 */
class Deps(
	val loadPack: (File) -> UniPack = { UniPackFolder(it).load().loadDetail() },
	val wrapLedRunner: (LedRunner) -> LedRunner = { it },
)

private fun hex8(value: Int) = "%08x".format(value)

class CaseResult(val actual: JsonElement, val outcome: Outcome)

/** What Android reports for a case: its result and status, or why the corpus says it is not observed here. */
fun resultFor(c: CorpusCase, corpus: Corpus, deps: Deps = Deps()): CaseResult {
	val unobserved = c.unobserved[PLATFORM]
	if (unobserved != null) return CaseResult(JsonNull, Outcome("unverified", false, unobserved))
	val actual = actualFor(c, corpus, deps)
	return CaseResult(actual, classify(c, actual))
}

fun actualFor(c: CorpusCase, corpus: Corpus, deps: Deps = Deps()): JsonElement {
	if (c.layer == "palette") {
		return buildJsonObject { putJsonArray("argb") { LaunchpadColor.ARGB.forEach { add(JsonPrimitive(hex8(it.toInt()))) } } }
	}
	val root = Files.createTempDirectory("unipack-conformance").toFile()
	try {
		c.writeTo(root, corpus)
		val pack = deps.loadPack(root)
		if (pack.criticalError) return buildJsonObject { put("loaded", false) }
		return if (c.layer == "parse") parseResult(pack, root) else runScenario(c, pack, root, deps)
	} finally {
		root.deleteRecursively()
	}
}

private fun parseResult(pack: UniPack, root: File): JsonElement {
	val soundsDir = File(root, "sounds")
	return buildJsonObject {
		put("loaded", true)
		putJsonObject("info") {
			put("title", pack.title)
			put("producerName", pack.producerName)
			put("buttonX", pack.buttonX)
			put("buttonY", pack.buttonY)
			put("chain", pack.chain)
			put("squareButton", pack.squareButton)
			pack.website?.takeIf { it.isNotEmpty() }?.let { put("website", it) }
		}
		putJsonArray("sounds") {
			val table = pack.soundTable ?: return@putJsonArray
			for (c in 0 until pack.chain) for (x in 0 until pack.buttonX) for (y in 0 until pack.buttonY) {
				val queue = table[c][x][y]?.takeIf { it.isNotEmpty() } ?: continue
				add(buildJsonObject {
					put("c", c); put("x", x); put("y", y)
					putJsonArray("queue") {
						for (s in queue) add(buildJsonObject {
							put("file", s.file.relativeTo(soundsDir).path.replace(File.separatorChar, '/'))
							put("loop", s.loop)
							put("wormhole", s.wormhole)
						})
					}
				})
			}
		}
		put("keyLedExist", pack.keyLedExist)
		putJsonArray("leds") {
			val table = pack.ledAnimationTable ?: return@putJsonArray
			for (c in 0 until pack.chain) for (x in 0 until pack.buttonX) for (y in 0 until pack.buttonY) {
				val queue = table[c][x][y]?.takeIf { it.isNotEmpty() } ?: continue
				add(buildJsonObject {
					put("c", c); put("x", x); put("y", y)
					putJsonArray("queue") {
						for (a in queue) add(buildJsonObject {
							put("loop", a.loop)
							putJsonArray("events") { a.ledEvents.forEach { add(ledEventJson(it)) } }
						})
					}
				})
			}
		}
		val autoPlay = pack.autoPlayTable
		if (autoPlay == null) put("autoPlay", JsonNull)
		else putJsonArray("autoPlay") { autoPlay.elements.forEach { add(autoPlayElementJson(it)) } }
		putJsonArray("errors") { pack.errorDetail?.lines()?.forEach { add(JsonPrimitive(normalizeError(it))) } }
	}
}

private fun ledEventJson(e: LedEvent): JsonElement = buildJsonArray {
	when (e) {
		is LedEvent.On -> { add(JsonPrimitive("on")); add(JsonPrimitive(e.x)); add(JsonPrimitive(e.y)); add(JsonPrimitive(hex8(e.color))); add(JsonPrimitive(e.velocity)) }
		is LedEvent.Off -> { add(JsonPrimitive("off")); add(JsonPrimitive(e.x)); add(JsonPrimitive(e.y)) }
		is LedEvent.Delay -> { add(JsonPrimitive("delay")); add(JsonPrimitive(e.delay)) }
		is LedEvent.Chain -> { add(JsonPrimitive("chain")); add(JsonPrimitive(e.chain)) }
	}
}

private fun autoPlayElementJson(e: AutoPlay.Element): JsonElement = buildJsonArray {
	when (e) {
		is AutoPlay.Element.On -> { add(JsonPrimitive("on")); add(JsonPrimitive(e.x)); add(JsonPrimitive(e.y)); add(JsonPrimitive(e.currChain)); add(JsonPrimitive(e.num)) }
		is AutoPlay.Element.Off -> { add(JsonPrimitive("off")); add(JsonPrimitive(e.x)); add(JsonPrimitive(e.y)); add(JsonPrimitive(e.currChain)) }
		is AutoPlay.Element.Chain -> { add(JsonPrimitive("chain")); add(JsonPrimitive(e.c)) }
		is AutoPlay.Element.Delay -> { add(JsonPrimitive("delay")); add(JsonPrimitive(e.delay)) }
	}
}

private fun event(vararg parts: Any): JsonElement = buildJsonArray {
	for (p in parts) add(if (p is Int) JsonPrimitive(p) else JsonPrimitive(p as String))
}

/** Stands in for the native audio engine and reports which file each `play` call started. */
private class RecordingEngine(private val onPlay: (file: File, loop: Int) -> Unit) : SoundRunner.Engine {
	private val decoded = IdentityHashMap<OboeAudioEngine.DecodedAudio, File>()
	private val fileById = HashMap<Int, File>()
	private var nextId = 0

	override fun start() = true
	override fun stop() {}

	override fun decode(file: File): OboeAudioEngine.DecodedAudio =
		OboeAudioEngine.DecodedAudio(ShortArray(1), 1, 1, 44_100).also { synchronized(this) { decoded[it] = file } }

	override fun load(decoded: OboeAudioEngine.DecodedAudio): Int = synchronized(this) {
		nextId++.also { fileById[it] = this.decoded.getValue(decoded) }
	}

	override fun unloadSound(soundId: Int) {}
	override fun unloadAll() {}

	override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int {
		onPlay(synchronized(this) { fileById.getValue(soundId) }, loop)
		return 0
	}

	override fun stopVoice(stopKey: Int) {}
	override fun stopAllVoices() {}
}

/**
 * One virtual clock for SystemClock, the LED runner's tick (called the way its coroutine calls it)
 * and the runners' threads. The auto-play runner polls the clock on its own thread, so every
 * change of time waits until that thread has read the new value and finished the pass that read it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class ScenarioRun(val c: CorpusCase, val pack: UniPack, root: File, val deps: Deps) {
	private val soundsDir = File(root, "sounds")
	private val testThread = Thread.currentThread()
	private val clock = AtomicLong(1000)
	private val watchFrom = AtomicLong(Long.MAX_VALUE)
	private val watchReads = AtomicInteger(0)
	private val events = java.util.Collections.synchronizedList(ArrayList<JsonElement>())
	private val chainLog = java.util.Collections.synchronizedList(ArrayList<Int>())
	private val chainTag = ThreadLocal<String?>()

	private val main = FakeMainDispatcher()
	private val vm = PlayActivityViewModel(mockk<UnipackRepository>())
	private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
	private val loopMethod = LedRunner::class.java.getDeclaredMethod("loop").apply { isAccessible = true }
	private val lit = HashMap<String, Pair<Int, Int>>()
	private val wormholes = ArrayList<Triple<Long, Int, Int>>()
	private var pressChain = 0
	private var pressX = 0
	private var pressY = 0

	private lateinit var led: LedRunner
	private lateinit var sound: SoundRunner
	private var autoPlay: AutoPlayRunner? = null

	fun run(): List<JsonElement> {
		Dispatchers.setMain(main)
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers {
			val now = clock.get()
			if (Thread.currentThread() !== testThread && now >= watchFrom.get()) watchReads.incrementAndGet()
			now
		}
		try {
			setUp()
			val checkpoints = ArrayList<JsonElement>()
			for (step in c.scenario) {
				val x = step["x"]?.jsonPrimitive?.int
				val y = step["y"]?.jsonPrimitive?.int
				when (val name = step.getValue("do").jsonPrimitive.content) {
					"press" -> press(x!!, y!!, true)
					"release" -> press(x!!, y!!, false)
					"chain" -> silently { vm.chain.value = step.getValue("c").jsonPrimitive.int }
					"advance" -> advance(step.getValue("ms").jsonPrimitive.int)
					"observe" -> synchronized(events) { checkpoints.add(jsonArrayOf(events)); events.clear() }
					"autoplay" -> startAutoPlay()
					"stop" -> stop()
					else -> error("${c.id}: unknown scenario step $name")
				}
			}
			return checkpoints
		} finally {
			autoPlay?.stop()
			if (::led.isInitialized) led.stop()
			if (::sound.isInitialized) sound.destroy()
			scope.cancel()
			unmockkStatic(SystemClock::class)
			Dispatchers.resetMain()
		}
	}

	private fun jsonArrayOf(items: List<JsonElement>): JsonElement = buildJsonArray { items.forEach { add(it) } }

	private fun silently(block: () -> Unit) {
		chainTag.set("")
		try { block() } finally { chainTag.set(null) }
	}

	private fun setUp() {
		vm.unipack = pack
		vm.channelManager = ChannelManager(pack.buttonX, pack.buttonY)
		// Pro light mode on. Off, which is the play screen's default (PlayActivityViewModel.proLightMode),
		// hides the round and logo lights of a keyLED file, and the cases that light them would see nothing.
		vm.channelManager.setCirIgnore(ChannelManager.Channel.LED, false)
		vm.chain.range = 0 until pack.chain
		vm.scbFeedbackLight.setChecked(false)
		vm.scbLed.setChecked(true)
		vm.uiCallback = LedSink()
		vm.chain.addObserver { curr, _ ->
			val tag = chainTag.get()
			if (tag == null) {
				chainLog.add(curr)
				events.add(event("chain", curr))
			} else if (tag.isNotEmpty()) events.add(event(tag, curr))
		}

		led = deps.wrapLedRunner(LedRunner(pack, vm.ledRunnerListener, vm.chain, 3_600_000L))
		vm.ledRunner = led
		watchFrom.set(0)
		vm.screenVisible = true
		waitUntil("the LED runner's first tick") { watchReads.get() >= 1 }
		led.afterDelivery { }
		watchFrom.set(Long.MAX_VALUE)

		val loaded = CountDownLatch(1)
		var failure: Throwable? = null
		sound = SoundRunner(pack, vm.chain, object : SoundRunner.LoadingListener {
			override fun onStart(soundCount: Int) {}
			override fun onProgressTick() {}
			override fun onEnd() = loaded.countDown()
			override fun onException(throwable: Throwable) { failure = throwable; loaded.countDown() }
		}, scope, RecordingEngine { file, loop ->
			val name = file.relativeTo(soundsDir).path.replace(File.separatorChar, '/')
			events.add(event("sound", pressChain, pressX, pressY, name, loop))
		})
		check(loaded.await(10, TimeUnit.SECONDS)) { "${c.id}: the sounds did not finish loading" }
		failure?.let { throw AssertionError("${c.id}: sound loading failed", it) }
		vm.soundRunner = sound
	}

	/**
	 * The pad LED and round LED output of PlayActivity. The view model calls setLedPad again for a pad
	 * whose light did not change (a release refreshes it), so only a change of the pad's light counts.
	 */
	private inner class LedSink : PlayActivityViewModel.UiCallback {
		override fun setLedPad(x: Int, y: Int) = seen(x, y)
		override fun setLedChain(c: Int) = seen(-1, c)

		private fun seen(x: Int, y: Int) {
			val item = vm.channelManager.get(x, y)
			val key = "$x,$y"
			if (item != null) {
				val light = item.color to item.code
				if (lit.put(key, light) != light) events.add(event("ledOn", x, y, hex8(item.color), item.code))
			} else if (lit.remove(key) != null) {
				events.add(event("ledOff", x, y))
			}
		}

		override fun updateTraceLogOverlay() {}
		override fun showToast(resId: Int) {}
		override fun finishActivity() {}
		override fun copyToClipboard(text: String) {}
		override fun setChainViewVisibility(index: Int, visibility: Int) {}
		override fun startGuideAnimation(x: Int, y: Int, targetWallTimeMs: Long) {}
		override fun stopGuideAnimation(x: Int, y: Int) {}
		override fun sendGuideLedToLaunchpad(x: Int, y: Int, velocity: Int) {}
		override fun onRequestRelayout() {}
	}

	private fun press(x: Int, y: Int, down: Boolean) {
		pressChain = vm.chain.value
		pressX = x
		pressY = y
		if (down) {
			val wormhole = pack.soundGet(pressChain, x, y)?.takeIf { it.id >= 0 }?.wormhole
			if (wormhole != null && wormhole != -1) wormholes.add(Triple(clock.get() + 100, wormhole, chainLog.size))
		}
		vm.padTouch(x, y, down)
		main.runAll()
	}

	private fun startAutoPlay() {
		val listener = object : AutoPlayRunner.Listener {
			override fun onStart() {}
			override fun onPadTouchOn(x: Int, y: Int) { events.add(event("autoOn", x, y)) }
			override fun onPadTouchOff(x: Int, y: Int) { events.add(event("autoOff", x, y)) }
			override fun onChainChange(c: Int) {
				chainTag.set("autoChain")
				try { vm.chain.value = c } finally { chainTag.set(null) }
			}
			override fun onGuidePadOn(x: Int, y: Int, targetWallTimeMs: Long) {}
			override fun onGuidePadOff(x: Int, y: Int) {}
			override fun onGuideLedUpdate(x: Int, y: Int, velocity: Int) {}
			override fun onGuideChainOn(c: Int) {}
			override fun onRemoveGuide() {}
			override fun chainButsRefresh() {}
			override fun onProgressUpdate(progress: Int) {}
			override fun onEnd() {}
		}
		val runner = AutoPlayRunner(pack, listener, vm.chain)
		autoPlay = runner
		watchReads.set(0)
		watchFrom.set(0)
		runner.launch()
		waitUntil("the auto-play runner's first pass") { watchReads.get() >= 2 || !runner.active }
		watchFrom.set(Long.MAX_VALUE)
	}

	private fun advance(ms: Int) {
		var left = ms
		while (left > 0) {
			val step = minOf(TICK_MS, left)
			left -= step
			val now = clock.get() + step
			setClock(now)
			if (led.active) loopMethod.invoke(led)
			main.runAll()
			awaitWormholes(now)
		}
	}

	private fun setClock(now: Long) {
		val runner = autoPlay
		if (runner == null || !runner.active) {
			clock.set(now)
			return
		}
		watchReads.set(0)
		watchFrom.set(now)
		clock.set(now)
		waitUntil("the auto-play runner to see $now") { watchReads.get() >= 2 || !runner.active }
		watchFrom.set(Long.MAX_VALUE)
	}

	/** A wormhole switches the chain 100 ms after its sound, on the main thread, after a real delay. */
	private fun awaitWormholes(now: Long) {
		val due = wormholes.filter { it.first <= now }
		for (w in due) {
			wormholes.remove(w)
			waitUntil("the wormhole to chain ${w.second}") {
				main.runAll()
				synchronized(chainLog) { chainLog.drop(w.third).contains(w.second) }
			}
		}
	}

	/** Leaving the play screen, as PlayActivity.onStop does it: the view model clears the lights. */
	private fun stop() {
		autoPlay?.stop()
		vm.screenVisible = false
		vm.ledInit()
		sound.destroy()
		main.runAll()
	}

	private fun waitUntil(what: String, condition: () -> Boolean) {
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
		while (!condition()) {
			check(System.nanoTime() < deadline) { "${c.id}: timed out waiting for $what" }
			Thread.sleep(1)
		}
	}

	private companion object {
		const val TICK_MS = 4
	}
}

private fun runScenario(c: CorpusCase, pack: UniPack, root: File, deps: Deps): JsonElement = buildJsonObject {
	putJsonArray("checkpoints") { ScenarioRun(c, pack, root, deps).run().forEach { add(it) } }
}
