package com.kimjisub.launchpad.unipack.runner

import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.struct.LedAnimation
import com.kimjisub.manager.Log
import kotlin.system.measureTimeMillis

class LedRunner(
	private val unipack: UniPack,
	private val listener: Listener,
	private val chain: ChainObserver,
	private val delay: Long = 16L
) {
	private var btnLed: Array<Array<Led?>?>?
	private var cirLed: Array<Led?>?
	private var ledAnimationStates: ArrayList<LedAnimationState> = ArrayList()
	private var ledAnimationStatesAdd: ArrayList<LedAnimationState> = ArrayList()

	private var thread: Thread? = null
	val active: Boolean
		get() = !(thread?.isInterrupted ?: true)

	interface Listener {
		fun onStart()
		fun onPadLedTurnOn(x: Int, y: Int, color: Int, velocity: Int)
		fun onPadLedTurnOff(x: Int, y: Int)
		fun onChainLedTurnOn(c: Int, color: Int, velocity: Int)
		fun onChainLedTurnOff(c: Int)
		fun onEnd()
	}

	init {
		btnLed = Array(unipack.buttonX) { arrayOfNulls<Led?>(unipack.buttonY) }
		cirLed = arrayOfNulls<Led?>(36)
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
		synchronized(this) {
			val currTime = System.currentTimeMillis()
			for (e in ledAnimationStates) {
				if (e.isPlaying && !e.isShutdown) {
					// Init if First
					if (e.delay == 0L) e.delay = currTime
					while (true) {
						// Counting Up Loop Progress
						if (e.index >= e.ledAnimation?.ledEvents!!.size) {
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
								when (val event = e.ledAnimation.ledEvents[e.index]) {
									is LedAnimation.LedEvent.On -> {
										val x = event.x
										val y = event.y
										val color = event.color
										val velocity = event.velocity

										if (x != -1) {
											listener.onPadLedTurnOn(x, y, color, velocity)
											btnLed!![x]!![y] = Led(e.buttonX, e.buttonY, event)
										} else {
											listener.onChainLedTurnOn(y, color, velocity)
											cirLed!![y] = Led(e.buttonX, e.buttonY, event)
										}
									}
									is LedAnimation.LedEvent.Off -> {
										val x = event.x
										val y = event.y

										if (x != -1) {
											if (btnLed!![x]!![y] != null && btnLed!![x]!![y]!!.equal(
													e.buttonX,
													e.buttonY
												)
											) {
												listener.onPadLedTurnOff(x, y)
												btnLed!![x]!![y] = null
											}
										} else {
											if (cirLed!![y] != null && cirLed!![y]!!.equal(
													e.buttonX,
													e.buttonY
												)
											) {
												listener.onChainLedTurnOff(y)
												cirLed!![y] = null
											}
										}
									}
									is LedAnimation.LedEvent.Delay -> {
										e.delay += event.delay.toLong()
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
							if (btnLed!![x]!![y] != null && btnLed!![x]!![y]!!.equal(
									e.buttonX,
									e.buttonY
								)
							) {
								listener.onPadLedTurnOff(x, y)
								btnLed!![x]!![y] = null
							}
						}
					}
					for (y in cirLed!!.indices) {
						if (cirLed!![y] != null && cirLed!![y]!!.equal(e.buttonX, e.buttonY)) {
							listener.onChainLedTurnOff(y)
							cirLed!![y] = null
						}
					}
					e.remove = true
				} else if (!e.isPlaying) {
					e.remove = true
				}
			}
			for(item in ledAnimationStatesAdd)
				ledAnimationStates.add(item)
			ledAnimationStatesAdd.clear()
			ledAnimationStates =
				ledAnimationStates.filter { !it.remove } as ArrayList<LedAnimationState>
		}
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

	private fun searchEvent(x: Int, y: Int): LedAnimationState? {
		var res: LedAnimationState? = null
		try {
			for (e in ledAnimationStates) {
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
			val e = LedAnimationState(x, y)
			if (e.noError) ledAnimationStatesAdd.add(e)
		}
	}

	fun eventOff(x: Int, y: Int) {
		if (active) {
			val e = searchEvent(x, y)
			if (e != null && e.ledAnimation?.loop == 0)
				searchEvent(x, y)!!.isShutdown = true
		}
	}

	// 점등되는 하나의 Led 를 의미합니다.
	inner class Led(
		var buttonX: Int,
		var buttonY: Int,
		var ledEvent: LedAnimation.LedEvent
	) {
		fun equal(buttonX: Int, buttonY: Int): Boolean {
			return this.buttonX == buttonX && this.buttonY == buttonY
		}
	}

	// Led 이벤트들의 모음인 LedAnimation 의 실행 상황을 기록합니다.
	inner class LedAnimationState(var buttonX: Int, var buttonY: Int) {
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
			val e: LedAnimation? = unipack.led_get(chain.value, buttonX, buttonY)
			unipack.led_push(chain.value, buttonX, buttonY)

			ledAnimation = e
		}
	}
}