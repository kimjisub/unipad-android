package com.kimjisub.launchpad.tool

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Coalesces filesystem changes while a visible library refresh is busy. */
object PackStorageChanges {
	private val events = MutableSharedFlow<Unit>(
		replay = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)
	val changes = events.asSharedFlow()

	fun notifyChanged() {
		events.tryEmit(Unit)
	}
}
