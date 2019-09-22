package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.unipack.Unipack
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.manager.Log
import kotlinx.coroutines.*
import java.util.*
import kotlin.system.measureTimeMillis

class LedRunner(
	private val unipack: Unipack,
	private val delay: Long = 16L,
	private val listener: Listener
) {
	val coroutineContext = newSingleThreadContext("LedContext")

	var chain = 0
	private var btnLED: Array<Array<LED?>?>?
	private var cirLED: Array<LED?>?
	private var LEDEvents: ArrayList<LEDEvent?>?

	private var job: Job? = null
	val active: Boolean
		get() = job?.isActive ?: false

	interface Listener {
		fun onStart()
		fun onLedTurnOn(x: Int, y: Int, color: Int, velo: Int)
		fun onLedTurnOff(x: Int, y: Int)
		fun onEnd()
	}

	init {
		btnLED = Array(unipack.buttonX) { arrayOfNulls<LED?>(unipack.buttonY) }
		cirLED = arrayOfNulls<LED?>(36)
		LEDEvents = ArrayList()
	}

	// Event /////////////////////////////////////////////////////////////////////////////////////////

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

	fun isEventExist(x: Int, y: Int): Boolean = searchEvent(x, y) != null

	fun eventOn(x: Int, y: Int) {
		if (active) {
			if (isEventExist(x, y)) {
				val e = searchEvent(x, y)
				e!!.isShutdown = true
			}
			val e = LEDEvent(x, y)
			if (e.noError) LEDEvents!!.add(e)
		}
	}

	fun eventOff(x: Int, y: Int) {
		if (active) {
			val e = searchEvent(x, y)
			if (e != null && e.ledAnimation?.loop == 0)
				searchEvent(x, y)!!.isShutdown = true
		}
	}

	inner class LED(
		var buttonX: Int,
		var buttonY: Int,
		var element: LedAnimation.Element
	) {


		fun equal(buttonX: Int, buttonY: Int): Boolean {
			return this.buttonX == buttonX && this.buttonY == buttonY
		}
	}

	inner class LEDEvent(var buttonX: Int, var buttonY: Int) {
		var index = 0
		var delay: Long = -1
		var isPlaying = true
		var isShutdown = false
		var loopProgress = 0

		val ledAnimation: LedAnimation?
		val noError
			get() = ledAnimation != null

		fun equal(buttonX: Int, buttonY: Int): Boolean {
			return this.buttonX == buttonX && this.buttonY == buttonY
		}

		init {
			val e: LedAnimation? = unipack.LED_get(chain, buttonX, buttonY)
			unipack.LED_push(chain, buttonX, buttonY)

			ledAnimation = e
		}
	}

	// unishare /////////////////////////////////////////////////////////////////////////////////////////

	fun launch() {
		job = CoroutineScope(Dispatchers.IO).launch {
			withContext(coroutineContext) {
				while (isActive) {
					val millis = measureTimeMillis {
						loop()
					}

					delay(delay - millis)
				}
			}
		}
	}

	fun stop() {
		job?.cancel()
		job = null
	}


	private suspend fun loop() {
		val currTime = System.currentTimeMillis()
		for (i in LEDEvents!!.indices) {
			val e = LEDEvents!![i]
			if (e != null && e.isPlaying && !e.isShutdown) {
				if (e.delay == -1L) e.delay = currTime
				while (true) {
					if (e.index >= e.ledAnimation?.elements!!.size) {
						e.loopProgress++
						e.index = 0
					}
					if (e.ledAnimation.loop != 0 && e.ledAnimation?.loop <= e.loopProgress) {
						e.isPlaying = false
						break
					}
					if (e.delay <= currTime) {
						val element = e.ledAnimation?.elements!![e.index]
						try {
							when (element) {
								is LedAnimation.Element.On -> {
									val x = element.x
									val y = element.y
									val color = element.color
									val velo = element.velo

									if (x != -1) {
										listener.onLedTurnOn(x, y, color, velo)
										btnLED!![x]!![y] = LED(e.buttonX, e.buttonY, element)
									} else {
										listener.onLedTurnOn(x, y, color, velo)
										cirLED!![y] = LED(e.buttonX, e.buttonY, element)
									}
								}
								is LedAnimation.Element.Off -> {
									val x = element.x
									val y = element.y

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
									val delay = element.delay

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
	}
}