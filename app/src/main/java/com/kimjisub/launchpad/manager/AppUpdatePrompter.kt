package com.kimjisub.launchpad.manager

import android.app.Activity
import com.google.android.play.core.install.model.ActivityResult
import com.kimjisub.launchpad.tool.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The pack list's new-version notice. Downloading and installing are separate choices of the
 * person; nothing is installed or restarted on its own. Every action asks [isSafe] (the list is in
 * front with nothing else going on) at the moment it would reach the store, and a result that
 * arrives while the list is not in front is only kept until the list shows it.
 */
class AppUpdatePrompter(
	private val source: AppUpdateSource,
	private val schedule: AppUpdateSchedule,
	private val scope: CoroutineScope,
	private val isSafe: () -> Boolean,
	private val openStore: () -> Boolean,
) {
	sealed interface Card {
		data object Offer : Card
		data class Downloading(val fraction: Float?) : Card
		data object Ready : Card
		data object Failed : Card
	}

	enum class Dialog { NoUpdate, CheckFailed, Unsupported, StoreNotice, StoreUnavailable }

	data class State(
		val card: Card? = null,
		val dialog: Dialog? = null,
		val checking: Boolean = false,
	)

	private val _state = MutableStateFlow(State())
	val state: StateFlow<State> = _state.asStateFlow()

	private var job: Job? = null
	private val busy get() = job?.isActive == true

	init {
		source.observe(::onStoreChange)
	}

	/** The list just became safe: look at a download in progress, and once a day for a new version. */
	fun onListReady() {
		if (busy) return
		val tracking = schedule.downloadRequested
		val offerNew = schedule.isAutoCheckDue() && schedule.mayOfferDownload()
		if (!tracking && !offerNew) return
		if (offerNew) schedule.markAutoCheck()
		run {
			val status = query() ?: return@run
			showAuto(status, offerNew)
		}
	}

	fun checkByHand() {
		if (busy || !isSafe()) return
		schedule.markAutoCheck()
		_state.update { it.copy(checking = true, dialog = null) }
		run {
			val status = query()
			_state.update { it.copy(checking = false) }
			showRequested(status)
		}
	}

	fun download() {
		if (busy || !isSafe()) return
		run { startDownload(query()) }
	}

	fun retry() {
		if (busy || !isSafe()) return
		run {
			when (val status = query()) {
				UpdateStatus.Available -> startDownload(status)
				else -> showRequested(status)
			}
		}
	}

	fun install() {
		if (busy || !isSafe()) return
		run {
			val status = query()
			if (status != UpdateStatus.Downloaded) {
				showRequested(status)
				return@run
			}
			if (!isSafe()) return@run
			try {
				source.install()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Log.err("in-app update install failed", e)
				failed()
			}
		}
	}

	fun later() {
		when (_state.value.card) {
			Card.Ready -> schedule.markInstallPostponed()
			Card.Offer, Card.Failed -> decline()
			is Card.Downloading, null -> return
		}
		setCard(null)
	}

	/** Result of the store's consent screen opened by [download]. */
	fun onConsentResult(resultCode: Int) {
		when (resultCode) {
			Activity.RESULT_OK -> {
				schedule.downloadRequested = true
				setCard(Card.Downloading(null))
			}
			ActivityResult.RESULT_IN_APP_UPDATE_FAILED -> failed()
			else -> {
				decline()
				setCard(null)
			}
		}
	}

	fun askStore() {
		_state.update { it.copy(dialog = Dialog.StoreNotice) }
	}

	fun openStore() {
		dismissDialog()
		if (!isSafe()) return
		// The store cannot tell the app whether it updated, so leaving for it counts as a decline.
		decline()
		setCard(null)
		if (!openStore.invoke()) _state.update { it.copy(dialog = Dialog.StoreUnavailable) }
	}

	fun dismissDialog() {
		_state.update { it.copy(dialog = null) }
	}

	fun close() {
		source.close()
	}

	private fun run(block: suspend () -> Unit) {
		job = scope.launch { block() }
	}

	/** Null when the store did not answer within [TIMEOUT_MS] or failed. */
	private suspend fun query(): UpdateStatus? = try {
		withTimeoutOrNull(TIMEOUT_MS) { source.status() }
	} catch (e: CancellationException) {
		throw e
	} catch (e: Exception) {
		Log.err("in-app update check failed", e)
		null
	}

	private fun startDownload(status: UpdateStatus?) {
		if (status != UpdateStatus.Available) {
			showRequested(status)
			return
		}
		if (!isSafe()) return
		if (!source.startDownload()) failed()
	}

	private fun showAuto(status: UpdateStatus, offerNew: Boolean) {
		when (status) {
			UpdateStatus.Available -> if (offerNew) setCard(Card.Offer)
			is UpdateStatus.Downloading -> onStoreChange(status)
			UpdateStatus.Downloaded -> if (schedule.mayOfferInstall()) setCard(Card.Ready)
			UpdateStatus.Failed -> failed()
			UpdateStatus.Canceled -> onStoreChange(status)
			UpdateStatus.None, UpdateStatus.Unsupported -> {
				schedule.downloadRequested = false
				setCard(null)
			}
		}
	}

	/** A result the person asked for; a dialog opens only while the list is still in front. */
	private fun showRequested(status: UpdateStatus?) {
		when (status) {
			UpdateStatus.Available -> setCard(Card.Offer)
			is UpdateStatus.Downloading, UpdateStatus.Downloaded, UpdateStatus.Canceled -> onStoreChange(status)
			UpdateStatus.Failed -> failed()
			UpdateStatus.None -> showDialog(Dialog.NoUpdate)
			UpdateStatus.Unsupported -> showDialog(Dialog.Unsupported)
			null -> showDialog(Dialog.CheckFailed)
		}
	}

	private fun onStoreChange(status: UpdateStatus) {
		when (status) {
			is UpdateStatus.Downloading -> {
				schedule.downloadRequested = true
				setCard(Card.Downloading(status.fraction))
			}
			UpdateStatus.Downloaded -> {
				schedule.downloadRequested = true
				setCard(Card.Ready)
			}
			UpdateStatus.Failed -> failed()
			UpdateStatus.Canceled -> {
				decline()
				setCard(null)
			}
			UpdateStatus.Available, UpdateStatus.None, UpdateStatus.Unsupported -> Unit
		}
	}

	private fun showDialog(dialog: Dialog) {
		if (isSafe()) _state.update { it.copy(dialog = dialog) }
	}

	private fun failed() {
		schedule.downloadRequested = false
		setCard(Card.Failed)
	}

	private fun decline() {
		schedule.downloadRequested = false
		schedule.markDeclined()
	}

	private fun setCard(card: Card?) {
		_state.update { it.copy(card = card) }
	}

	companion object {
		const val TIMEOUT_MS = 10_000L
	}
}
