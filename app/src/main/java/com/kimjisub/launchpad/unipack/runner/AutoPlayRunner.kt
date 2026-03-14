package com.kimjisub.launchpad.unipack.runner

import android.os.SystemClock
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutoPlayRunner(
	private val unipack: UniPack,
	private val listener: Listener,
	private val chain: ChainObserver,
	private val loopDelay: Long = 1L,
) {
	@Volatile
	var playmode = true
	@Volatile
	var beforeStartPlaying = true

	@Volatile
	var practiceGuide = false

	@Volatile
	var stepMode = false

	@Volatile
	var progress = 0
		set(value) {
			field = value
			listener.onProgressUpdate(progress)
		}

	private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
	private var job: Job? = null
	val active: Boolean
		get() = job?.isActive == true

	interface Listener {
		fun onStart()
		fun onPadTouchOn(x: Int, y: Int)
		fun onPadTouchOff(x: Int, y: Int)
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
	private data class GuideEvent(val timeMs: Long, val x: Int, val y: Int, val chain: Int)
	private var guideTimeline: List<GuideEvent> = emptyList()
	private var guideIndex = 0
	private var waitingForChain = -1
	private var waitStartTime = 0L

	// Active guide tracking for LED brightness + auto-expiration
	private val activeGuides = mutableMapOf<Int, Long>() // key = x*256+y, value = targetWallTimeMs
	private var lastGuideUpdateMs = 0L

	// Step mode state
	private val stepPendingPads = mutableSetOf<Int>()
	private val pressedKeysQueue = mutableListOf<Int>()
	private val pressedKeysLock = Any()
	private var stepScanned = false
	private var stepStartProgress = 0
	private var stepChainValue = -1

	companion object {
		const val GUIDE_LOOKAHEAD_MS = 800L
		private const val GUIDE_LED_UPDATE_INTERVAL_MS = 50L
		private val GUIDE_VELOCITIES = intArrayOf(1, 2, 3, 21) // dim white → bright white → green
		private const val STEP_GROUP_THRESHOLD_MS = 50L
	}

	private fun guideKey(x: Int, y: Int) = x * 256 + y

	private fun buildGuideTimeline(autoPlay: AutoPlay): List<GuideEvent> {
		val events = mutableListOf<GuideEvent>()
		var time = 0L
		for (element in autoPlay.elements) {
			when (element) {
				is AutoPlay.Element.Delay -> time += element.delay
				is AutoPlay.Element.On -> events.add(GuideEvent(time, element.x, element.y, element.currChain))
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
				progress = 0
				listener.onStart()

				val autoPlay = unipack.autoPlayTable ?: return@launch

				if (practiceGuide) {
					guideTimeline = buildGuideTimeline(autoPlay)
					guideIndex = 0
					waitingForChain = -1
					activeGuides.clear()
				}

				try {
					var delayAccum: Long = 0
					var startTime = SystemClock.elapsedRealtime()
					var prevPracticeGuide = practiceGuide
					while (progress < autoPlay.elements.size && isActive) {
						val currTime = SystemClock.elapsedRealtime()

						// Detect mid-run practice mode toggle
						if (practiceGuide != prevPracticeGuide) {
							if (practiceGuide) {
								// Switched TO practice mode — initialize guide state
								guideTimeline = buildGuideTimeline(autoPlay)
								val elapsed = currTime - startTime
								guideIndex = guideTimeline.indexOfFirst { it.timeMs > elapsed - GUIDE_LOOKAHEAD_MS }
									.let { if (it < 0) guideTimeline.size else it }
								waitingForChain = -1
								activeGuides.clear()
							} else {
								// Switched FROM practice mode — clean up guides
								for ((key, _) in activeGuides) {
									listener.onGuideLedUpdate(key / 256, key % 256, 0)
								}
								activeGuides.clear()
								guideTimeline = emptyList()
								waitingForChain = -1
								listener.onRemoveGuide()
							}
							prevPracticeGuide = practiceGuide
						}

						if (playmode) {
							// Practice mode: waiting for chain change
							if (practiceGuide && waitingForChain >= 0) {
								if (chain.value == waitingForChain) {
									// User switched to correct chain — resume
									startTime += currTime - waitStartTime
									waitingForChain = -1
									listener.onRemoveGuide()
								} else {
									// Keep delayAccum in sync while waiting
									if (delayAccum <= currTime - startTime) delayAccum = currTime - startTime
								}
							} else {
								beforeStartPlaying()

								// Guide lookahead
								if (practiceGuide) {
									val elapsed = currTime - startTime
									while (guideIndex < guideTimeline.size) {
										val event = guideTimeline[guideIndex]
										if (event.timeMs <= elapsed + GUIDE_LOOKAHEAD_MS) {
											if (event.chain != chain.value) {
												// Chain mismatch — pause and show chain indicator
												waitingForChain = event.chain
												waitStartTime = currTime
												for ((key, _) in activeGuides) {
													listener.onGuideLedUpdate(key / 256, key % 256, 0)
												}
												activeGuides.clear()
												listener.onRemoveGuide()
												listener.onGuideChainOn(event.chain)
												break
											}
											val targetWallTimeMs = startTime + event.timeMs
											activeGuides[guideKey(event.x, event.y)] = targetWallTimeMs
											listener.onGuidePadOn(event.x, event.y, targetWallTimeMs)
											guideIndex++
										} else break
									}

									// Guide expiration + launchpad LED brightness update
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

								while (waitingForChain < 0 && delayAccum <= currTime - startTime
									&& progress < autoPlay.elements.size) {
									when (val element: AutoPlay.Element =
										autoPlay.elements[progress]) {
										is AutoPlay.Element.On -> {
											if (!practiceGuide) {
												if (chain.value != element.currChain) listener.onChainChange(element.currChain)
												unipack.soundPush(element.currChain, element.x, element.y, element.num)
												unipack.ledPush(element.currChain, element.x, element.y, element.num)
												listener.onPadTouchOn(element.x, element.y)
											}
										}

										is AutoPlay.Element.Off -> {
											if (!practiceGuide) {
												if (chain.value != element.currChain) listener.onChainChange(element.currChain)
												listener.onPadTouchOff(element.x, element.y)
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
									progress++
								}
							}
						} else {
							beforeStartPlaying = true

							if (stepMode && practiceGuide) {
								drainPressedKeys()

								val currentChain = chain.value

								if (currentChain != stepChainValue && stepChainValue >= 0) {
									synchronized(stepPendingPads) {
										if (stepScanned) {
											progress = stepStartProgress
											stepPendingPads.clear()
											stepScanned = false
										}
									}
									waitingForChain = -1
								}
								stepChainValue = currentChain

								var needsScan = false

								if (waitingForChain >= 0) {
									if (currentChain == waitingForChain) {
										waitingForChain = -1
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
									stepStartProgress = progress
									stepScanNext(autoPlay)
									synchronized(stepPendingPads) {
										stepScanned = stepPendingPads.isNotEmpty() || waitingForChain >= 0
									}
								}
							}

							if (delayAccum <= currTime - startTime) delayAccum = currTime - startTime
						}
						delay(loopDelay)
					}
				} catch (_: CancellationException) {
					// Normal cancellation, no action needed
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
		activeGuides.clear()
		resetStepState()
	}

	private fun beforeStartPlaying() {
		if (beforeStartPlaying) {
			beforeStartPlaying = false
			listener.onRemoveGuide()
		}
	}

	fun progressOffset(offset: Int) {
		val range = 0 until Int.MAX_VALUE
		val targetProgress = progress + offset

		progress =
			when {
				range.first > targetProgress -> range.first
				range.last < targetProgress -> range.last
				else -> targetProgress
			}
		if (stepMode) {
			resetStepState()
			listener.onRemoveGuide()
		}
	}

	fun resetStepState() {
		synchronized(pressedKeysLock) {
			pressedKeysQueue.clear()
		}
		synchronized(stepPendingPads) {
			stepPendingPads.clear()
			stepScanned = false
			stepStartProgress = 0
			stepChainValue = -1
		}
	}

	fun stepPadPressed(x: Int, y: Int) {
		val key = guideKey(x, y)
		synchronized(pressedKeysLock) {
			pressedKeysQueue.add(key)
		}
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
			listener.onGuideLedUpdate(key / 256, key % 256, 0)
			listener.onGuidePadOff(key / 256, key % 256)
		}
	}

	private fun stepScanNext(autoPlay: AutoPlay) {
		val newPending = mutableSetOf<Int>()
		var totalDelayMs = 0L

		scanLoop@ while (progress < autoPlay.elements.size) {
			when (val element = autoPlay.elements[progress]) {
				is AutoPlay.Element.On -> {
					if (chain.value != element.currChain) {
						if (newPending.isEmpty()) {
							waitingForChain = element.currChain
							listener.onGuideChainOn(element.currChain)
						}
						break@scanLoop
					}
					val key = guideKey(element.x, element.y)
					newPending.add(key)
					listener.onGuidePadOn(element.x, element.y, 0)
					listener.onGuideLedUpdate(element.x, element.y, GUIDE_VELOCITIES.last())
					progress++
				}
				is AutoPlay.Element.Off -> progress++
				is AutoPlay.Element.Delay -> {
					totalDelayMs += element.delay.toLong()
					if (newPending.isNotEmpty() && totalDelayMs >= STEP_GROUP_THRESHOLD_MS) {
						break@scanLoop
					}
					progress++
				}
				is AutoPlay.Element.Chain -> progress++
			}
		}

		synchronized(stepPendingPads) {
			stepPendingPads.clear()
			stepPendingPads.addAll(newPending)
		}
	}
}
