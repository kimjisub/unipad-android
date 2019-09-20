package com.kimjisub.launchpad.unipack.runner

import android.annotation.SuppressLint
import android.os.AsyncTask
import com.kimjisub.launchpad.unipack.Unipack
import com.kimjisub.launchpad.unipack.struct.AutoPlay
import com.kimjisub.manager.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

class AutoPlayRunner(
	private val unipack: Unipack,
	private val delay_: Long = 1L,
	private val listener: Listener
) {
	var chain = 0
	var loop = true
	var isPlaying = false
	var progress = 0
	var beforeStartPlaying = true
	var afterMatchChain = false
	var beforeChain = -1
	var guideItems: ArrayList<AutoPlay.Element.On>? = ArrayList()
	var achieve = 0

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


	fun check() {
		if (achieve >= guideItems!!.size || achieve == -1) {
			achieve = 0
			for (i in guideItems!!.indices) {
				val e: AutoPlay.Element? = guideItems!![i]
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
						unipack.LED_push(e.currChain, e.x, e.y, e.num)
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

	fun launch(){
		CoroutineScope(Dispatchers.IO).launch {
			var delay: Long = 0
			val startTime = java.lang.System.currentTimeMillis()
			while (progress < unipack.autoPlayTable!!.elements.size && loop) {
				val currTime = java.lang.System.currentTimeMillis()
				if (isPlaying) {
					if (beforeStartPlaying) {
						beforeStartPlaying = false
						Log.log("beforeStartPlaying")
						listener.onRemoveGuide()
					}
					if (delay <= currTime - startTime) {
						val e: AutoPlay.Element = unipack.autoPlayTable!!.elements[progress]

						when (e) {
							is AutoPlay.Element.On -> {
								if (chain != e.currChain) listener.onChainChange(e.currChain)
								unipack.Sound_push(e.currChain, e.x, e.y, e.num)
								unipack.LED_push(e.currChain, e.x, e.y, e.num)
								listener.onPadTouchOn(e.x, e.y)
							}
							is AutoPlay.Element.Off -> {
								if (chain != e.currChain) listener.onChainChange(e.currChain)
								listener.onPadTouchOff(e.x, e.y)
							}
							is AutoPlay.Element.Chain -> listener.onChainChange(e.c)
							is AutoPlay.Element.Delay -> delay += e.delay.toLong()
						}
						progress++
						listener.onProgressUpdate(progress)
					}
				} else {
					beforeStartPlaying = true
					if (delay <= currTime - startTime) delay = currTime - startTime
					if (guideItems!!.size > 0 && guideItems!![0].currChain !== chain) { // 현재 체인이 다음 연습 체인이 아닌 경우

						if (beforeChain == -1 || beforeChain != chain) {
							beforeChain = chain
							afterMatchChain = true
							listener.onRemoveGuide()
							listener.onGuideChainOn(guideItems!![0].currChain)
						}
					} else {
						if (afterMatchChain) {
							afterMatchChain = false
							listener.chainButsRefresh()
							for (i in 0 until unipack.chain) listener.onGuideChainOff(i)
							beforeChain = -1
							for (i in guideItems!!.indices) {
								val e: AutoPlay.Element? = guideItems!![i]
								when (e) {
									is AutoPlay.Element.On -> listener.onGuidePadOn(e.x, e.y)
								}
							}
						}
						check()
					}
				}
				try {
					Thread.sleep(delay_)
				} catch (e: InterruptedException) {
					e.printStackTrace()
				}
			}
		}
	}
}