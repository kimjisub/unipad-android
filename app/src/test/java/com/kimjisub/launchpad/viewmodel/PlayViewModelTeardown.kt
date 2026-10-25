package com.kimjisub.launchpad.viewmodel

import androidx.lifecycle.ViewModelStore

/** Clears [vm] as its screen would; see [stopPlayViewModel]. */
fun clearPlayViewModel(vm: PlayActivityViewModel?, drain: () -> Unit, restore: () -> Unit) =
	stopPlayViewModel(vm, drain, restore) { ViewModelStore().apply { put("play", it); clear() } }

/**
 * Runs [stop] on [vm], waits until every autoplay run it started has ended on its own thread, then runs
 * [restore]. Those runs still post their last callbacks to Dispatchers.Main, so resetting Main before they
 * end fails as a concurrent change, or hands the callbacks to the next test. [drain] runs the queued main
 * work. [restore] runs even when stopping fails, so Main and mocks never leak into later test classes.
 */
fun stopPlayViewModel(
	vm: PlayActivityViewModel?,
	drain: () -> Unit,
	restore: () -> Unit,
	stop: (PlayActivityViewModel) -> Unit,
) {
	val failure = runCatching {
		if (vm == null) return@runCatching
		stop(vm)
		val deadline = System.nanoTime() + 5_000_000_000L
		while (vm.autoPlayRunning) {
			check(System.nanoTime() < deadline) { "A stopped autoplay run did not end within 5 seconds" }
			drain()
			Thread.sleep(1)
		}
	}.exceptionOrNull()
	runCatching(restore).exceptionOrNull()?.let { failure?.addSuppressed(it) ?: throw it }
	failure?.let { throw it }
}
