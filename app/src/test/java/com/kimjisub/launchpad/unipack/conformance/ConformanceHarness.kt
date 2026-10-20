package com.kimjisub.launchpad.unipack.conformance

import android.media.AudioManager
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.LaunchpadColor
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.AutoPlayRunner
import com.kimjisub.launchpad.unipack.runner.LedRunner
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.launchpad.unipack.struct.LedAnimation.LedEvent
import com.kimjisub.launchpad.unipack.struct.Sound
import com.kimjisub.launchpad.viewmodel.FakeMainDispatcher
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import com.kimjisub.launchpad.viewmodel.PlayMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.File
import java.nio.file.Files
import java.util.IdentityHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.EmptyCoroutineContext

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

/** The pack's sounds folder as it is on disk; the parser finds it whatever its letter case. */
private fun soundsDirOf(root: File): File =
	root.listFiles()?.firstOrNull { it.isDirectory && it.name.equals("sounds", ignoreCase = true) } ?: File(root, "sounds")

/**
 * Whether files whose names differ only in letter case are different files where the packs are
 * written. Where they are not, a parser that opens only the exact name of `info` passes too.
 */
fun caseSensitiveFileSystem(): Boolean {
	val dir = Files.createTempDirectory("unipack-case").toFile()
	try {
		File(dir, "a").writeText("")
		return !File(dir, "A").exists()
	} finally {
		dir.deleteRecursively()
	}
}

internal fun parseResult(pack: UniPack, root: File): JsonElement {
	val soundsDir = soundsDirOf(root)
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

/** A WAV (RIFF), or an MP3 with an ID3 tag or starting at a frame: what the decoder stand-in accepts. */
private fun looksLikeAudio(file: File): Boolean {
	val head = file.inputStream().use { it.readNBytes(12) }
	fun ascii(at: Int, text: String) = head.size >= at + text.length && text.indices.all { head[at + it] == text[it].code.toByte() }
	val mpegFrame = head.size >= 2 && head[0] == 0xff.toByte() && (head[1].toInt() and 0xe0) == 0xe0
	return (ascii(0, "RIFF") && ascii(8, "WAVE")) || ascii(0, "ID3") || mpegFrame
}

/**
 * Stands in for the native audio engine and reports which file each `play` call started, and each
 * voice a `stopVoice` call stopped the first time. It decodes no audio: any bytes that start like a
 * WAV or an MP3 decode, others fail to decode as they would in the native decoder. It has no output
 * stream: opening and closing one (start, stop) are accepted and not checked.
 * [onPlay] records the start and returns the event its stop is recorded as.
 */
private class RecordingEngine(
	private val onPlay: (file: File, loop: Int) -> JsonElement,
	private val onStop: (stopEvent: JsonElement) -> Unit,
) : SoundRunner.Engine {
	private val decoded = IdentityHashMap<OboeAudioEngine.DecodedAudio, File>()
	private val fileById = HashMap<Int, File>()
	private val stopEvents = HashMap<Int, JsonElement>()
	private var nextId = 0
	// 0 is the runner's "no voice" key.
	private var nextStopKey = 1
	private val endless = HashMap<Int, Pair<Int, Int>>()

	override fun start() = true
	override fun stop() = stopAllVoices()

	override fun decode(file: File): OboeAudioEngine.DecodedAudio? {
		if (!looksLikeAudio(file)) return null
		return OboeAudioEngine.DecodedAudio(ShortArray(1), 1, 1, 44_100).also { synchronized(this) { decoded[it] = file } }
	}

	override fun load(decoded: OboeAudioEngine.DecodedAudio): Int = synchronized(this) {
		nextId++.also { fileById[it] = this.decoded.getValue(decoded) }
	}

	override fun unloadSound(soundId: Int) {}
	override fun unloadAll() = stopAllVoices()

	override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int {
		val stopEvent = onPlay(synchronized(this) { fileById.getValue(soundId) }, loop)
		return synchronized(this) { nextStopKey++.also { key ->
			stopEvents[key] = stopEvent
			if (loop == -1) endless[key] = stopEvent.jsonArray[2].jsonPrimitive.int to stopEvent.jsonArray[3].jsonPrimitive.int
		} }
	}

	override fun stopVoice(stopKey: Int) {
		synchronized(this) { endless.remove(stopKey); stopEvents.remove(stopKey) }?.let(onStop)
	}
	override fun stopAllVoices() { synchronized(this) { endless.clear(); stopEvents.clear() } }

	fun looping(): List<Pair<Int, Int>> = synchronized(this) {
		endless.values.sortedWith(compareBy({ it.first }, { it.second }))
	}
}

/**
 * One virtual clock for SystemClock, the LED runner's tick (called the way its coroutine calls it)
 * and the runners' threads. The auto-play runner polls the clock on its own thread, so every
 * change of time waits until that thread has read the new value and finished the pass that read it.
 *
 * The play screen's view model is set up the way PlayActivity does it (initState, the option locks and
 * listeners, initPlayback), so its chain observer and options are in place; only its LED runner is
 * swapped for one this run drives, of the same class. Auto play is the screen's own runner, with its
 * listener wrapped: pad presses and releases are recorded, and onEnd is the screen's own. Leaving and
 * returning to the screen are PlayActivity's onStop and onStart/onResume calls into the view model.
 *
 * Where this run still differs from the play screen: the auto-play listener's other calls (start,
 * progress, guides, chain lights) are not passed on; unless auto play plays the pads, its chain changes
 * run on the runner's thread, not posted to the main thread; "start" expects a "stop" before it; returning
 * to the screen is checked up to the sounds' play calls, not whether the audio stream was opened again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private class ScenarioRun(val c: CorpusCase, val pack: UniPack, root: File, val deps: Deps) {
	private val soundsDir = soundsDirOf(root)
	private val testThread = Thread.currentThread()
	private val clock = AtomicLong(1000)
	private val watchFrom = AtomicLong(Long.MAX_VALUE)
	private val watchReads = AtomicInteger(0)
	private val events = java.util.Collections.synchronizedList(ArrayList<JsonElement>())
	private val chainLog = java.util.Collections.synchronizedList(ArrayList<Int>())
	private val chainTag = ThreadLocal<String?>()

	private val main = FakeMainDispatcher()
	private val engine = RecordingEngine(
		onPlay = { file, loop ->
			val name = file.relativeTo(soundsDir).path.replace(File.separatorChar, '/')
			events.add(event("sound", pressChain, pressX, pressY, name, loop))
			event("soundStop", pressChain, pressX, pressY, name)
		},
		onStop = { if (watchingStops) events.add(it) },
	)
	private val vm = PlayActivityViewModel(mockk<UnipackRepository>(relaxed = true), UsageAnalytics { _, _ -> }, engine)
	private val loopMethod = LedRunner::class.java.getDeclaredMethod("loop").apply { isAccessible = true }
	private val listenerField = AutoPlayRunner::class.java.getDeclaredField("listener").apply { isAccessible = true }
	private val lit = HashMap<String, Pair<Int, Int>>()
	private val wormholes = ArrayList<Triple<Long, Int, Int>>()
	private var pressChain = 0
	private var pressX = 0
	private var pressY = 0
	@Volatile private var watchingStops = false

	private var autoPlay: AutoPlayRunner? = null
	private var finished = false

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
					"autoplay" -> startAutoPlay(toPads = step["toPads"]?.jsonPrimitive?.boolean == true)
					"autoplayOff" -> afterAutoPlayPass("auto play to stop") { vm.switchPlayMode(PlayMode.None) }
					"looping" -> engine.looping().forEach { (px, py) -> events.add(event("looping", px, py)) }
					"stop" -> stop()
					"watchStops" -> watchingStops = true
					// Android-only steps for ConformancePlayPathTest; the shared corpus does not use them.
					"start" -> start()
					"resume" -> resumeAutoPlay()
					else -> error("${c.id}: unknown scenario step $name")
				}
			}
			return checkpoints
		} finally {
			autoPlay?.stop()
			vm.ledRunner?.stop()
			vm.soundRunner?.destroy()
			vm.viewModelScope.cancel()
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
		vm.uiCallback = LedSink()
		vm.initState()
		vm.setupCheckBoxVisibility()
		vm.setupCheckBoxListeners()
		// Pro light mode on. Off, which is the play screen's default (PlayActivityViewModel.proLightMode),
		// hides the round and logo lights of a keyLED file, and the cases that light them would see nothing.
		vm.scbProLightMode.setChecked(true)
		// The option window open with the UI hidden: the screen's own lights on the round buttons (selected
		// chain, option bar, logo) are all off, so only the pack's lights are reported.
		vm.scbHideUI.setChecked(true)
		vm.toggleOptionWindow(true)
		vm.chain.addObserver { curr, _ ->
			val tag = chainTag.get()
			if (tag == null) {
				chainLog.add(curr)
				events.add(event("chain", curr))
			} else if (tag.isNotEmpty()) events.add(event(tag, curr))
		}
		vm.initPlayback()
		// Touch feedback off, which initPlayback turns on for a pack without a keyLED file: the cases report
		// the pack's own lights only.
		vm.scbFeedbackLight.setChecked(false)
		vm.scbLed.setChecked(true)

		// The app's LED runner ticks every 4 ms of real time. This one is the same class ticked on the
		// virtual clock; its own coroutine runs the first tick at launch and then waits an hour.
		vm.ledRunner?.let { vm.ledRunner = deps.wrapLedRunner(LedRunner(pack, vm.ledRunnerListener, vm.chain, 3_600_000L)) }
		waitUntil("the sounds to finish loading", seconds = 10) { main.runAll(); !vm.soundLoadingActive }
		check(!finished) { "${c.id}: sound loading failed" }
		start()
		// The screen's own lights blink on and off while the options above are set; a case starts after that.
		check(lit.isEmpty()) { "${c.id}: lights left on after setup: $lit" }
		events.clear()
	}

	/** Back on the play screen, as PlayActivity.onStart and onResume do it. */
	private fun start() {
		val led = vm.ledRunner
		watchReads.set(0)
		watchFrom.set(0)
		vm.screenVisible = true
		if (led != null && led.active) {
			waitUntil("the LED runner's first tick") { watchReads.get() >= 1 }
			led.afterDelivery { }
		}
		watchFrom.set(Long.MAX_VALUE)
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)
		main.runAll()
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
		override fun finishActivity() { finished = true }
		override fun copyToClipboard(text: String) {}
		override fun setChainViewVisibility(index: Int, visibility: Int) {}
		override fun startGuideAnimation(x: Int, y: Int, targetWallTimeMs: Long) {}
		override fun stopGuideAnimation(x: Int, y: Int) {}
		override fun sendGuideLedToLaunchpad(x: Int, y: Int, velocity: Int) {}
		override fun onRequestRelayout() {}
	}

	/** A press or release of (x, y), by a person unless [touch] hands it to the view model another way. */
	private fun press(x: Int, y: Int, down: Boolean, touch: () -> Unit = { vm.padTouch(x, y, down) }) {
		pressChain = vm.chain.value
		pressX = x
		pressY = y
		if (down) {
			// Every sound with a wormhole, decoded or not: whether the runner switches is what is measured.
			val wormhole = pack.soundGet(pressChain, x, y)?.wormhole
			if (wormhole != null && wormhole != -1) wormholes.add(Triple(clock.get() + 100, wormhole.coerceIn(vm.chain.range), chainLog.size))
		}
		touch()
		main.runAll()
	}

	/**
	 * [toPads]: each press, release and chain change also reaches the screen the way PlayActivityViewModel's
	 * own auto-play listener sends it, posted to the main thread and handed to the view model's auto-play calls.
	 */
	private fun startAutoPlay(toPads: Boolean) {
		val runner = checkNotNull(vm.autoPlayRunner) { "${c.id}: the play screen has no auto play" }
		if (autoPlay !== runner) listenerField.set(runner, recording(listenerField.get(runner) as AutoPlayRunner.Listener, toPads))
		autoPlay = runner
		afterAutoPlayPass("the auto-play runner's first pass", STARTING) { vm.switchPlayMode(PlayMode.AutoPlay) }
	}

	/** The screen's auto-play listener, recording pad presses and releases instead of passing them on. */
	private fun recording(screen: AutoPlayRunner.Listener, toPads: Boolean): AutoPlayRunner.Listener {
		fun forward(x: Int, y: Int, down: Boolean, touch: () -> Unit) {
			if (toPads) main.dispatch(EmptyCoroutineContext, Runnable { press(x, y, down, touch) })
		}
		fun changeChain(c: Int) {
			chainTag.set("autoChain")
			try { vm.chain.value = c } finally { chainTag.set(null) }
		}
		return object : AutoPlayRunner.Listener {
			override fun onStart() {}
			override fun onPadTouchOn(x: Int, y: Int, c: Int, num: Int, atMs: Long) {
				events.add(event("autoOn", x, y))
				forward(x, y, true) { vm.autoPlayPadOn(x, y, c, num, atMs) }
			}
			override fun onPadTouchOff(x: Int, y: Int, atMs: Long) {
				events.add(event("autoOff", x, y))
				forward(x, y, false) { vm.autoPlayPadTouch(x, y, false, atMs) }
			}
			override fun onChainChange(c: Int) {
				if (toPads) main.dispatch(EmptyCoroutineContext, Runnable { changeChain(c) }) else changeChain(c)
			}
			override fun onGuidePadOn(x: Int, y: Int, targetWallTimeMs: Long) {}
			override fun onGuidePadOff(x: Int, y: Int) {}
			override fun onGuideLedUpdate(x: Int, y: Int, velocity: Int) {}
			override fun onGuideChainOn(c: Int) {}
			override fun onRemoveGuide() {}
			override fun chainButsRefresh() {}
			override fun onProgressUpdate(progress: Int) {}
			// At its end auto play turns its option off, so leaving the screen afterwards finds it not playing.
			override fun onEnd() = screen.onEnd()
		}
	}

	/**
	 * Runs [action] and, when auto play was or is now playing, waits until its runner has read the clock
	 * [reads] times after it: the pass in progress has finished, so nothing it sends arrives later. A pass
	 * reads the clock once, at its top, and the next pass's read marks its end. Starting reads it once more
	 * before the first pass (the start time); continuing counts from after autoPlayResume, where a paused
	 * pass may still read it once on waking (the time spent paused); pausing reads it at least twice after
	 * the pass in progress before the runner waits.
	 */
	private fun afterAutoPlayPass(what: String, reads: Int = 2, action: () -> Unit) {
		val runner = autoPlay
		val wasPlaying = runner != null && runner.active && runner.playmode
		watchReads.set(0)
		watchFrom.set(0)
		action()
		if (runner != null && (wasPlaying || runner.active && runner.playmode)) {
			waitUntil(what) { watchReads.get() >= reads || !runner.active }
		}
		watchFrom.set(Long.MAX_VALUE)
		main.runAll()
	}

	/**
	 * The play button of paused auto play. A paused runner drops the time it spent paused when it next
	 * reads the clock while not playing. A person presses play long after that; here the clock may have
	 * moved on before the runner got that far, and it would play everything due meanwhile at once. So it
	 * is first woken (setting playmode to what it is only wakes it) and has read the clock twice: the
	 * first pass after the wake has dropped the time.
	 *
	 * autoPlayResume wakes the paused runner (stepMode, resetStepState) before it sets playmode, so the
	 * runner can read the clock a few times while still paused; only the reads after autoPlayResume has
	 * returned are counted.
	 */
	private fun resumeAutoPlay() {
		val runner = autoPlay
		if (runner != null && runner.active && !runner.playmode) {
			watchReads.set(0)
			watchFrom.set(0)
			runner.playmode = false
			waitUntil("paused auto play to drop the time spent paused") { watchReads.get() >= 2 || !runner.active }
			watchFrom.set(Long.MAX_VALUE)
		}
		vm.autoPlayResume()
		afterAutoPlayPass("auto play to continue", STARTING) {}
	}

	private fun advance(ms: Int) {
		var left = ms
		while (left > 0) {
			val step = minOf(TICK_MS, left)
			left -= step
			val now = clock.get() + step
			setClock(now)
			vm.ledRunner?.takeIf { it.active }?.let { loopMethod.invoke(it) }
			main.runAll()
			awaitWormholes(now)
		}
	}

	private fun setClock(now: Long) {
		val runner = autoPlay
		if (runner == null || !runner.active || !runner.playmode) {
			clock.set(now)
			return
		}
		watchReads.set(0)
		watchFrom.set(now)
		clock.set(now)
		waitUntil("the auto-play runner to see $now") { watchReads.get() >= 2 || !runner.active }
		watchFrom.set(Long.MAX_VALUE)
	}

	/**
	 * A wormhole switches the chain 100 ms after its sound, on the main thread, after a real delay. A
	 * runner that does not switch (a sound that failed to decode) is waited for no longer than
	 * [WORMHOLE_WAIT_MS]; the missing chain event then shows in the result.
	 */
	private fun awaitWormholes(now: Long) {
		val due = wormholes.filter { it.first <= now }
		for (w in due) {
			wormholes.remove(w)
			val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WORMHOLE_WAIT_MS)
			while (System.nanoTime() < deadline) {
				main.runAll()
				if (synchronized(chainLog) { chainLog.drop(w.third).contains(w.second) }) break
				Thread.sleep(1)
			}
		}
	}

	/**
	 * Leaving the play screen, as PlayActivity.onStop does it: a permanent audio focus loss pauses auto
	 * play where it is and silences the pack, then the LED runner and the audio stream stop and the
	 * lights are cleared. The sounds stay loaded. Giving the focus back to the system is not modelled.
	 */
	private fun stop() = afterAutoPlayPass("auto play to pause") {
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS)
		vm.screenVisible = false
		vm.ledInit()
	}

	private fun waitUntil(what: String, seconds: Long = 5, condition: () -> Boolean) {
		val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)
		while (!condition()) {
			check(System.nanoTime() < deadline) { "${c.id}: timed out waiting for $what" }
			Thread.sleep(1)
		}
	}

	private companion object {
		const val TICK_MS = 4
		const val STARTING = 3
		const val WORMHOLE_WAIT_MS = 1_000L
	}
}

private fun runScenario(c: CorpusCase, pack: UniPack, root: File, deps: Deps): JsonElement = buildJsonObject {
	putJsonArray("checkpoints") { ScenarioRun(c, pack, root, deps).run().forEach { add(it) } }
}
