package com.kimjisub.launchpad.unipack.runner

import android.os.SystemClock
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AutoPlayRunner(
	private val unipack: UniPack,
	private val listener: Listener,
	private val chain: ChainObserver,
	private val loopDelay: Long = 1L,
	private val eventDispatcher: CoroutineDispatcher = Dispatchers.Default,
	/** Parent of every run; sharing one across runners lets their owner tell when all of them have ended. */
	runs: Job = SupervisorJob(),
) {
	private val pauseLock = Any()
	private val suspensionLock = Any()
	private var pausedSince: Long? = null
	private var completedPauseMs = 0L

	@Volatile
	var playmode = true
		set(value) {
			// Record the transition itself: a pause and resume can both happen between loop ticks.
			synchronized(pauseLock) {
				if (field != value) {
					val now = SystemClock.elapsedRealtime()
					if (value) {
						pausedSince?.let { completedPauseMs += (now - it).coerceAtLeast(0) }
						pausedSince = null
					} else pausedSince = now
					field = value
				}
			}
			wake()
		}

	private fun totalPausedAt(now: Long): Long = synchronized(pauseLock) {
		completedPauseMs + (pausedSince?.let { (now - it).coerceAtLeast(0) } ?: 0L)
	}
	@Volatile
	var beforeStartPlaying = true

	@Volatile
	var practiceGuide = false
		set(value) {
			field = value
			wake()
		}

	@Volatile
	var stepMode = false
		set(value) {
			field = value
			wake()
		}

	@Volatile
	var suspended = false
		private set
	private var playmodeBeforeSuspension = false

	/** Unlike an ordinary pause, a covered screen cannot advance step practice or publish guides. */
	fun suspendPlayback() = synchronized(suspensionLock) {
		if (!suspended && active) {
			playmodeBeforeSuspension = playmode
			suspended = true
			playmode = false
		}
	}

	fun resumeAfterInterruption() = synchronized(suspensionLock) {
		if (!suspended) return@synchronized
		suspended = false
		beforeStartPlaying = true
		playmode = playmodeBeforeSuspension
		if (stepMode && practiceGuide) {
			synchronized(stepPendingPads) {
				for (key in stepPendingPads) {
					listener.onGuidePadOn(key / 256, key % 256, 0)
					listener.onGuideLedUpdate(key / 256, key % 256, GUIDE_VELOCITIES.last())
				}
			}
		}
		if (waitingForChain >= 0) listener.onGuideChainOn(waitingForChain)
		wake()
	}

	// While not playing, the loop has nothing to do until a mode flag, the progress, a step pad or
	// the chain changes, so it sleeps on this signal instead of polling every loopDelay. CONFLATED
	// keeps a wake sent between the idle check and receive() from being lost.
	private val wakeSignal = Channel<Unit>(Channel.CONFLATED)
	private val wakeOnChainChange: (Int, Int) -> Unit = { _, _ -> wake() }

	private fun wake() {
		wakeSignal.trySend(Unit)
	}

	// Written by both the runner coroutine and the UI (progressOffset), so every
	// read-modify-write goes through the atomic and element reads use a snapshot index.
	private val progressValue = AtomicInteger(0)
	// The UI publishes a target; only the loop rebases its clock and guide state.
	private data class Seek(val index: Int, val wallTimeMs: Long, val pausedMs: Long)
	private val pendingSeek = AtomicReference<Seek?>(null)
	private val pendingResume = AtomicBoolean(false)
	private val pendingWaitReset = AtomicBoolean(false)
	private val pendingAutoPlay = AtomicBoolean(false)
	val progress: Int
		get() = progressValue.get()

	private fun setProgress(value: Int) {
		progressValue.set(value)
		listener.onProgressUpdate(value)
	}

	private fun elementAtProgress(autoPlay: AutoPlay): IndexedValue<AutoPlay.Element>? {
		val index = progressValue.get()
		return autoPlay.elements.getOrNull(index)?.let { IndexedValue(index, it) }
	}

	// Skipped when progress was moved elsewhere meanwhile, so a user jump is not overwritten.
	private fun advanceProgressFrom(index: Int) {
		if (progressValue.compareAndSet(index, index + 1)) listener.onProgressUpdate(index + 1)
	}

	private val scope = CoroutineScope(Dispatchers.Default + runs)
	private var job: Job? = null
	val active: Boolean
		get() = job?.isActive == true

	interface Listener {
		fun onStart()
		/**
		 * Presses (x, y) with the pad's [num]th sound and light of chain [c]. [atMs] is when the pack places
		 * the press, on the SystemClock.elapsedRealtime clock. The order is set where the press is handled,
		 * after any chain change sent before it, whose reset would otherwise undo it.
		 */
		fun onPadTouchOn(x: Int, y: Int, c: Int, num: Int, atMs: Long)
		fun onPadTouchOff(x: Int, y: Int, atMs: Long)
		fun onChainChange(c: Int)
		fun onGuidePadOn(x: Int, y: Int, targetWallTimeMs: Long)
		fun onGuidePadOff(x: Int, y: Int)
		fun onGuideLedUpdate(x: Int, y: Int, velocity: Int)
		fun onGuideChainOn(c: Int)
		fun onRemoveGuide()
		fun chainButsRefresh()
		fun onProgressUpdate(progress: Int)
		fun onEnd()
	}

	// Guide timeline for practice mode
	private data class GuideEvent(val index: Int, val timeMs: Long, val x: Int, val y: Int, val chain: Int)
	private var guideTimeline: List<GuideEvent> = emptyList()
	private var guideIndex = 0
	private var waitingForChain = -1

	// Active guide tracking for LED brightness + auto-expiration
	private val activeGuides = mutableMapOf<Int, Long>() // key = x*256+y, value = targetWallTimeMs
	private var lastGuideUpdateMs = 0L

	// Step mode state
	private val stepPendingPads = mutableSetOf<Int>()
	private val pressedKeysQueue = mutableListOf<Int>()
	private val pressedKeysLock = Any()
	private var stepScanned = false
	private var stepStartProgress = 0
	// Where the first scan of this practice began, so leaving it can tell which shown notes were played.
	private var stepOrigin = -1
	private var stepChainValue = -1

	private fun hasPendingStep() = synchronized(stepPendingPads) { stepPendingPads.isNotEmpty() }

	private fun clearGuides() {
		val keys = synchronized(activeGuides) {
			activeGuides.keys.toMutableSet().also { activeGuides.clear() }
		}
		synchronized(stepPendingPads) {
			keys.addAll(stepPendingPads)
			stepPendingPads.clear()
		}
		for (key in keys) {
			listener.onGuideLedUpdate(key / 256, key % 256, 0)
			listener.onGuidePadOff(key / 256, key % 256)
		}
		listener.onRemoveGuide()
	}

	companion object {
		const val GUIDE_LOOKAHEAD_MS = 800L
		private const val GUIDE_LED_UPDATE_INTERVAL_MS = 50L
		private val GUIDE_VELOCITIES = intArrayOf(1, 2, 3, 21) // dim white → bright white → green
		private const val STEP_GROUP_THRESHOLD_MS = 50L
	}

	private fun guideKey(x: Int, y: Int) = x * 256 + y

	/**
	 * How step practice is left. [owedPads] are the pads of the shown group that were not pressed, and
	 * [firstOwed] the first of them in the song; [end] is where the scan stopped. [played] holds the
	 * touch indices it passed as pressed, and [lastPlayedSongTimeMs] the song time of the last of them
	 * on a selectable chain, or null when none was.
	 */
	private data class StepExit(
		val end: Int,
		val owedPads: Set<Int>,
		val firstOwed: Int?,
		val played: Set<Int>,
		val lastPlayedSongTimeMs: Long?,
	)

	private fun stepExit(autoPlay: AutoPlay): StepExit {
		val end = progress
		val (owed, groupStart, origin) = synchronized(stepPendingPads) {
			Triple(stepPendingPads.toSet(), stepStartProgress, stepOrigin)
		}
		var songTimeMs = 0L
		var firstOwed: Int? = null
		val played = mutableSetOf<Int>()
		var lastPlayed: Long? = null
		for (index in 0 until end) {
			when (val element = autoPlay.elements[index]) {
				is AutoPlay.Element.Delay -> songTimeMs += element.delay
				is AutoPlay.Element.On -> {
					val owedHere = index >= groupStart && guideKey(element.x, element.y) in owed
					if (owedHere) {
						if (firstOwed == null) firstOwed = index
					} else if (origin in 0..index) {
						played += index
						if (isSelectable(element.currChain)) lastPlayed = songTimeMs
					}
				}
				else -> {}
			}
		}
		return StepExit(end, owed, firstOwed, played, lastPlayed)
	}

	/** A guide pad on a chain the user cannot select would be waited for forever, so it is passed over. */
	private fun isSelectable(c: Int) = c in chain.range

	private fun buildGuideTimeline(autoPlay: AutoPlay): List<GuideEvent> {
		val events = mutableListOf<GuideEvent>()
		var time = 0L
		for ((index, element) in autoPlay.elements.withIndex()) {
			when (element) {
				is AutoPlay.Element.Delay -> time += element.delay
				is AutoPlay.Element.On -> events.add(GuideEvent(index, time, element.x, element.y, element.currChain))
				else -> {}
			}
		}
		return events
	}

	// Coroutine

	fun launch() {
		Log.thread("[AutoPlay] 1. Request Coroutine")
		if (job?.isActive != true) {
			job = scope.launch {
				Log.thread("[AutoPlay] 2. Start Coroutine")
				synchronized(progressValue) {
					pendingSeek.set(null)
					pendingResume.set(false)
					pendingWaitReset.set(false)
					pendingAutoPlay.set(false)
					setProgress(0)
				}
				clearStepProgress()
				listener.onStart()

				val autoPlay = unipack.autoPlayTable
				if (autoPlay == null) {
					// onStart already put the UI into "playing"; without onEnd it stays there.
					listener.onEnd()
					return@launch
				}

				waitingForChain = -1
				if (practiceGuide) {
					guideTimeline = buildGuideTimeline(autoPlay)
					guideIndex = 0
					synchronized(activeGuides) { activeGuides.clear() }
				}

				chain.addObserver(wakeOnChainChange)
				try {
					var delayAccum: Long = 0
					var startTime = SystemClock.elapsedRealtime()
					var accountedPauseMs = totalPausedAt(startTime)
					var prevPracticeGuide = practiceGuide
					var rebaseSeekOnResume = false
					// Touches pressed in step practice since the last seek. Each pause adds to them, so a
					// later pause that scans less of the song does not hand back what an earlier one played.
					val stepPlayed = mutableSetOf<Int>()
					fun isOwed(index: Int) = index !in stepPlayed
					// Leaves step practice, counting input queued just before the mode change as played.
					fun leaveStep(): StepExit {
						drainPressedKeys()
						return stepExit(autoPlay).also { stepPlayed += it.played }
					}
					// Set when timed guidance resumes from a step, until a hint is given ahead of its time:
					// a note already due then is still shown, briefly, rather than lit and cleared in the
					// same pass, even when a chain wait delays it; the song clock is not moved for it.
					var showLateHints = false
					// Only timed guide waits own wall time; step-mode chain hints do not.
					var timedWaitStart: Long? = null
					fun finishChainWait(now: Long) {
						timedWaitStart?.let { startTime += (now - it).coerceAtLeast(0) }
						timedWaitStart = null
						waitingForChain = -1
					}
					var currTime = startTime
					fun hasPendingDelay() = !(stepMode && practiceGuide) && delayAccum > currTime - startTime
					// A timed hint still on screen after the last command keeps the loop until it expires.
					fun hasTimedHint() = practiceGuide && playmode && synchronized(activeGuides) { activeGuides.isNotEmpty() }
					while ((progress < autoPlay.elements.size || pendingSeek.get() != null || pendingResume.get() || pendingAutoPlay.get() ||
						stepMode && practiceGuide && hasPendingStep() || suspended || hasPendingDelay() || hasTimedHint()) && isActive) {
						if (suspended) {
							wakeSignal.receive()
							continue
						}
						val seek: Seek?
						val playing: Boolean
						val resuming: Boolean
						val switchingToAutoPlay: Boolean
						synchronized(progressValue) {
							seek = pendingSeek.getAndSet(null)
							// A step scan or command pass may have moved on from the target after the
							// UI published it; the seek's position wins.
							if (seek != null && progress != seek.index) setProgress(seek.index)
							// Read the mode with the request, so a pause before the UI's seek
							// cannot be mistaken for the playing state from an earlier tick.
							playing = playmode
							// A newer pause supersedes a resume even before its first timed tick.
							val resumeRequested = pendingResume.getAndSet(false)
							resuming = playing && resumeRequested
							switchingToAutoPlay = pendingAutoPlay.getAndSet(false)
						}
						var target = seek?.index ?: if (playing && rebaseSeekOnResume) progress else null
						currTime = SystemClock.elapsedRealtime()
						// Song time the target resumes at, when it is not where the target's own commands start.
						var targetSongTimeMs: Long? = null
						var keepSongClock = false
						var resumingFromStep = false
						if (switchingToAutoPlay) {
							// A shown step has advanced progress without playing it. Recover only the unplayed pads.
							val exit = leaveStep()
							if (seek == null && exit.firstOwed != null) {
								target = exit.firstOwed
								setProgress(exit.firstOwed)
							}
							clearStepProgress()
						} else if (resuming && practiceGuide && seek == null &&
							synchronized(stepPendingPads) { stepOrigin >= 0 }) {
							// Timed guidance after a paused step: a shown note left unpressed is still owed at
							// its own song time, while a pressed one is played and the written gap after it
							// starts now.
							val exit = leaveStep()
							// The step light gives way to the timed hint, which relights the pad as its time nears.
							for (key in exit.owedPads) listener.onGuideLedUpdate(key / 256, key % 256, 0)
							targetSongTimeMs = exit.lastPlayedSongTimeMs
							keepSongClock = targetSongTimeMs == null && target == null
							resumingFromStep = true
							target = exit.firstOwed ?: exit.end
							setProgress(target)
						}
						if (target != null) {
							finishChainWait(currTime)
							showLateHints = resumingFromStep
							// Step practice can advance after a paused seek. On resume, align to
							// that current position without replaying the seek.
							rebaseSeekOnResume = !playing
							// Only the loop scans steps, so a seek's step reset happens here, before
							// the scan of the new position, and never after it.
							if (seek != null) {
								clearStepProgress()
								stepPlayed.clear()
							}
							delayAccum = 0
							for (index in 0 until target) {
								val element = autoPlay.elements[index]
								if (element is AutoPlay.Element.Delay) delayAccum += element.delay
							}
							if (!keepSongClock) {
								startTime = (seek?.wallTimeMs ?: currTime) - (targetSongTimeMs ?: delayAccum)
								accountedPauseMs = seek?.pausedMs ?: totalPausedAt(currTime)
							}
							synchronized(activeGuides) {
								for ((key, _) in activeGuides) listener.onGuideLedUpdate(key / 256, key % 256, 0)
								activeGuides.clear()
							}
							listener.onRemoveGuide()
							guideIndex = guideTimeline.indexOfFirst { it.index >= target }
								.let { if (it < 0) guideTimeline.size else it }
							lastGuideUpdateMs = currTime - GUIDE_LED_UPDATE_INTERVAL_MS
						}

						val totalPausedMs = totalPausedAt(currTime)
						val pausedMs = totalPausedMs - accountedPauseMs
						if (pausedMs > 0) {
							startTime += pausedMs
							timedWaitStart = timedWaitStart?.plus(pausedMs)
							synchronized(activeGuides) {
								for (entry in activeGuides.entries) entry.setValue(entry.value + pausedMs)
							}
							accountedPauseMs = totalPausedMs
						}

						if (resuming || switchingToAutoPlay) {
							clearStepProgress()
							finishChainWait(currTime)
						}
						if (pendingWaitReset.getAndSet(false)) finishChainWait(currTime)

						// Detect mid-run practice mode toggle
						if (practiceGuide != prevPracticeGuide) {
							finishChainWait(currTime)
							if (practiceGuide) {
								// Switched TO practice mode — initialize guide state
								guideTimeline = buildGuideTimeline(autoPlay)
								val elapsed = currTime - startTime
								guideIndex = guideTimeline.indexOfFirst { it.timeMs > elapsed - GUIDE_LOOKAHEAD_MS }
									.let { if (it < 0) guideTimeline.size else it }
								synchronized(activeGuides) { activeGuides.clear() }
							} else {
								// Switched FROM practice mode — clean up guides
								synchronized(activeGuides) {
									for ((key, _) in activeGuides) {
										listener.onGuideLedUpdate(key / 256, key % 256, 0)
									}
									activeGuides.clear()
								}
								guideTimeline = emptyList()
								listener.onRemoveGuide()
							}
							prevPracticeGuide = practiceGuide
						}

						if (!suspended && playing && playmode && pendingSeek.get() == null) {
							// Practice mode: waiting for chain change
							if (practiceGuide && waitingForChain >= 0) {
								if (chain.value == waitingForChain) {
									// User switched to correct chain — resume
									finishChainWait(currTime)
									listener.onRemoveGuide()
								}
							} else {
								beforeStartPlaying(currTime)

								// Guide lookahead
								if (practiceGuide) {
									val elapsed = currTime - startTime
									while (guideIndex < guideTimeline.size) {
										val event = guideTimeline[guideIndex]
										if (event.timeMs <= elapsed + GUIDE_LOOKAHEAD_MS) {
											if (!isSelectable(event.chain) || !isOwed(event.index)) {
												guideIndex++
												continue
											}
											if (event.chain != chain.value) {
												// Chain mismatch — pause and show chain indicator
												waitingForChain = event.chain
												timedWaitStart = currTime
												synchronized(activeGuides) {
													for ((key, _) in activeGuides) {
														listener.onGuideLedUpdate(key / 256, key % 256, 0)
													}
													activeGuides.clear()
												}
												listener.onRemoveGuide()
												listener.onGuideChainOn(event.chain)
												break
											}
											val dueWallTimeMs = startTime + event.timeMs
											val late = showLateHints && dueWallTimeMs <= currTime
											val targetWallTimeMs = if (late) currTime + GUIDE_LED_UPDATE_INTERVAL_MS else dueWallTimeMs
											// A late hint's controller light is sent lit on this pass, not at the next update.
											if (late) lastGuideUpdateMs = currTime - GUIDE_LED_UPDATE_INTERVAL_MS
											else showLateHints = false
											synchronized(activeGuides) { activeGuides[guideKey(event.x, event.y)] = targetWallTimeMs }
											listener.onGuidePadOn(event.x, event.y, targetWallTimeMs)
											guideIndex++
										} else break
									}

									// Guide expiration + launchpad LED brightness update
									synchronized(activeGuides) {
										if (activeGuides.isNotEmpty()) {
											val throttle = currTime - lastGuideUpdateMs >= GUIDE_LED_UPDATE_INTERVAL_MS
											val iter = activeGuides.iterator()
											while (iter.hasNext()) {
												val (key, targetMs) = iter.next()
												val gx = key / 256
												val gy = key % 256
												if (currTime >= targetMs) {
													// Guide expired — auto-remove
													iter.remove()
													listener.onGuideLedUpdate(gx, gy, 0)
													listener.onGuidePadOff(gx, gy)
												} else if (throttle) {
													val remaining = targetMs - currTime
													val p = (1f - remaining.toFloat() / GUIDE_LOOKAHEAD_MS).coerceIn(0f, 1f)
													val idx = (p * GUIDE_VELOCITIES.size).toInt().coerceIn(0, GUIDE_VELOCITIES.lastIndex)
													listener.onGuideLedUpdate(gx, gy, GUIDE_VELOCITIES[idx])
												}
											}
											if (throttle) lastGuideUpdateMs = currTime
										}
									}
								}

								// Every command due now is handled in one pass on the event thread: presses placed
								// together sound together, and a press sees the chain the command before it chose.
								// Progress and playmode are read there, as seeking or pausing may have happened
								// while delivery waited.
								if (waitingForChain < 0 && delayAccum <= currTime - startTime) withContext(eventDispatcher) {
									while (!suspended && playmode && pendingSeek.get() == null && delayAccum <= currTime - startTime) {
										val (index, element) = elementAtProgress(autoPlay) ?: break
										when (element) {
											is AutoPlay.Element.On -> {
												if (!practiceGuide && isOwed(index)) {
													if (chain.value != element.currChain) listener.onChainChange(element.currChain)
													listener.onPadTouchOn(element.x, element.y, element.currChain, element.num, startTime + delayAccum)
												}
											}

											is AutoPlay.Element.Off -> {
												if (!practiceGuide) {
													if (chain.value != element.currChain) listener.onChainChange(element.currChain)
													listener.onPadTouchOff(element.x, element.y, startTime + delayAccum)
												}
											}
											is AutoPlay.Element.Delay -> {
												delayAccum += element.delay.toLong()
											}
											is AutoPlay.Element.Chain -> {
												if (!practiceGuide) {
													listener.onChainChange(element.c)
												}
											}
										}
										advanceProgressFrom(index)
									}
								}
							}
						} else {
							beforeStartPlaying = true

							if (stepMode && practiceGuide) synchronized(suspensionLock) {
								if (suspended) return@synchronized
								drainPressedKeys()

								val currentChain = chain.value

								if (currentChain != stepChainValue && stepChainValue >= 0) {
									synchronized(stepPendingPads) {
										if (stepScanned) {
											setProgress(stepStartProgress)
											stepPendingPads.clear()
											stepScanned = false
										}
									}
									finishChainWait(currTime)
								}
								stepChainValue = currentChain

								var needsScan = false

								if (waitingForChain >= 0) {
									if (currentChain == waitingForChain) {
										finishChainWait(currTime)
										needsScan = true
									}
								} else {
									synchronized(stepPendingPads) {
										if (!stepScanned || stepPendingPads.isEmpty()) {
											needsScan = true
										}
									}
								}

								if (needsScan) {
									listener.onRemoveGuide()
									synchronized(stepPendingPads) { if (stepOrigin < 0) stepOrigin = progress }
									stepStartProgress = progress
									stepScanNext(autoPlay, ::isOwed)
									synchronized(stepPendingPads) {
										stepScanned = stepPendingPads.isNotEmpty() || waitingForChain >= 0
									}
								}
							}

						}
						if (!playmode && (progress < autoPlay.elements.size ||
							stepMode && practiceGuide && hasPendingStep() || hasPendingDelay())) {
							wakeSignal.receive()
						} else {
							delay(loopDelay)
						}
					}
				} catch (_: CancellationException) {
					// Normal cancellation, no action needed
				} finally {
					chain.removeObserver(wakeOnChainChange)
					clearGuides()
				}
				Log.thread("[AutoPlay] 4. End Coroutine")
				listener.onEnd()
			}
		}
	}

	fun stop() {
		Log.thread("[AutoPlay] 3. Request Stop")
		job?.cancel()
		job = null
		suspended = false
		pendingSeek.set(null)
		pendingResume.set(false)
		pendingAutoPlay.set(false)
		clearGuides()
		resetStepState()
	}

	/** Switches to audible playback; the loop owns recovering and rebasing the pending step. */
	fun startAutoPlay() {
		synchronized(progressValue) {
			if (active) pendingAutoPlay.set(true) else resetStepState()
			beforeStartPlaying = true
			practiceGuide = false
			stepMode = false
			playmode = true
		}
		wake()
	}

	/** Publishes a complete timed resume before waking the loop that owns pending steps. */
	fun resume() {
		synchronized(progressValue) {
			if (!active || playmode) return
			if (stepMode) pendingResume.set(true)
			beforeStartPlaying = true
			stepMode = false
			playmode = true
		}
		wake()
	}

	private fun beforeStartPlaying(now: Long) {
		if (beforeStartPlaying) {
			beforeStartPlaying = false
			listener.onRemoveGuide()
			// Rebuild after the screen cleanup, using the same postponed deadlines as the LEDs.
			synchronized(activeGuides) {
				for ((key, target) in activeGuides) {
					if (target > now) listener.onGuidePadOn(key / 256, key % 256, target)
				}
			}
		}
	}

	fun progressOffset(offset: Int) {
		val size = unipack.autoPlayTable?.elements?.size ?: 0
		synchronized(progressValue) {
			val target = (progress + offset).coerceIn(0, size)
			val now = SystemClock.elapsedRealtime()
			pendingSeek.set(Seek(target, now, totalPausedAt(now)))
			setProgress(target)
		}
		wake()
	}

	fun resetStepState() {
		clearStepProgress()
		// The loop owns the song clock and settles any timed wait before clearing it.
		pendingWaitReset.set(true)
		wake()
	}

	private fun clearStepProgress() {
		synchronized(pressedKeysLock) {
			pressedKeysQueue.clear()
		}
		synchronized(stepPendingPads) {
			stepPendingPads.clear()
			stepScanned = false
			stepStartProgress = 0
			stepOrigin = -1
			stepChainValue = -1
		}
	}

	fun stepPadPressed(x: Int, y: Int) {
		val key = guideKey(x, y)
		synchronized(pressedKeysLock) {
			pressedKeysQueue.add(key)
		}
		wake()
	}

	private fun drainPressedKeys() {
		val keys: List<Int>
		synchronized(pressedKeysLock) {
			keys = pressedKeysQueue.toList()
			pressedKeysQueue.clear()
		}

		val removedKeys = mutableListOf<Int>()
		synchronized(stepPendingPads) {
			for (key in keys) {
				if (stepPendingPads.remove(key)) {
					removedKeys.add(key)
				}
			}
		}

		for (key in removedKeys) {
			// A played step must not regain its timed guide when playback resumes.
			synchronized(activeGuides) { activeGuides.remove(key) }
			listener.onGuideLedUpdate(key / 256, key % 256, 0)
			listener.onGuidePadOff(key / 256, key % 256)
		}
	}

	/** Shows the next group of owed pads; [isOwed] passes over notes already played in an earlier step. */
	private fun stepScanNext(autoPlay: AutoPlay, isOwed: (index: Int) -> Boolean) {
		val newPending = mutableSetOf<Int>()
		var totalDelayMs = 0L

		scanLoop@ while (true) {
			val (index, element) = elementAtProgress(autoPlay) ?: break
			when (element) {
				is AutoPlay.Element.On -> {
					val key = guideKey(element.x, element.y)
					if (!isSelectable(element.currChain) || !isOwed(index)) {
						advanceProgressFrom(index)
						continue@scanLoop
					}
					if (chain.value != element.currChain) {
						if (newPending.isEmpty()) {
							waitingForChain = element.currChain
							listener.onGuideChainOn(element.currChain)
						}
						break@scanLoop
					}
					newPending.add(key)
					listener.onGuidePadOn(element.x, element.y, 0)
					listener.onGuideLedUpdate(element.x, element.y, GUIDE_VELOCITIES.last())
					advanceProgressFrom(index)
				}
				is AutoPlay.Element.Off -> advanceProgressFrom(index)
				is AutoPlay.Element.Delay -> {
					totalDelayMs += element.delay.toLong()
					if (newPending.isNotEmpty() && totalDelayMs >= STEP_GROUP_THRESHOLD_MS) {
						break@scanLoop
					}
					advanceProgressFrom(index)
				}
				is AutoPlay.Element.Chain -> advanceProgressFrom(index)
			}
		}

		synchronized(stepPendingPads) {
			stepPendingPads.clear()
			stepPendingPads.addAll(newPending)
		}
	}
}
