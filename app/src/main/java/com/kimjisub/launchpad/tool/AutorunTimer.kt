package com.kimjisub.launchpad.tool

import java.util.Timer
import java.util.TimerTask

class AutorunTimer(val listener: OnListener, val timeout: Long) {
	private val everySecTimer = Timer()
	private val timeoutTimer = Timer()
	private var elapsedTime: Long = 0
	var running = false

	interface OnListener {
		fun onEverySec(leftTime: Long, elapsedTime: Long)
		fun onTimeOut()
		fun onCanceled()
	}

	fun start() {
		running = true
		everySecTimer.schedule(object : TimerTask() {
			override fun run() {
				listener.onEverySec(timeout - elapsedTime, elapsedTime)
				elapsedTime += 1000
			}

		}, 0, 1000)

		timeoutTimer.schedule(object : TimerTask() {
			override fun run() {
				timeout()
			}
		}, timeout)
	}

	fun cancel() {
		if (running) {
			running = false
			timeoutTimer.cancel()
			everySecTimer.cancel()
			listener.onCanceled()
		}
	}

	fun timeout() {
		if (running) {
			running = false
			timeoutTimer.cancel()
			everySecTimer.cancel()
			listener.onTimeOut()
		}
	}
}