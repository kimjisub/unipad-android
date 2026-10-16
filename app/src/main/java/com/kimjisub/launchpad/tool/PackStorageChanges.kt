package com.kimjisub.launchpad.tool

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Announces that files in a pack workspace changed after a screen counted them.
 * Nothing is replayed: a screen that was not listening re-reads the workspace when it resumes.
 */
object PackStorageChanges {
	private val events = MutableSharedFlow<Unit>(
		extraBufferCapacity = 1,
		onBufferOverflow = BufferOverflow.DROP_OLDEST,
	)
	val changes = events.asSharedFlow()

	fun notifyChanged() {
		events.tryEmit(Unit)
	}
}
