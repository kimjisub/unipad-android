package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.AutoPlay

class AutoPlayRunner(
	private val unipack: UniPack,
	private val listener: Listener,
	private val chain: ChainObserver,
	private val loopDelay: Long = 1L,
) {
	var playmode = true
	var beforeStartPlaying = true
	var afterMatchChain = false
	var beforeChain = -1
	var guideItems: ArrayList<AutoPlay.Element.On>? = ArrayList()
	var achieve = 0

	var progress = 0
		set(value) {
			field = value
			listener.onProgressUpdate(progress)
		}

	private var thread: Thread? = null
	val active: Boolean
		get() = !(thread?.isInterrupted ?: true)

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

	// Thread

	private val runnable = java.lang.Runnable {
		Log.thread("[AutoPlay] 2. Start Thread")
		progress = 0
		listener.onStart()

		try {
			var delay: Long = 0
			val startTime = System.currentTimeMillis()
			while (progress < unipack.autoPlayTable!!.elements.size && active) {
				val currTime = System.currentTimeMillis()
				if (playmode) {
					beforeStartPlaying()
					if (delay <= currTime - startTime) {
						Log.play("[AutoPlay] progress $progress")
						when (val e: AutoPlay.Element =
							unipack.autoPlayTable!!.elements[progress]) {
							is AutoPlay.Element.On -> {
								Log.play("[AutoPlay] on ${e.x} ${e.y}")
								if (chain.value != e.currChain) listener.onChainChange(e.currChain)
								unipack.Sound_push(e.currChain, e.x, e.y, e.num)
								unipack.led_push(e.currChain, e.x, e.y, e.num)
								listener.onPadTouchOn(e.x, e.y)
							}

							is AutoPlay.Element.Off -> {
								Log.play("[AutoPlay] off ${e.x} ${e.y}")
								if (chain.value != e.currChain) listener.onChainChange(e.currChain)
								listener.onPadTouchOff(e.x, e.y)
							}
							//is AutoPlay.Element.Chain -> listener.onChainChange(e.c)
							is AutoPlay.Element.Delay -> {
								Log.play("[AutoPlay] delay ${e.delay}")
								delay += e.delay.toLong()
							}
						}
						progress++
					}
				} else {
					beforeStartPlaying = true
					if (delay <= currTime - startTime) delay = currTime - startTime
					if (guideItems!!.size > 0 && guideItems!![0].currChain !== chain.value) { // 현재 체인이 다음 연습 체인이 아닌 경우

						if (beforeChain == -1 || beforeChain != chain.value) {
							beforeChain = chain.value
							afterMatchChain = true
							listener.onRemoveGuide()
							listener.onGuideChainOn(guideItems!![0].currChain)
						}
					} else {
						afterMatchChain()
						guideCheck()
					}
				}
				Thread.sleep(loopDelay)
			}
		} catch (e: InterruptedException) {
		}
		Log.thread("[AutoPlay] 4. End Thread")
		thread = null
		listener.onEnd()
	}

	fun launch() {
		Log.thread("[AutoPlay] 1. Request Thread")
		if (thread == null) {
			thread = Thread(runnable)
			thread!!.start()
		}
	}

	fun stop() {
		Log.thread("[AutoPlay] 3. Request Stop")
		thread?.interrupt()
	}

	// Functions

	private fun beforeStartPlaying() {
		if (beforeStartPlaying) {
			beforeStartPlaying = false
			Log.log("beforeStartPlaying")
			listener.onRemoveGuide()
		}
	}

	private fun afterMatchChain() {
		if (afterMatchChain) {
			afterMatchChain = false
			listener.chainButsRefresh()
			for (i in 0 until unipack.chain) listener.onGuideChainOff(i)
			beforeChain = -1
			for (i in guideItems!!.indices) {
				val e: AutoPlay.Element = guideItems!![i]
				when (e) {
					is AutoPlay.Element.On -> listener.onGuidePadOn(e.x, e.y)
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
		if (achieve >= guideItems!!.size || achieve == -1) {
			achieve = 0
			for (i in guideItems!!.indices) {
				val e: AutoPlay.Element = guideItems!![i]
				when (e) {
					is AutoPlay.Element.On -> listener.onGuidePadOff(e.x, e.y)
				}
			}
			guideItems!!.clear()
			var addedDelay = 0
			var complete = false
			while (progress < unipack.autoPlayTable!!.elements.size && (addedDelay <= 20 || !complete)) {
				val e: AutoPlay.Element = unipack.autoPlayTable!!.elements[progress]
				when (e) {
					is AutoPlay.Element.On -> {
						unipack.Sound_push(e.currChain, e.x, e.y, e.num)
						unipack.led_push(e.currChain, e.x, e.y, e.num)
						listener.onGuidePadOn(e.x, e.y)
						complete = true
						guideItems!!.add(e)
						Log.log(e.currChain.toString() + " " + e.x.toString() + " " + e.y)
					}

					is AutoPlay.Element.Delay -> if (complete) addedDelay += e.delay
				}
				progress++
			}
		}
	}
}