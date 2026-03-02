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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

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
	var afterMatchChain = false
	@Volatile
	var beforeChain = -1
	var guideItems: MutableList<AutoPlay.Element.On> = CopyOnWriteArrayList()
	val achieve = AtomicInteger(0)

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
		fun onGuidePadOn(x: Int, y: Int)
		fun onGuidePadOff(x: Int, y: Int)
		fun onGuideChainOn(c: Int)
		fun onGuideChainOff(c: Int)
		fun onRemoveGuide()
		fun chainButsRefresh()
		fun onProgressUpdate(progress: Int)
		fun onEnd()
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
				try {
					var delayAccum: Long = 0
					val startTime = SystemClock.elapsedRealtime()
					while (progress < autoPlay.elements.size && isActive) {
						val currTime = SystemClock.elapsedRealtime()
						if (playmode) {
							beforeStartPlaying()
							if (delayAccum <= currTime - startTime) {
								when (val element: AutoPlay.Element =
									autoPlay.elements[progress]) {
									is AutoPlay.Element.On -> {
										if (chain.value != element.currChain) listener.onChainChange(element.currChain)
										unipack.soundPush(element.currChain, element.x, element.y, element.num)
										unipack.ledPush(element.currChain, element.x, element.y, element.num)
										listener.onPadTouchOn(element.x, element.y)
									}

									is AutoPlay.Element.Off -> {
										if (chain.value != element.currChain) listener.onChainChange(element.currChain)
										listener.onPadTouchOff(element.x, element.y)
									}
									is AutoPlay.Element.Delay -> {
										delayAccum += element.delay.toLong()
									}
									is AutoPlay.Element.Chain -> {
										listener.onChainChange(element.c)
									}
								}
								progress++
							}
						} else {
							beforeStartPlaying = true
							if (delayAccum <= currTime - startTime) delayAccum = currTime - startTime
							if (guideItems.isNotEmpty() && guideItems[0].currChain != chain.value) {
								if (beforeChain == -1 || beforeChain != chain.value) {
									beforeChain = chain.value
									afterMatchChain = true
									listener.onRemoveGuide()
									listener.onGuideChainOn(guideItems[0].currChain)
								}
							} else {
								afterMatchChain()
								guideCheck()
							}
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

	// Functions

	private fun beforeStartPlaying() {
		if (beforeStartPlaying) {
			beforeStartPlaying = false
			listener.onRemoveGuide()
		}
	}

	private fun afterMatchChain() {
		if (afterMatchChain) {
			afterMatchChain = false
			listener.chainButsRefresh()
			for (i in 0 until unipack.chain) listener.onGuideChainOff(i)
			beforeChain = -1
			for (i in guideItems.indices) {
				val element: AutoPlay.Element = guideItems[i]
				when (element) {
					is AutoPlay.Element.On -> listener.onGuidePadOn(element.x, element.y)
					is AutoPlay.Element.Off,
					is AutoPlay.Element.Chain,
					is AutoPlay.Element.Delay -> {}
				}
			}
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


	fun guideCheck() {
		if (achieve.get() >= guideItems.size || achieve.get() == -1) {
			achieve.set(0)
			for (i in guideItems.indices) {
				val element: AutoPlay.Element = guideItems[i]
				when (element) {
					is AutoPlay.Element.On -> listener.onGuidePadOff(element.x, element.y)
					is AutoPlay.Element.Off,
					is AutoPlay.Element.Chain,
					is AutoPlay.Element.Delay -> {}
				}
			}
			guideItems.clear()
			val autoPlay = unipack.autoPlayTable ?: return
			var addedDelay = 0
			var complete = false
			while (progress < autoPlay.elements.size && addedDelay <= 20) {
				val element: AutoPlay.Element = autoPlay.elements[progress]
				when (element) {
					is AutoPlay.Element.On -> {
						unipack.soundPush(element.currChain, element.x, element.y, element.num)
						unipack.ledPush(element.currChain, element.x, element.y, element.num)
						listener.onGuidePadOn(element.x, element.y)
						complete = true
						guideItems.add(element)
					}

					is AutoPlay.Element.Delay -> if (complete) addedDelay += element.delay
					is AutoPlay.Element.Off,
					is AutoPlay.Element.Chain -> {}
				}
				progress++
			}
		}
	}
}
