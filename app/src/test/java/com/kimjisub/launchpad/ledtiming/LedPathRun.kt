package com.kimjisub.launchpad.ledtiming

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.kimjisub.launchpad.analytics.UsageAnalytics
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.manager.ChannelManager
import com.kimjisub.launchpad.midi.driver.DriverRef
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.runner.AutoPlayRunner
import com.kimjisub.launchpad.unipack.runner.LedRunner
import com.kimjisub.launchpad.viewmodel.PlayActivityViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/** A command as LedRunner handed it over, or as it reached the Launchpad driver, [ms] after autoplay started. */
data class SentCommand(override val ms: Double, override val target: LedTarget, override val code: Int) : LedCommand

class Recording(
	/** LedRunner's changes as commands, in the order and at the time it handed them over. */
	val handedOver: List<SentCommand>,
	/** Commands that carried an LED runner change, in the order the driver received them. */
	val toDriver: List<SentCommand>,
	/** When each autoplay press reached the view model on main, in order. */
	val pressTimes: List<Double>,
	/** Commands a release sent to repaint its pad, which no keyLED event asked for. */
	val releaseRepaints: Int,
	/** LED runner commands that went out with another channel's colour on top. */
	val covered: Int,
	/**
	 * How long after the final release the run stopped counting arrivals with animations still playing;
	 * null when they all ended first. [handedOver] and [toDriver] hold what arrived until then.
	 */
	val gaveUpAfterReleaseMs: Long?,
	/** Commands that reached the driver after the run gave up, while it went on to tell lag from loss. */
	val toDriverLate: List<SentCommand> = emptyList(),
	/** How long the run went on after giving up. */
	val lateForMs: Long = 0,
)

/**
 * Plays a pack's autoplay in real time through the play screen's own LED path, from AutoPlayRunner
 * to the Launchpad driver, and records what the driver is asked to send:
 *
 * AutoPlayRunner (its own thread) → main → PlayActivityViewModel.padTouch → LedRunner (its 4 ms
 * loop) → LedChangeQueue → main → ledRunnerListener → ChannelManager → UiCallback.setLedPad/Chain →
 * the driver call PlayActivity.setLedLaunchpad/setLedLaunchpadChain makes.
 *
 * What LedRunner hands over is recorded too, so a difference can be placed before or after it.
 *
 * Every part is the app's, on the real clock, except three stand-ins: a thread for the main looper,
 * the AutoPlayRunner listener (the three calls PlayActivityViewModel.initAutoPlayRunner's listener
 * makes for presses, releases and chains), and the activity's two driver calls. The feedback light and
 * practice guide stay off, as in autoplay, so no other channel covers the pack's LEDs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LedPathRun(private val pack: UniPack) {

	private val main = MainThread()
	private val vm = PlayActivityViewModel(mockk<UnipackRepository>(relaxed = true), UsageAnalytics { _, _ -> })
	private lateinit var ledRunner: LedRunner

	@Volatile
	private var startNanos = 0L
	private val ended = CountDownLatch(1)

	// LED runner thread only.
	private val handedOver = ArrayList<SentCommand>()
	// Main thread only.
	private val sent = ArrayList<SentCommand>()
	private val presses = ArrayList<Double>()
	private var releaseRepaints = 0
	private var covered = 0
	private var inPadEvent = false

	private fun now(): Double = (System.nanoTime() - startNanos) / 1_000_000.0

	private val driver = object : DriverRef() {
		var channel: ChannelManager.Channel? = null

		override fun sendPadLed(x: Int, y: Int, velocity: Int) = record(LedTarget(x, y), velocity)
		override fun sendFunctionKeyLed(f: Int, velocity: Int) = record(LedTarget(-1, f), velocity)
		override fun sendChainLed(c: Int, velocity: Int) {}
		override fun sendClearLed() {}

		private fun record(target: LedTarget, code: Int) {
			if (inPadEvent) {
				releaseRepaints++
				return
			}
			if (channel != null && channel != ChannelManager.Channel.LED) covered++
			sent += SentCommand(now(), target, code)
		}
	}

	private val activity = object : PlayActivityViewModel.UiCallback {
		override fun setLedPad(x: Int, y: Int) {
			if (!vm.enable) return
			val item = vm.channelManager.get(x, y)
			driver.channel = item?.channel
			driver.sendPadLed(x, y, item?.code ?: 0)
		}

		override fun setLedChain(c: Int) {
			if (!vm.enable) return
			val item = vm.channelManager.get(-1, c)
			driver.channel = item?.channel
			driver.sendFunctionKeyLed(c, item?.code ?: 0)
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

	private fun padEvent(block: () -> Unit) {
		inPadEvent = true
		try {
			block()
		} finally {
			inPadEvent = false
		}
	}

	/** The view model's listener, recording each tick's changes before passing them on. */
	private val ledListener = object : LedRunner.Listener by vm.ledRunnerListener {
		override fun onLedChanges(changes: List<LedRunner.LedChange>) {
			val at = now()
			for (change in changes) {
				handedOver += when (change) {
					is LedRunner.LedChange.PadOn -> SentCommand(at, LedTarget(change.x, change.y), change.velocity)
					is LedRunner.LedChange.PadOff -> SentCommand(at, LedTarget(change.x, change.y), 0)
					is LedRunner.LedChange.ChainLedOn -> SentCommand(at, LedTarget(-1, change.c), change.velocity)
					is LedRunner.LedChange.ChainLedOff -> SentCommand(at, LedTarget(-1, change.c), 0)
					is LedRunner.LedChange.ChainChange -> continue
				}
			}
			vm.ledRunnerListener.onLedChanges(changes)
		}
	}

	private val autoPlayListener = object : AutoPlayRunner.Listener {
		override fun onStart() {
			startNanos = System.nanoTime()
		}

		override fun onPadTouchOn(x: Int, y: Int, atMs: Long) {
			vm.viewModelScope.launch {
				presses += now()
				padEvent { vm.autoPlayPadTouch(x, y, true, atMs) }
			}
		}

		override fun onPadTouchOff(x: Int, y: Int, atMs: Long) {
			vm.viewModelScope.launch { padEvent { vm.autoPlayPadTouch(x, y, false, atMs) } }
		}

		override fun onChainChange(c: Int) {
			vm.viewModelScope.launch { vm.chain.value = c }
		}

		override fun onEnd() = ended.countDown()
		override fun onGuidePadOn(x: Int, y: Int, targetWallTimeMs: Long) {}
		override fun onGuidePadOff(x: Int, y: Int) {}
		override fun onGuideLedUpdate(x: Int, y: Int, velocity: Int) {}
		override fun onGuideChainOn(c: Int) {}
		override fun onRemoveGuide() {}
		override fun chainButsRefresh() {}
		override fun onProgressUpdate(progress: Int) {}
	}

	/**
	 * Plays the autoplay, releases every pad at [releaseAllAtMs] as PlayActivityViewModel.padInit does
	 * (ending endless animations), waits until no animation runs or [giveUpAtMs] passes, and returns what
	 * the driver received. Times count from AutoPlayRunner's start.
	 *
	 * Every command the pack asks for before [giveUpAtMs] is due by then, so a run that gives up keeps
	 * going while LedRunner still hands changes over (up to [LATE_MAX_MS]) and records what arrives late
	 * apart: commands that only came late were delayed, commands that never came were lost.
	 */
	fun run(releaseAllAtMs: Long, giveUpAtMs: Long): Recording {
		Dispatchers.setMain(main)
		mockkStatic(SystemClock::class)
		every { SystemClock.elapsedRealtime() } answers { System.nanoTime() / 1_000_000 }
		val autoPlay = AutoPlayRunner(pack, autoPlayListener, vm.chain)
		try {
			main.runAndWait {
				vm.unipack = pack
				vm.channelManager = ChannelManager(pack.buttonX, pack.buttonY)
				vm.chain.range = 0 until pack.chain
				vm.uiCallback = activity
				ledRunner = LedRunner(pack, ledListener, vm.chain)
				vm.ledRunner = ledRunner
				ledRunner.launch()
			}
			autoPlay.launch()
			check(ended.await(giveUpAtMs + 60_000, TimeUnit.MILLISECONDS)) { "autoplay did not end" }

			sleepUntil(releaseAllAtMs)
			val releasedAt = main.runAndWait {
				padEvent { vm.padInit() }
				now()
			}
			var gaveUp = false
			while (animating()) {
				if (now() >= giveUpAtMs) {
					gaveUp = true
					break
				}
				Thread.sleep(20)
			}
			// The last tick may still be on its way to main.
			Thread.sleep(50)
			var inTime = Int.MAX_VALUE
			var gaveUpAfterReleaseMs: Long? = null
			var lateForMs = 0L
			if (gaveUp) {
				inTime = ledRunner.afterDelivery { handedOver.size }
				val gaveUpAt = now()
				gaveUpAfterReleaseMs = (gaveUpAt - releasedAt).toLong()
				var count = inTime
				var changedAt = gaveUpAt
				while (animating() && now() - gaveUpAt < LATE_MAX_MS && now() - changedAt < LATE_IDLE_MS) {
					Thread.sleep(20)
					val next = ledRunner.afterDelivery { handedOver.size }
					if (next != count) {
						count = next
						changedAt = now()
					}
				}
				Thread.sleep(50)
				lateForMs = (now() - gaveUpAt).toLong()
			}
			ledRunner.stop()
			main.drain()
			// The driver gets one command per change handed over (deliveredAsHandedOver), so the first
			// inTime of each line up.
			return main.runAndWait {
				Recording(
					handedOver.take(inTime), sent.take(inTime), ArrayList(presses), releaseRepaints, covered,
					gaveUpAfterReleaseMs, sent.drop(inTime), lateForMs,
				)
			}
		} finally {
			autoPlay.stop()
			if (::ledRunner.isInitialized) ledRunner.stop()
			main.close()
			unmockkStatic(SystemClock::class)
			Dispatchers.resetMain()
		}
	}

	private companion object {
		/** After giving up, the run stops waiting for late commands once LedRunner hands nothing over for this long. */
		const val LATE_IDLE_MS = 5_000L
		const val LATE_MAX_MS = 60_000L
	}

	private fun animating(): Boolean =
		(0 until pack.buttonX).any { x -> (0 until pack.buttonY).any { y -> ledRunner.isEventExist(x, y) } }

	private fun sleepUntil(ms: Long) {
		val wait = ms - now()
		if (wait > 0) Thread.sleep(wait.toLong())
	}

	/** The main looper: one thread running posted tasks in order, so Main.immediate runs in place on it. */
	private class MainThread : MainCoroutineDispatcher(), AutoCloseable {
		private val queue = LinkedBlockingQueue<Runnable>()
		private val errors = ArrayList<Throwable>()
		private val thread = Thread({
			while (true) {
				val task = queue.take()
				if (task === STOP) return@Thread
				try {
					task.run()
				} catch (e: Throwable) {
					synchronized(errors) { errors += e }
				}
			}
		}, "led-timing-main").apply {
			// A coroutine on main hands its exception to the thread's handler rather than to the task.
			uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { _, e -> synchronized(errors) { errors += e } }
			start()
		}

		override val immediate: MainCoroutineDispatcher = object : MainCoroutineDispatcher() {
			override val immediate: MainCoroutineDispatcher get() = this
			override fun isDispatchNeeded(context: CoroutineContext) = Thread.currentThread() !== thread
			override fun dispatch(context: CoroutineContext, block: Runnable) = this@MainThread.dispatch(context, block)
		}

		override fun dispatch(context: CoroutineContext, block: Runnable) {
			queue.put(block)
		}

		fun <T> runAndWait(block: () -> T): T {
			val done = CountDownLatch(1)
			var result: Result<T>? = null
			queue.put(Runnable {
				result = runCatching(block)
				done.countDown()
			})
			check(done.await(60, TimeUnit.SECONDS)) { "main thread did not run the task" }
			synchronized(errors) { errors.firstOrNull()?.let { throw AssertionError("main thread task failed", it) } }
			return result!!.getOrThrow()
		}

		/** Runs main until a pass leaves nothing new behind; a large backlog reposts itself per portion. */
		fun drain() {
			while (true) {
				runAndWait {}
				if (queue.isEmpty()) return
			}
		}

		override fun close() {
			queue.put(STOP)
			thread.join(10_000)
		}

		private companion object {
			val STOP = Runnable {}
		}
	}
}
