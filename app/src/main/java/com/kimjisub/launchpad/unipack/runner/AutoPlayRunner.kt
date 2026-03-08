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

	companion object {
		const val GUIDE_LOOKAHEAD_MS = 800L
	}

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
				}

				try {
					var delayAccum: Long = 0
					var startTime = SystemClock.elapsedRealtime()
					while (progress < autoPlay.elements.size && isActive) {
						val currTime = SystemClock.elapsedRealtime()
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
												listener.onRemoveGuide()
												listener.onGuideChainOn(event.chain)
												break
											}
											listener.onGuidePadOn(event.x, event.y, startTime + event.timeMs)
											guideIndex++
										} else break
									}
								}

								if (waitingForChain < 0 && delayAccum <= currTime - startTime) {
									when (val element: AutoPlay.Element =
										autoPlay.elements[progress]) {
										is AutoPlay.Element.On -> {
											if (practiceGuide) {
												listener.onGuidePadOff(element.x, element.y)
											} else {
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
							// Paused: keep delayAccum in sync so resume is seamless
							beforeStartPlaying = true
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
	}
}
