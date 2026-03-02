package com.kimjisub.launchpad.tool

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AutorunTimer(val listener: OnListener, val timeout: Long) {
	companion object {
		private const val TIMER_INTERVAL_MS = 1000L
	}

	private var scope: CoroutineScope? = null
	private var elapsedTime: Long = 0
	var running = false

	interface OnListener {
		fun onEverySec(leftTime: Long, elapsedTime: Long)
		fun onTimeOut()
		fun onCanceled()
	}

	fun start() {
		running = true
		val newScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
		scope = newScope

		newScope.launch {
			while (isActive) {
				listener.onEverySec(timeout - elapsedTime, elapsedTime)
				elapsedTime += TIMER_INTERVAL_MS
				delay(TIMER_INTERVAL_MS)
			}
		}

		newScope.launch {
			delay(timeout)
			timeout()
		}
	}

	fun cancel() {
		if (running) {
			running = false
			scope?.cancel()
			scope = null
			listener.onCanceled()
		}
	}

	fun timeout() {
		if (running) {
			running = false
			scope?.cancel()
			scope = null
			listener.onTimeOut()
		}
	}
}
