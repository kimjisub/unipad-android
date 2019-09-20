package com.kimjisub.launchpad.unipack.runner

import android.annotation.SuppressLint
import android.os.AsyncTask
import com.kimjisub.launchpad.unipack.Unipack
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.manager.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class LedRunner(
	private val unipack: Unipack,
	private val delay: Long = 1L,
	private val listener: Listener
) {
	var chain = 0
	var isPlaying = true
	var btnLED: Array<Array<LED?>?>?
	var cirLED: Array<LED?>?
	var LEDEvents: ArrayList<LEDEvent?>?

	interface Listener {
		fun onStart()
		fun onLedTurnOn(x: Int, y: Int, color: Int, velo: Int)
		fun onLedTurnOff(x: Int, y: Int)
		fun onEnd()
	}


	fun searchEvent(x: Int, y: Int): LEDEvent? {
		var res: LEDEvent? = null
		try {
			for (i in LEDEvents!!.indices) {
				val e = LEDEvents!![i]
				if (e!!.equal(x, y)) {
					res = e
					break
				}
			}
		} catch (ee: IndexOutOfBoundsException) {
			ee.printStackTrace()
		}
		return res
	}

	fun isEventExist(x: Int, y: Int): Boolean {
		return searchEvent(x, y) != null
	}

	fun addEvent(x: Int, y: Int) {
		if (isEventExist(x, y)) {
			val e = searchEvent(x, y)
			e!!.isShutdown = true
		}
		val e = LEDEvent(x, y)
		if (e.noError) LEDEvents!!.add(e)
	}

	fun eventShutdown(x: Int, y: Int) {
		searchEvent(x, y)!!.isShutdown = true
	}

	inner class LED(
		var buttonX: Int,
		var buttonY: Int,
		var x: Int,
		var y: Int,
		var color: Int,
		var velo: Int
	) {
		fun equal(buttonX: Int, buttonY: Int): Boolean {
			return this.buttonX == buttonX && this.buttonY == buttonY
		}

	}

	inner class LEDEvent(var buttonX: Int, var buttonY: Int) {
		var noError = false
		var elements: ArrayList<LedAnimation.Element>? = null
		var index = 0
		var delay: Long = -1
		var isPlaying = true
		var isShutdown = false
		var loop = 0
		var loopProgress = 0
		fun equal(buttonX: Int, buttonY: Int): Boolean {
			return this.buttonX == buttonX && this.buttonY == buttonY
		}

		init {
			val e: LedAnimation? = unipack.LED_get(chain, buttonX, buttonY)
			unipack.LED_push(chain, buttonX, buttonY)
			if (e != null) {
				elements = e.elements
				loop = e.loop
				noError = true
			}
		}
	}

	init {
		btnLED = Array(unipack.buttonX) { arrayOfNulls<LED?>(unipack.buttonY) }
		cirLED = arrayOfNulls<LED?>(36)
		LEDEvents = ArrayList()
	}

	fun launch() {
		CoroutineScope(Dispatchers.IO).launch {
			while (isPlaying) {
				val currTime = System.currentTimeMillis()
				for (i in LEDEvents!!.indices) {
					val e = LEDEvents!![i]
					if (e != null && e.isPlaying && !e.isShutdown) {
						if (e.delay == -1L) e.delay = currTime
						while (true) {
							if (e.index >= e.elements!!.size) {
								e.loopProgress++
								e.index = 0
							}
							if (e.loop != 0 && e.loop <= e.loopProgress) {
								e.isPlaying = false
								break
							}
							if (e.delay <= currTime) {
								val syntax = e.elements!![e.index]
								try {
									when (syntax) {
										is LedAnimation.Element.On -> {
											val x = syntax.x
											val y = syntax.y
											val color = syntax.color
											val velo = syntax.velo

											if (x != -1) {
												listener.onLedTurnOn(x, y, color, velo)
												btnLED!![x]!![y] = LED(e.buttonX, e.buttonY, x, y, color, velo)
											} else {
												listener.onLedTurnOn(x, y, color, velo)
												cirLED!![y] = LED(e.buttonX, e.buttonY, x, y, color, velo)
											}
										}
										is LedAnimation.Element.Off -> {
											val x = syntax.x
											val y = syntax.y

											if (x != -1) {
												if (btnLED!![x]!![y] != null && btnLED!![x]!![y]!!.equal(e.buttonX, e.buttonY)) {
													listener.onLedTurnOff(x, y)
													btnLED!![x]!![y] = null
												}
											} else {
												if (cirLED!![y] != null && cirLED!![y]!!.equal(e.buttonX, e.buttonY)) {
													listener.onLedTurnOff(x, y)
													cirLED!![y] = null
												}
											}
										}
										is LedAnimation.Element.Delay -> {
											val delay = syntax.delay

											e.delay += delay.toLong()
										}
									}
								} catch (ee: ArrayIndexOutOfBoundsException) {
									ee.printStackTrace()
								}
							} else break
							e.index++
						}
					} else if (e == null) {
						LEDEvents!!.removeAt(i)
						Log.log("led 오류 e == null")
					} else if (e.isShutdown) {
						for (x in 0 until unipack.buttonX) {
							for (y in 0 until unipack.buttonY) {
								if (btnLED!![x]!![y] != null && btnLED!![x]!![y]!!.equal(e.buttonX, e.buttonY)) {
									listener.onLedTurnOff(x, y)
									btnLED!![x]!![y] = null
								}
							}
						}
						for (y in cirLED!!.indices) {
							if (cirLED!![y] != null && cirLED!![y]!!.equal(e.buttonX, e.buttonY)) {
								listener.onLedTurnOff(-1, y)
								cirLED!![y] = null
							}
						}
						LEDEvents!!.removeAt(i)
					} else if (!e.isPlaying) {
						LEDEvents!!.removeAt(i)
					}
				}
				delay(delay)
			}
		}
	}
}