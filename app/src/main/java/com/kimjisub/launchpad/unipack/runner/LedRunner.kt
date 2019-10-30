package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.unipack.Unipack
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.manager.Log
import kotlin.system.measureTimeMillis

class LedRunner(
	private val unipack: Unipack,
	private val listener: Listener,
	private val chain: ChainObserver,
	private val delay: Long = 16L
) {
	private var btnLED: Array<Array<LED?>?>?
	private var cirLED: Array<LED?>?
	private var LEDEvents: ArrayList<LEDEvent> = ArrayList()

	private var thread: Thread? = null
	val active: Boolean
		get() = !(thread?.isInterrupted ?: true)

	interface Listener {
		fun onStart()
		fun onPadLedTurnOn(x: Int, y: Int, color: Int, velo: Int)
		fun onPadLedTurnOff(x: Int, y: Int)
		fun onChainLedTurnOn(c: Int, color: Int, velo: Int)
		fun onChainLedTurnOff(c: Int)
		fun onEnd()
	}

	init {
		btnLED = Array(unipack.buttonX) { arrayOfNulls<LED?>(unipack.buttonY) }
		cirLED = arrayOfNulls<LED?>(36)
	}

	// Thread /////////////////////////////////////////////////////////////////////////////////////////

	private val runnable = java.lang.Runnable {
		Log.thread("[Led] 2. Start Thread")
		try {
			while (active) {
				val millis = measureTimeMillis {
					loop()
				}
				Thread.sleep((delay - millis).coerceAtLeast(0))
			}
		} catch (e: InterruptedException) {
		}
		Log.thread("[Led] 4. End Thread")
		thread = null
	}

	private fun loop() {
		val currTime = System.currentTimeMillis()
		for (e in LEDEvents) {
			if (e.isPlaying && !e.isShutdown) {
				// Init if First
				if (e.delay == 0L) e.delay = currTime
				while (true) {
					// Counting Up Loop Progress
					if (e.index >= e.ledAnimation?.elements!!.size) {
						e.loopProgress++
						e.index = 0
					}
					// Stop if Loop is Done
					if (e.ledAnimation.loop != 0 && e.ledAnimation.loop <= e.loopProgress) {
						e.isPlaying = false
						break
					}
					if (e.delay <= currTime) {
						try {
							val element = e.ledAnimation.elements[e.index]//todo
							when (element) {
								is LedAnimation.Element.On -> {
									val x = element.x
									val y = element.y
									val color = element.color
									val velo = element.velo

									if (x != -1) {
										listener.onPadLedTurnOn(x, y, color, velo)
										btnLED!![x]!![y] = LED(e.buttonX, e.buttonY, element)
									} else {
										listener.onChainLedTurnOn(y, color, velo)
										cirLED!![y] = LED(e.buttonX, e.buttonY, element)
									}
								}
								is LedAnimation.Element.Off -> {
									val x = element.x
									val y = element.y

									if (x != -1) {
										if (btnLED!![x]!![y] != null && btnLED!![x]!![y]!!.equal(e.buttonX, e.buttonY)) {
											listener.onPadLedTurnOff(x, y)
											btnLED!![x]!![y] = null
										}
									} else {
										if (cirLED!![y] != null && cirLED!![y]!!.equal(e.buttonX, e.buttonY)) {
											listener.onChainLedTurnOff(y)
											cirLED!![y] = null
										}
									}
								}
								is LedAnimation.Element.Delay -> {
									e.delay += element.delay.toLong()
								}
							}
						} catch (ee: ArrayIndexOutOfBoundsException) {
							ee.printStackTrace()
						}
					} else break
					e.index++
				}
			} else if (e.isShutdown) {
				for (x in 0 until unipack.buttonX) {
					for (y in 0 until unipack.buttonY) {
						if (btnLED!![x]!![y] != null && btnLED!![x]!![y]!!.equal(e.buttonX, e.buttonY)) {
							listener.onPadLedTurnOff(x, y)
							btnLED!![x]!![y] = null
						}
					}
				}
				for (y in cirLED!!.indices) {
					if (cirLED!![y] != null && cirLED!![y]!!.equal(e.buttonX, e.buttonY)) {
						listener.onChainLedTurnOff(y)
						cirLED!![y] = null
					}
				}
				e.remove = true
			} else if (!e.isPlaying) {
				e.remove = true
			}
		}
		LEDEvents = LEDEvents.filter { !it.remove } as ArrayList<LEDEvent>
	}


	fun launch() {
		Log.thread("[Led] 1. Request Thread")
		if (thread == null) {
			thread = Thread(runnable)
			thread!!.start()
		}
	}

	fun stop() {
		Log.thread("[Led] 3. Request Stop")
		thread?.interrupt()
	}

	// Functions /////////////////////////////////////////////////////////////////////////////////////////

	fun searchEvent(x: Int, y: Int): LEDEvent? {
		var res: LEDEvent? = null
		try {
			for (e in LEDEvents) {
				if (e.equal(x, y)) {
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
			if (e.noError) LEDEvents.add(e)
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
		var delay: Long = 0
		var isPlaying = true
		var isShutdown = false
		var remove = false
		var loopProgress = 0

		val ledAnimation: LedAnimation?
		val noError
			get() = ledAnimation != null

		fun equal(buttonX: Int, buttonY: Int): Boolean {
			return this.buttonX == buttonX && this.buttonY == buttonY
		}

		init {
			val e: LedAnimation? = unipack.LED_get(chain.value, buttonX, buttonY)
			unipack.LED_push(chain.value, buttonX, buttonY)

			ledAnimation = e
		}
	}
}