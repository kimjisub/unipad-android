package com.kimjisub.launchpad.manager

/** What the store says about a newer build for this install. */
sealed interface UpdateStatus {
	/** Nothing newer is offered to this install right now. */
	data object None : UpdateStatus

	/** The store cannot update this install from inside the app (not from Play, no Play, ...). */
	data object Unsupported : UpdateStatus

	data object Available : UpdateStatus

	/** [fraction] is null until the store reports a size. */
	data class Downloading(val fraction: Float?) : UpdateStatus

	data object Downloaded : UpdateStatus

	data object Failed : UpdateStatus

	data object Canceled : UpdateStatus
}

/** The store side of an in-app update; it never installs on its own. */
interface AppUpdateSource {
	/** Throws when the store could not be asked. */
	suspend fun status(): UpdateStatus

	/** Opens the store's consent screen for the last [status] that was [UpdateStatus.Available]. */
	fun startDownload(): Boolean

	/** Installs a finished download; the store restarts the app. Throws when it cannot start. */
	suspend fun install()

	/** Download progress pushed by the store, until [close]. */
	fun observe(onChange: (UpdateStatus) -> Unit)

	fun close()
}
