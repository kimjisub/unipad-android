package com.kimjisub.launchpad.viewmodel

import android.os.SystemClock
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.unipack.UniPackFolder
import com.kimjisub.launchpad.unipack.runner.RecordingSoundEngine
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/** Runs the parsed sequence and sound runner together; a parsed chain move must not run twice. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayActivityViewModelAutoPlayChainTimingTest {
	@get:Rule val tmp = TemporaryFolder()
	private val scheduler = TestCoroutineScheduler()
	private val clock = AtomicLong()
	private val engine = RecordingSoundEngine()
	private lateinit var vm: PlayActivityViewModel
	private val chains = mutableListOf<Pair<Long, Int>>()

	@Before fun setUp() {
		Dispatchers.setMain(StandardTestDispatcher(scheduler))
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { clock.get() }
	}

	@After fun tearDown() {
		clearPlayViewModel(if (::vm.isInitialized) vm else null, drain = { scheduler.runCurrent() }) {
			scheduler.runCurrent()
			unmockkStatic(SystemClock::class)
			Dispatchers.resetMain()
		}
	}

	private fun await(check: () -> Boolean) {
		val deadline = System.nanoTime() + 5_000_000_000L
		while (true) {
			scheduler.runCurrent()
			if (check()) return
			assertTrue("The playback operation did not finish", System.nanoTime() < deadline)
			Thread.sleep(1)
		}
	}

	private fun start() {
		val root = tmp.newFolder()
		File(root, "info").writeText("title=Test\nproducerName=Test\nbuttonX=2\nbuttonY=2\nchain=3\nsquareButton=true\n")
		File(root, "sounds").mkdir()
		File(root, "sounds/a.wav").writeBytes(byteArrayOf(1))
		File(root, "keySound").writeText("1 1 1 a.wav 1 2\n3 2 1 a.wav\n")
		File(root, "autoPlay").writeText("on 1 1\ndelay 50\nchain 3\ndelay 100\non 2 1\ndelay 60000\n")
		vm = PlayActivityViewModel(mockk<UnipackRepository>(relaxed = true), UsageAnalytics { _, _ -> }, engine)
		vm.unipack = UniPackFolder(root).load().loadDetail()
		vm.initState()
		vm.initPlayback()
		await { !vm.soundLoadingActive && (vm.unipack.soundGet(0, 0, 0)?.id ?: -1) >= 0 }
		vm.chain.addObserver { c, _ -> chains += clock.get() to c }
		vm.switchPlayMode(PlayMode.AutoPlay)
		await { engine.plays.size == 1 && vm.chain.value == 1 }
	}

	private fun moveTo(ms: Long) {
		clock.set(ms)
		scheduler.advanceTimeBy(ms - scheduler.currentTime)
		scheduler.runCurrent()
	}

	@Test fun parsedWormholeDoesNotOverrideTheFollowingChainCommand() {
		start()
		moveTo(50)
		await { vm.chain.value == 2 }
		moveTo(150)
		await { engine.plays.size == 2 }
		assertEquals(listOf(0L to 1, 50L to 2), chains)
	}

	@Test fun stoppingAutoplayLeavesNoDelayedWormhole() {
		start()
		moveTo(50)
		await { vm.chain.value == 2 }
		vm.switchPlayMode(PlayMode.None)
		scheduler.runCurrent()
		val before = chains.toList()
		moveTo(150)
		assertEquals("A stopped sequence must not move the chain later", before, chains)
		assertEquals(2, vm.chain.value)
	}
}
