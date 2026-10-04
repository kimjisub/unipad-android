package com.kimjisub.launchpad.viewmodel

import android.os.SystemClock
import com.kimjisub.launchpad.analytics.RecordingUsageSink
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.analytics.UsageEvent
import com.kimjisub.launchpad.analytics.UsageParam
import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkConstructor
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.io.File as JFile

/**
 * What the play screen tells usage analytics: `pack_load` when the pack is read and its sounds are
 * ready, `play_start` at the first sound, `play_end` on leaving. The view model outlives a rebuilt
 * activity, so it, not the activity, owns the session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelUsageTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private val main = FakeMainDispatcher()
	private val sink = RecordingUsageSink()
	private val created = mutableListOf<PlayActivityViewModel>()

	/** Loads without the native audio library; [startOk] false makes the engine fail to start. */
	private class FakeEngine(private val startOk: Boolean = true) : SoundRunner.Engine {
		override fun start() = startOk
		override fun stop() {}
		override fun decode(file: JFile) = OboeAudioEngine.DecodedAudio(ShortArray(1), 1, 1, 44_100)
		override fun load(decoded: OboeAudioEngine.DecodedAudio) = 1
		override fun unloadSound(soundId: Int) {}
		override fun unloadAll() {}
		override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int) = 0
		override fun stopVoice(stopKey: Int) {}
		override fun stopAllVoices() {}
	}

	/** Decoding waits until the test lets it finish, which keeps the pack in its not-yet-ready state. */
	private class SlowEngine : SoundRunner.Engine by FakeEngine() {
		private val mayFinish = CountDownLatch(1)

		fun finishDecoding() = mayFinish.countDown()

		override fun decode(file: JFile): OboeAudioEngine.DecodedAudio {
			mayFinish.await(10, TimeUnit.SECONDS)
			return OboeAudioEngine.DecodedAudio(ShortArray(1), 1, 1, 44_100)
		}
	}

	@Before
	fun setUp() {
		Dispatchers.setMain(main)
		// Auto play paces itself with this clock, which stands still in a plain JVM test.
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { System.nanoTime() / 1_000_000 }
	}

	@After
	fun tearDown() {
		created.forEach { runCatching { leave(it) } }
		unmockkStatic(SystemClock::class)
		unmockkConstructor(UniPackFolder::class)
		Dispatchers.resetMain()
	}

	/** Runs what reaches the main thread, as the looper would, until [done] or the timeout. */
	private fun runUntil(timeoutMs: Long = 5_000, done: () -> Boolean) {
		val deadline = System.nanoTime() + timeoutMs * 1_000_000
		while (!done() && System.nanoTime() < deadline) {
			main.runAll()
			Thread.sleep(5)
		}
		main.runAll()
	}

	private fun runFor(durationMs: Long) = runUntil(durationMs) { false }

	private fun playStarts() = sink.named(UsageEvent.PLAY_START).map { it.parameters }

	private fun createPack(withSounds: Boolean = true, autoPlay: String? = null, withInfo: Boolean = true): File {
		val root = tmp.newFolder()
		if (withInfo) File(root, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=1\nsquareButton=true\n")
		// keySound is required by UniPack; a pack without sounds has it empty.
		File(root, "keySound").writeText(if (withSounds) "1 1 1 a.wav\n" else "")
		if (withSounds) {
			File(root, "sounds").mkdir()
			File(root, "sounds/a.wav").writeText("")
		}
		autoPlay?.let { File(root, "autoPlay").writeText(it) }
		return root
	}

	private fun newVm(engine: SoundRunner.Engine = FakeEngine()): PlayActivityViewModel {
		val vm = PlayActivityViewModel(mockk<UnipackRepository>(relaxed = true), sink.analytics, engine)
		created += vm
		return vm
	}

	private fun load(vm: PlayActivityViewModel, pack: File) {
		runBlocking { withTimeout(5_000) { vm.loadUnipackOnce(pack.path).await() } }
	}

	/** What PlayActivity does once the pack is read and the layout exists. */
	private fun startPlayback(vm: PlayActivityViewModel) {
		vm.initState()
		vm.initPlayback()
	}

	private fun leave(vm: PlayActivityViewModel) {
		PlayActivityViewModel::class.java.getDeclaredMethod("onCleared").apply { isAccessible = true }.invoke(vm)
	}

	private fun names() = sink.events.map { it.name }

	private fun loadSuccess() = sink.events.first { it.name == UsageEvent.PACK_LOAD }

	@Test
	fun aHumanPressAfterAutoPlayIsRecordedOnceWithoutAnotherPlayStart() {
		val vm = newVm()
		load(vm, createPack(autoPlay = LONG_AUTO_PLAY))
		startPlayback(vm)
		sink.awaitEvent(UsageEvent.PACK_LOAD)
		vm.switchPlayMode(PlayMode.AutoPlay)
		runUntil { playStarts().isNotEmpty() }
		assertEquals(emptyList<String>(), sink.named(UsageEvent.PLAY_FIRST_INPUT).map { it.name })
		vm.switchPlayMode(PlayMode.None)
		repeat(3) {
			vm.padTouch(0, 0, true)
			vm.padTouch(0, 0, false)
		}
		leave(vm)

		assertEquals(1, sink.named(UsageEvent.PLAY_FIRST_INPUT).size)
		assertEquals(listOf(mapOf(UsageParam.TRIGGER to "autoplay")), playStarts())
	}

	@Test
	fun aPackIsNotCountedAsLoadedUntilItsSoundsAreReady() {
		val vm = newVm()
		load(vm, createPack())
		assertEquals("reading the files is not the end of loading", emptyList<String>(), names())

		startPlayback(vm)
		sink.awaitEvent(UsageEvent.PACK_LOAD)

		assertEquals(listOf(UsageEvent.PACK_LOAD), names())
		assertEquals("success", loadSuccess().parameters[UsageParam.RESULT])
		assertEquals("lt_1s", loadSuccess().parameters[UsageParam.DURATION_BUCKET])
	}

	@Test
	fun firstPadPressStartsOneSessionAndLeavingEndsItOnce() {
		val vm = newVm()
		load(vm, createPack())
		startPlayback(vm)
		sink.awaitEvent(UsageEvent.PACK_LOAD)

		vm.padTouch(0, 0, true)
		vm.padTouch(0, 0, false)
		vm.screenVisible = false // the app goes to the background and comes back: the same session
		vm.screenVisible = true
		vm.padTouch(1, 1, true)
		vm.padTouch(1, 1, false)
		assertEquals(listOf(UsageEvent.PACK_LOAD, UsageEvent.PLAY_START, UsageEvent.PLAY_FIRST_INPUT), names())
		assertEquals(mapOf(UsageParam.TRIGGER to "pad"), sink.named(UsageEvent.PLAY_START).single().parameters)

		leave(vm)
		leave(vm)

		assertEquals(listOf(UsageEvent.PACK_LOAD, UsageEvent.PLAY_START, UsageEvent.PLAY_FIRST_INPUT, UsageEvent.PLAY_END), names())
		assertEquals(mapOf(UsageParam.DURATION_BUCKET to "lt_1s"), sink.named(UsageEvent.PLAY_END).single().parameters)
	}

	@Test
	fun padOutsideThePackGridIsNotAPlayStart() {
		val vm = newVm()
		load(vm, createPack())
		startPlayback(vm)
		sink.awaitEvent(UsageEvent.PACK_LOAD)

		vm.padTouch(-1, 0, true)
		vm.padTouch(0, 99, true)

		assertEquals(listOf(UsageEvent.PACK_LOAD), names())
	}

	@Test
	fun leavingBeforeTheFirstSoundSendsNoPlayEvents() {
		val vm = newVm()
		load(vm, createPack())
		startPlayback(vm)
		sink.awaitEvent(UsageEvent.PACK_LOAD)

		leave(vm)

		assertEquals(listOf(UsageEvent.PACK_LOAD), names())
	}

	@Test
	fun leavingWhileThePackIsStillLoadingIsOneCancelledLoad() {
		val vm = newVm()
		load(vm, createPack())

		leave(vm)
		startPlayback(vm)

		assertEquals(listOf(UsageEvent.PACK_LOAD), names())
		assertEquals("cancelled", loadSuccess().parameters[UsageParam.RESULT])
	}

	@Test
	fun aRebuiltActivityDoesNotStartASecondLoad() {
		val vm = newVm()
		val pack = createPack()
		repeat(3) {
			load(vm, pack) // each rebuilt PlayActivity asks the same view model again
			startPlayback(vm)
		}
		sink.awaitEvent(UsageEvent.PACK_LOAD)
		vm.padTouch(0, 0, true)
		leave(vm)

		assertEquals(listOf(UsageEvent.PACK_LOAD, UsageEvent.PLAY_START, UsageEvent.PLAY_FIRST_INPUT, UsageEvent.PLAY_END), names())
	}

	@Test
	fun aPackWithoutAnInfoFileIsAnInvalidPackAndNeverPlays() {
		val vm = newVm()
		load(vm, createPack(withInfo = false))

		vm.padTouch(0, 0, true)
		leave(vm)

		assertEquals(listOf(UsageEvent.PACK_LOAD), names())
		assertEquals(
			mapOf(UsageParam.RESULT to "failure", UsageParam.ERROR_TYPE to "invalid_pack"),
			loadSuccess().parameters,
		)
	}

	@Test
	fun anUnreadablePackIsAFileAccessFailure() {
		mockkConstructor(UniPackFolder::class)
		every { anyConstructed<UniPackFolder>().load() } throws IOException("/private/path/Secret Pack: Permission denied")
		val vm = newVm()

		try {
			load(vm, createPack())
			fail("the load should have thrown")
		} catch (_: IOException) {
		}
		leave(vm)

		assertEquals(
			listOf(sink.pack(UsageEvent.PACK_LOAD, UsageParam.RESULT to "failure", UsageParam.ERROR_TYPE to "file_access")),
			sink.events,
		)
	}

	@Test
	fun anAudioEngineThatCannotStartIsASoundEngineFailure() {
		val vm = newVm(FakeEngine(startOk = false))
		load(vm, createPack())
		startPlayback(vm)
		sink.awaitEvent(UsageEvent.PACK_LOAD)

		vm.padTouch(0, 0, true)
		leave(vm)

		assertEquals(listOf(UsageEvent.PACK_LOAD), names())
		assertEquals(
			mapOf(UsageParam.RESULT to "failure", UsageParam.ERROR_TYPE to "sound_engine"),
			loadSuccess().parameters,
		)
	}

	/** A pack of only LEDs or auto play has nothing to decode; its load still ends and a press still starts a session. */
	@Test
	fun aPackWithoutSoundsLoadsAndPlaysLikeAnyOther() {
		val vm = newVm()
		load(vm, createPack(withSounds = false))
		startPlayback(vm)
		sink.awaitEvent(UsageEvent.PACK_LOAD)

		vm.padTouch(0, 0, true)

		assertEquals(listOf(UsageEvent.PACK_LOAD, UsageEvent.PLAY_START, UsageEvent.PLAY_FIRST_INPUT), names())
	}

	@Test
	fun autoPlayStartsTheSessionOnceHoweverOftenItIsSwitchedOn() {
		val vm = newVm()
		load(vm, createPack(autoPlay = LONG_AUTO_PLAY))
		startPlayback(vm)
		sink.awaitEvent(UsageEvent.PACK_LOAD)

		vm.switchPlayMode(PlayMode.AutoPlay)
		runUntil { playStarts().isNotEmpty() }
		vm.switchPlayMode(PlayMode.None)
		vm.switchPlayMode(PlayMode.AutoPlay)
		runFor(AUTO_PLAY_NOTE_MS * 3)
		vm.padTouch(0, 0, true)
		leave(vm)

		assertEquals(listOf(UsageEvent.PACK_LOAD, UsageEvent.PLAY_START, UsageEvent.PLAY_FIRST_INPUT, UsageEvent.PLAY_END), names())
		assertEquals(listOf(mapOf(UsageParam.TRIGGER to "autoplay")), playStarts())
	}

	/** Step practice and guide play only show where to press; nothing sounds until the person presses. */
	@Test
	fun enteringStepPracticeOrGuidePlayIsNotAPlayStartButThePressThatFollowsIs() {
		for (mode in listOf(PlayMode.StepPractice, PlayMode.GuidePlay)) {
			val recorded = sink.events.size
			val vm = newVm()
			load(vm, createPack(autoPlay = LONG_AUTO_PLAY))
			startPlayback(vm)
			runUntil { sink.events.size > recorded }

			vm.switchPlayMode(mode)
			runFor(AUTO_PLAY_NOTE_MS * 3)
			assertEquals("$mode alone", listOf(UsageEvent.PACK_LOAD), names().drop(recorded))

			vm.padTouch(0, 0, true)
			leave(vm)

			assertEquals("$mode", listOf(UsageEvent.PACK_LOAD, UsageEvent.PLAY_START, UsageEvent.PLAY_FIRST_INPUT, UsageEvent.PLAY_END), names().drop(recorded))
			assertEquals("$mode", mapOf(UsageParam.TRIGGER to "pad"), playStarts().last())
		}
	}

	/** Nobody touches a pad: the first sound comes from auto play, whatever mode was entered before it. */
	@Test
	fun autoPlayChosenAfterStepPracticeWithoutAPressIsAnAutoPlayStart() {
		val vm = newVm()
		load(vm, createPack(autoPlay = LONG_AUTO_PLAY))
		startPlayback(vm)
		sink.awaitEvent(UsageEvent.PACK_LOAD)

		vm.switchPlayMode(PlayMode.StepPractice)
		runFor(AUTO_PLAY_NOTE_MS * 3)
		vm.switchPlayMode(PlayMode.AutoPlay)
		runUntil { playStarts().isNotEmpty() }
		leave(vm)

		assertEquals(listOf(UsageEvent.PACK_LOAD, UsageEvent.PLAY_START, UsageEvent.PLAY_END), names())
		assertEquals(listOf(mapOf(UsageParam.TRIGGER to "autoplay")), playStarts())
	}

	/** The option panel can switch auto play on while the sounds are still decoding. */
	@Test
	fun autoPlaySwitchedOnBeforeTheSoundsAreReadyStartsAsAutoPlayOnceTheyAre() {
		val engine = SlowEngine()
		val vm = newVm(engine)
		load(vm, createPack(autoPlay = LONG_AUTO_PLAY))
		startPlayback(vm)

		vm.switchPlayMode(PlayMode.AutoPlay)
		runFor(AUTO_PLAY_NOTE_MS * 3)
		assertEquals("auto play pressing pads of a pack that is not ready", emptyList<String>(), names())

		engine.finishDecoding()
		sink.awaitEvent(UsageEvent.PACK_LOAD)
		runUntil { playStarts().isNotEmpty() }
		leave(vm)

		assertEquals(listOf(UsageEvent.PACK_LOAD, UsageEvent.PLAY_START, UsageEvent.PLAY_END), names())
		assertEquals(listOf(mapOf(UsageParam.TRIGGER to "autoplay")), playStarts())
	}

	@Test
	fun autoPlaySwitchedOnBeforeTheSoundsAreReadyAndLeftBeforeTheyAreIsOnlyACancelledLoad() {
		val engine = SlowEngine()
		val vm = newVm(engine)
		load(vm, createPack(autoPlay = LONG_AUTO_PLAY))
		startPlayback(vm)

		vm.switchPlayMode(PlayMode.AutoPlay)
		runFor(AUTO_PLAY_NOTE_MS * 3)
		leave(vm)
		engine.finishDecoding()
		runFor(AUTO_PLAY_NOTE_MS * 3)

		assertEquals(listOf(sink.pack(UsageEvent.PACK_LOAD, UsageParam.RESULT to "cancelled")), sink.events)
	}

	@Test
	fun aRealPressBeforeTheSoundsAreReadyIsNotAPlayStartButTheFirstOneAfterIs() {
		val engine = SlowEngine()
		val vm = newVm(engine)
		load(vm, createPack())
		startPlayback(vm)

		vm.padTouch(0, 0, true)
		vm.padTouch(0, 0, false)
		assertEquals(emptyList<String>(), names())

		engine.finishDecoding()
		sink.awaitEvent(UsageEvent.PACK_LOAD)
		assertEquals("becoming ready is not a press", listOf(UsageEvent.PACK_LOAD), names())

		vm.padTouch(0, 0, true)

		assertEquals(listOf(mapOf(UsageParam.TRIGGER to "pad")), playStarts())
	}

	@Test
	fun changingModesAfterThePlayStartedAddsNoSecondStart() {
		val vm = newVm()
		load(vm, createPack(autoPlay = LONG_AUTO_PLAY))
		startPlayback(vm)
		sink.awaitEvent(UsageEvent.PACK_LOAD)

		vm.padTouch(0, 0, true)
		vm.padTouch(0, 0, false)
		for (mode in listOf(PlayMode.StepPractice, PlayMode.AutoPlay, PlayMode.GuidePlay, PlayMode.AutoPlay, PlayMode.None)) {
			vm.switchPlayMode(mode)
			runFor(AUTO_PLAY_NOTE_MS * 3)
		}
		leave(vm)

		assertEquals(listOf(UsageEvent.PACK_LOAD, UsageEvent.PLAY_START, UsageEvent.PLAY_FIRST_INPUT, UsageEvent.PLAY_END), names())
		assertEquals(listOf(mapOf(UsageParam.TRIGGER to "pad")), playStarts())
	}

	/** Analytics that cannot start, as when Firebase fails to initialise, must not cost the person the pack. */
	@Test
	fun aBrokenAnalyticsSinkDoesNotStopLoadingOrPlaying() {
		val reported = CopyOnWriteArrayList<String>()
		val broken = UsageAnalytics { name, _ ->
			reported += name
			throw IllegalStateException("Firebase is not initialised")
		}
		val played = AtomicInteger()
		val engine = object : SoundRunner.Engine by FakeEngine() {
			override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int) = played.incrementAndGet()
		}
		val vm = PlayActivityViewModel(mockk<UnipackRepository>(relaxed = true), broken, engine).also { created += it }

		load(vm, createPack())
		startPlayback(vm)
		runUntil { UsageEvent.PACK_LOAD in reported }
		vm.padTouch(0, 0, true)
		vm.padTouch(0, 0, false)
		leave(vm)

		assertEquals("the pad's sound was played", 1, played.get())
		assertEquals(listOf(UsageEvent.PACK_LOAD, UsageEvent.PLAY_START, UsageEvent.PLAY_FIRST_INPUT, UsageEvent.PLAY_END), reported.toList())
	}

	private companion object {
		const val AUTO_PLAY_NOTE_MS = 40L

		/** Presses one pad after another for some twenty seconds, so auto play is still running whenever a test looks. */
		val LONG_AUTO_PLAY = (0 until 256).joinToString("") { i ->
			val pad = "${i / 8 % 8 + 1} ${i % 8 + 1}"
			"o $pad\nd $AUTO_PLAY_NOTE_MS\nf $pad\nd $AUTO_PLAY_NOTE_MS\n"
		}
	}
}
