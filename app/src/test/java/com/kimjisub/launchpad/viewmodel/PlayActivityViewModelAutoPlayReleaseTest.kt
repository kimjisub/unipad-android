package com.kimjisub.launchpad.viewmodel

import android.media.AudioManager
import android.os.SystemClock
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.audio.OboeAudioEngine
import com.kimjisub.launchpad.manager.ChannelManager.Channel
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.SoundRunner
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelAutoPlayReleaseTest {
	@get:Rule val tmp = TemporaryFolder()
	private val main = FakeMainDispatcher()
	private lateinit var vm: PlayActivityViewModel
	private val engine = RecordingEngine()

	private class RecordingEngine : SoundRunner.Engine {
		val voices = mutableMapOf<Int, Int>()
		var playCount = 0
			private set
		override fun start() = true
		override fun stop() {}
		override fun decode(file: File) = OboeAudioEngine.DecodedAudio(ShortArray(1), 1, 1, 44100)
		override fun load(decoded: OboeAudioEngine.DecodedAudio) = 1
		override fun unloadSound(soundId: Int) {}
		override fun unloadAll() {}
		override fun play(soundId: Int, volumeL: Float, volumeR: Float, loop: Int): Int =
			(++playCount).also { voices[it] = loop }
		override fun stopVoice(stopKey: Int) { voices.remove(stopKey) }
		override fun stopAllVoices() { voices.clear() }
	}

	@Before fun setUp() {
		Dispatchers.setMain(main)
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { System.nanoTime() / 1_000_000 }
	}

	@After fun tearDown() {
		clearPlayViewModel(if (::vm.isInitialized) vm else null, drain = { main.runAll() }) {
			main.runAll()
			unmockkStatic(SystemClock::class)
			Dispatchers.resetMain()
		}
	}

	private fun await(check: () -> Boolean) {
		val deadline = System.nanoTime() + 5_000_000_000L
		while (!check() && System.nanoTime() < deadline) {
			main.runAll()
			Thread.sleep(5)
		}
		main.runAll()
		assertTrue("Condition did not become true", check())
	}

	private fun start(keySound: String = "1 1 1 a.wav 0\n", sequence: String = "o 1 1\nd 60000\nf 1 1\n", autoplay: Boolean = true) {
		val folder = tmp.newFolder()
		File(folder, "info").writeText("title=Test\nproducerName=Tester\nbuttonX=8\nbuttonY=8\nchain=2\nsquareButton=true\n")
		File(folder, "sounds").mkdir()
		File(folder, "sounds/a.wav").writeText("")
		File(folder, "keySound").writeText(keySound)
		File(folder, "autoPlay").writeText(sequence)
		vm = PlayActivityViewModel(mockk<UnipackRepository>(relaxed = true), UsageAnalytics { _, _ -> }, engine)
		vm.unipack = UniPackFolder(folder).load().loadDetail()
		vm.initState()
		vm.initPlayback()
		await { !vm.soundLoadingActive && vm.unipack.soundGet(0, 0, 0)?.id == 1 }
		if (autoplay) {
			vm.switchPlayMode(PlayMode.AutoPlay)
			await { engine.playCount > 0 }
		}
	}

	private fun sharedPadRelease(controllerFirst: Boolean, autoplay: Boolean) {
		start(autoplay = autoplay)
		vm.scbFeedbackLight.setChecked(true)
		vm.padTouch(0, 0, true)
		vm.controllerPadTouch(0, 0, true)
		val held = engine.voices.toMap()
		assertEquals(1, held.size)

		if (controllerFirst) vm.controllerPadTouch(0, 0, false) else vm.padTouch(0, 0, false)
		assertEquals("Only the input that started the current loop may stop it",
			if (controllerFirst) emptyMap<Int, Int>() else held, engine.voices)
		assertEquals(Channel.PRESSED, vm.channelManager.get(0, 0)?.channel)

		if (controllerFirst) vm.padTouch(0, 0, false) else vm.controllerPadTouch(0, 0, false)
		assertEquals(emptyMap<Int, Int>(), engine.voices)
		if (autoplay) {
			assertEquals(Channel.PRESSED, vm.channelManager.get(0, 0)?.channel)
			vm.autoPlayPause()
			assertEquals(emptyMap<Int, Int>(), engine.voices)
		}
		assertNull(vm.channelManager.get(0, 0)?.channel)
	}

	@Test fun seekingPastTheEndDoesNotPlayACommandWaitingForMain() {
		start(autoplay = false)
		vm.switchPlayMode(PlayMode.AutoPlay)
		Thread.sleep(200)
		vm.autoPlayNext()
		await { vm.autoPlayRunner?.active == false }
		assertEquals("A command read before seeking must not play afterwards", 0, engine.playCount)
	}

	@Test fun chainCommandBeforeAPressResetsTheChainOnlyOnce() {
		start("1 1 1 a.wav 1\n2 1 1 a.wav 3\n", sequence = "chain 2\non 1 1\nd 60000\n", autoplay = false)
		val chains = mutableListOf<Int>()
		val record: (Int, Int) -> Unit = { curr, _ -> chains += curr }
		vm.chain.addObserver(record)
		try {
			vm.switchPlayMode(PlayMode.AutoPlay)
			// Leave the main queue undrained while the background runner reads the adjacent commands.
			Thread.sleep(200)
			await { engine.playCount == 1 }
			assertEquals(listOf(1), chains)
			val chain2Loop = checkNotNull(vm.unipack.soundGet(1, 0, 0)).loop
			assertTrue(chain2Loop != checkNotNull(vm.unipack.soundGet(0, 0, 0)).loop)
			assertEquals("The press plays chain 2's mapping", listOf(chain2Loop.toInt()), engine.voices.values.toList())
		} finally {
			vm.chain.removeObserver(record)
		}
	}

	@Test fun padsPressedTogetherPlayInOneMainThreadTask() {
		val pads = 1..8
		start(pads.joinToString("") { "1 1 $it a.wav\n" }, sequence = pads.joinToString("") { "on 1 $it\n" } + "d 60000\n", autoplay = false)
		vm.switchPlayMode(PlayMode.AutoPlay)
		val afterEachTask = mutableListOf<Int>()
		val deadline = System.nanoTime() + 5_000_000_000L
		while (engine.playCount < pads.count() && System.nanoTime() < deadline) {
			main.runAll { afterEachTask += engine.playCount }
			Thread.sleep(5)
		}
		assertEquals(pads.count(), engine.playCount)
		assertEquals(
			"Presses placed at the same time must sound together, not one main-thread task apart",
			emptyList<Int>(),
			afterEachTask.filter { it in 1 until pads.count() },
		)
	}

	@Test fun controllerReleaseStopsItsLoopAndKeepsTheScreenFeedback() = sharedPadRelease(controllerFirst = true, autoplay = false)

	@Test fun screenReleaseKeepsThePadHeldOnController() = sharedPadRelease(controllerFirst = false, autoplay = false)

	@Test fun controllerReleaseStopsItsLoopWhileScreenAndAutoplayRemainHeld() = sharedPadRelease(controllerFirst = true, autoplay = true)

	@Test fun lastControllerReleaseOverridesAutoplayAfterScreenRelease() = sharedPadRelease(controllerFirst = false, autoplay = true)

	@Test fun disablingAutoplayStopsTheLoopItPressed() {
		start()
		vm.switchPlayMode(PlayMode.None)
		assertEquals(emptyMap<Int, Int>(), engine.voices)
	}

	@Test fun pausingAutoplayStopsTheLoopItPressed() {
		start()
		vm.autoPlayPause()
		assertEquals(emptyMap<Int, Int>(), engine.voices)
	}

	@Test fun disablingAutoplayPreservesOtherManuallyHeldPads() {
		start("1 1 1 a.wav 0\n1 1 2 a.wav 0\n")
		vm.padTouch(0, 1, true)
		val manualVoice = engine.voices.keys.last()
		vm.switchPlayMode(PlayMode.None)
		assertEquals(mapOf(manualVoice to -1), engine.voices)
		vm.padTouch(0, 1, false)
		assertEquals(emptyMap<Int, Int>(), engine.voices)
	}

	@Test fun disablingAutoplayPreservesTheSamePadHeldManually() {
		start()
		vm.padTouch(0, 0, true)
		val manual = engine.voices.toMap()
		assertEquals(1, manual.size)
		vm.switchPlayMode(PlayMode.None)
		assertEquals(manual, engine.voices)
		vm.padTouch(0, 0, false)
		assertEquals(emptyMap<Int, Int>(), engine.voices)
	}

	@Test fun handPressOnAnotherChainReplacesTheLoopAutoplayHolds() {
		start("1 1 1 a.wav 0\n2 1 1 a.wav 0\n")
		vm.chain.value = 1
		vm.padTouch(0, 0, true)
		val manual = mapOf(engine.playCount to -1)
		assertEquals(manual, engine.voices)
		vm.switchPlayMode(PlayMode.None)
		assertEquals(manual, engine.voices)
		vm.padTouch(0, 0, false)
		assertEquals(emptyMap<Int, Int>(), engine.voices)
	}

	@Test fun manualReleaseStopsTheLoopEvenWhileAutoplayHoldsThePad() {
		start()
		vm.padTouch(0, 0, true)
		vm.padTouch(0, 0, false)
		assertEquals(emptyMap<Int, Int>(), engine.voices)
		vm.autoPlayPause()
		assertEquals(emptyMap<Int, Int>(), engine.voices)
	}

	@Test fun controllerReleaseStopsTheLoopEvenWhileAutoplayHoldsThePad() {
		start()
		vm.controllerPadTouch(0, 0, true)
		vm.controllerPadTouch(0, 0, false)
		assertEquals(emptyMap<Int, Int>(), engine.voices)
		vm.autoPlayPause()
		assertEquals(emptyMap<Int, Int>(), engine.voices)
	}

	@Test fun unmatchedManualReleasePreservesTheLoopAutoplayPressed() {
		// A trailing delay is not waited for, so another press keeps the sequence running.
		start(sequence = "o 1 1\nd 60000\no 1 2\n")
		assertEquals(1, engine.voices.size)
		val autoplay = engine.voices.toMap()
		vm.padTouch(0, 0, false)
		assertEquals("A release without a player press cannot own auto play's loop", autoplay, engine.voices)
		vm.autoPlayPause()
		assertEquals(emptyMap<Int, Int>(), engine.voices)
	}

	@Test fun finiteAndOneShotSoundsContinueWhenAutoplayStops() {
		start("1 1 1 a.wav 3\n1 1 2 a.wav 1\n", "o 1 1\no 1 2\nd 60000\nf 1 1\nf 1 2\n")
		await { engine.voices.size == 2 }
		val before = engine.voices.toMap()
		vm.switchPlayMode(PlayMode.None)
		assertEquals(before, engine.voices)
	}

	@Test fun releaseUsesThePlayedMappingRatherThanTheNextMapping() {
		start("1 1 1 a.wav 0\n1 1 1 a.wav 3\n")
		vm.switchPlayMode(PlayMode.None)
		assertEquals(emptyMap<Int, Int>(), engine.voices)
	}

	@Test fun releaseDoesNotStopFiniteSoundWhenTheNextMappingLoopsForever() {
		start("1 1 1 a.wav 3\n1 1 1 a.wav 0\n")
		val before = engine.voices.toMap()
		vm.switchPlayMode(PlayMode.None)
		assertEquals(before, engine.voices)
	}

	@Test fun releaseStopsTheLoopOnTheChainWhereAutoplayPressedIt() {
		start()
		vm.chain.value = 1
		vm.switchPlayMode(PlayMode.None)
		assertEquals(emptyMap<Int, Int>(), engine.voices)
	}

	@Test fun queuedPressDoesNotRestartTheLoopAfterPause() {
		start("1 1 1 a.wav 0\n1 1 2 a.wav 0\n", "o 1 1\nd 200\no 1 2\nd 60000\nf 1 2\n")
		// The runner emits the next press while the main looper is busy handling input.
		Thread.sleep(400)
		vm.autoPlayPause()
		main.runAll()
		assertEquals(emptyMap<Int, Int>(), engine.voices)
	}

	@Test fun stoppingTwiceIsSafeAndDoesNotReleaseManualInput() {
		start()
		vm.padTouch(0, 0, true)
		vm.switchPlayMode(PlayMode.None)
		val manual = engine.voices.toMap()
		vm.switchPlayMode(PlayMode.None)
		assertEquals(manual, engine.voices)
	}

	@Test fun interruptionReleasesEveryInputAndFiniteSound() {
		start("1 1 1 a.wav 0\n1 1 2 a.wav 0\n1 1 3 a.wav 3\n")
		vm.scbFeedbackLight.setChecked(true)
		vm.padTouch(0, 1, true)
		vm.controllerPadTouch(0, 2, true)
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
		assertEquals(emptyMap<Int, Int>(), engine.voices)
		assertNull("Screen presses must be released", vm.channelManager.get(0, 1))
		assertNull("Controller presses must be released", vm.channelManager.get(0, 2))
		val plays = engine.playCount
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)
		main.runAll()
		assertEquals(plays, engine.playCount)
		assertTrue(!vm.isAutoPlayPlaying)
	}

	@Test fun deniedManualResumeKeepsThePositionAndNeedsANewRequest() {
		start()
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
		val progress = vm.autoPlayRunner!!.progress
		val plays = engine.playCount
		vm.requestAudioFocus = { false }
		vm.autoPlayResume()
		assertTrue(!vm.isAutoPlayPlaying)
		assertTrue(vm.autoPlayRunner!!.suspended)
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)
		main.runAll()
		assertTrue(!vm.isAutoPlayPlaying)
		assertEquals(progress, vm.autoPlayRunner!!.progress)
		vm.requestAudioFocus = { vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN); true }
		vm.autoPlayResume()
		assertTrue(vm.isAutoPlayPlaying)
		assertTrue(!vm.autoPlayRunner!!.suspended)
		main.runAll()
		assertEquals("Resume does not recreate the old press", plays, engine.playCount)
	}

	private fun manualPadAfterPermanentFocusLoss(controller: Boolean) {
		start(autoplay = false)
		var requests = 0
		vm.requestAudioFocus = {
			requests++
			vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)
			true
		}
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS)
		if (controller) vm.controllerPadTouch(0, 0, true) else vm.padTouch(0, 0, true)
		assertEquals("The explicit pad press requests audio again", 1, requests)
		assertEquals("The first press after focus loss must sound", 1, engine.playCount)
		if (controller) vm.controllerPadTouch(0, 0, false) else vm.padTouch(0, 0, false)
		assertTrue(engine.voices.isEmpty())
		assertEquals("Release does not request audio", 1, requests)
	}

	@Test fun screenPadAfterPermanentFocusLossRequestsAudioAndPlays() =
		manualPadAfterPermanentFocusLoss(controller = false)

	@Test fun controllerPadAfterPermanentFocusLossRequestsAudioAndPlays() =
		manualPadAfterPermanentFocusLoss(controller = true)

	@Test fun deniedPadPressShowsNoticeAndRequiresAnotherPress() {
		start(autoplay = false)
		val ui = mockk<PlayActivityViewModel.UiCallback>(relaxed = true)
		vm.uiCallback = ui
		var requests = 0
		vm.requestAudioFocus = { requests++; false }
		vm.interruptPlayback()
		vm.padTouch(0, 0, true)
		vm.padTouch(0, 0, false)
		verify(exactly = 1) { ui.showToast(com.kimjisub.launchpad.R.string.playback_audio_unavailable) }
		assertEquals(1, requests)
		assertEquals(0, engine.playCount)
		assertNull(vm.channelManager.get(0, 0))
		vm.requestAudioFocus = {
			requests++
			vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)
			true
		}
		main.runAll()
		assertEquals("Granting a later request cannot recreate a rejected press", 0, engine.playCount)
		vm.padTouch(0, 0, true)
		assertEquals(2, requests)
		assertEquals(1, engine.playCount)
		vm.padTouch(0, 0, false)
		assertTrue(engine.voices.isEmpty())
	}

	@Test fun manualPadReacquiresAudioWithoutResumingTheInterruptedSong() {
		start()
		vm.interruptPlayback()
		val progress = vm.autoPlayRunner!!.progress
		val plays = engine.playCount
		vm.requestAudioFocus = {
			vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)
			true
		}
		vm.padTouch(0, 0, true)
		assertEquals(plays + 1, engine.playCount)
		assertTrue("Only Play can resume the song", vm.autoPlayRunner!!.suspended)
		assertTrue(!vm.isAutoPlayPlaying)
		assertEquals(PlayMode.AutoPlay, vm.playMode)
		assertEquals(progress, vm.autoPlayRunner!!.progress)
		vm.padTouch(0, 0, false)
	}

	@Test fun automaticInputCannotRequestAudioAfterFocusLoss() {
		start(autoplay = false)
		var requests = 0
		vm.requestAudioFocus = { requests++; true }
		vm.interruptPlayback()
		vm.autoPlayPadTouch(0, 0, true, SystemClock.elapsedRealtime())
		assertEquals(0, requests)
		assertEquals(0, engine.playCount)
	}

	@Test fun padWithAudioFocusDoesNotRequestItAgain() {
		start(autoplay = false)
		var requests = 0
		vm.requestAudioFocus = { requests++; true }
		vm.padTouch(0, 0, true)
		vm.padTouch(0, 0, false)
		assertEquals(0, requests)
		assertEquals(1, engine.playCount)
	}

	@Test fun deniedStartDoesNotLaunchWhenPermissionLaterReturns() {
		start(autoplay = false)
		vm.requestAudioFocus = { false }
		vm.switchPlayMode(PlayMode.AutoPlay)
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)
		main.runAll()
		assertTrue(!vm.autoPlayRunner!!.active)
		assertEquals(PlayMode.None, vm.playMode)
		assertEquals(0, engine.playCount)
	}

	@Test fun replacingASuspendedRunnerCannotResumeItOnPermission() {
		start()
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
		val old = vm.autoPlayRunner!!
		vm.initAutoPlayRunner()
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)
		main.runAll()
		assertTrue(!old.active)
		assertTrue(!vm.autoPlayRunner!!.active)
		assertTrue(!vm.isAutoPlayPlaying)
		assertTrue(engine.voices.isEmpty())
	}

	@Test fun deniedModeChangeSuspendsTheCurrentPlayback() {
		start()
		vm.requestAudioFocus = { false }
		vm.switchPlayMode(PlayMode.GuidePlay)
		assertTrue("Denied output must suspend the old playback too", !vm.isAutoPlayPlaying)
		assertEquals(PlayMode.AutoPlay, vm.playMode)
		assertTrue(vm.autoPlayRunner!!.suspended)
		assertTrue(engine.voices.isEmpty())
	}

	@Test fun onePlayRequestResumesAManuallyPausedAutoplayAfterInterruption() {
		start()
		vm.autoPlayPause()
		assertTrue(!vm.autoPlayRunner!!.playmode)
		vm.interruptPlayback()
		vm.audioFocusPolicy.onFocusChange(AudioManager.AUDIOFOCUS_GAIN)
		vm.autoPlayResume()
		assertTrue("One explicit Play request must start the preserved auto-play mode", vm.autoPlayRunner!!.playmode)
		assertTrue(vm.isAutoPlayPlaying)
		assertEquals(PlayMode.AutoPlay, vm.playMode)
	}

	@Test fun naturalEndKeepsTheLoopHeldByThePlayer() {
		start("1 1 1 a.wav 0\n1 1 2 a.wav 0\n", sequence = "on 1 1\ndelay 100\n", autoplay = false)
		vm.padTouch(0, 1, true)
		val manualVoice = engine.playCount
		vm.switchPlayMode(PlayMode.AutoPlay)
		await { vm.playMode == PlayMode.None && !vm.autoPlayRunner!!.active }
		assertEquals("Only autoplay's unmatched press is released at the end", mapOf(manualVoice to -1), engine.voices)
		vm.padTouch(0, 1, false)
		assertTrue(engine.voices.isEmpty())
	}

	@Test fun naturalEndReleasesAnUnmatchedAutoplayPress() {
		start(sequence = "o 1 1\nd 100\n")
		await { vm.playMode == PlayMode.None }
		assertEquals(emptyMap<Int, Int>(), engine.voices)
	}
}
