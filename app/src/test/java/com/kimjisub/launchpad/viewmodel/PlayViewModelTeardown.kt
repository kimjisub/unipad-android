package com.kimjisub.launchpad.viewmodel

import androidx.lifecycle.ViewModelStore

/**
 * Clears [vm] as its screen would and waits until the autoplay run it stopped has ended on its own
 * thread. That run still posts its last callbacks to Dispatchers.Main, so resetting Main before it ends
 * fails as a concurrent change, or hands the callbacks to the next test. [drain] runs the queued main work.
 */
fun clearPlayViewModel(vm: PlayActivityViewModel, drain: () -> Unit) {
	val runner = vm.autoPlayRunner
	ViewModelStore().apply { put("play", vm); clear() }
	val deadline = System.nanoTime() + 5_000_000_000L
	while (runner?.running == true) {
		drain()
		check(System.nanoTime() < deadline) { "The stopped autoplay run did not end" }
		Thread.sleep(1)
	}
}
