package com.kimjisub.launchpad.manager

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.tasks.Task
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallErrorCode
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.kimjisub.launchpad.tool.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Google Play's in-app update, flexible type only: Play downloads while the app stays usable. */
class PlayAppUpdateSource(
	private val manager: AppUpdateManager,
	private val consentLauncher: ActivityResultLauncher<IntentSenderRequest>,
) : AppUpdateSource {
	private val options = AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
	private var lastInfo: AppUpdateInfo? = null
	private var listener: InstallStateUpdatedListener? = null

	override suspend fun status(): UpdateStatus {
		val info = try {
			manager.appUpdateInfo.await()
		} catch (e: InstallException) {
			if (e.errorCode in UNSUPPORTED_ERRORS) return UpdateStatus.Unsupported
			throw e
		}
		lastInfo = info
		// A query uses availability after cancellation so a stale canceled status cannot suppress checks.
		val installStatus = info.installStatus().takeUnless { it == InstallStatus.CANCELED } ?: InstallStatus.UNKNOWN
		return downloadStatus(installStatus, info.bytesDownloaded(), info.totalBytesToDownload())
			?: when (info.updateAvailability()) {
				UpdateAvailability.UPDATE_AVAILABLE ->
					if (info.isUpdateTypeAllowed(options)) UpdateStatus.Available else UpdateStatus.Unsupported
				UpdateAvailability.UPDATE_NOT_AVAILABLE -> UpdateStatus.None
				else -> throw IllegalStateException("update availability ${info.updateAvailability()}")
			}
	}

	override fun startDownload(): Boolean {
		val info = lastInfo ?: return false
		// An AppUpdateInfo opens the consent screen once; the next attempt asks Play again.
		lastInfo = null
		return try {
			manager.startUpdateFlowForResult(info, consentLauncher, options)
		} catch (e: RuntimeException) {
			Log.err("in-app update consent failed to open", e)
			false
		}
	}

	override suspend fun install() {
		manager.completeUpdate().await()
	}

	override fun observe(onChange: (UpdateStatus) -> Unit) {
		close()
		val newListener = InstallStateUpdatedListener { state ->
			val status = downloadStatus(state.installStatus(), state.bytesDownloaded(), state.totalBytesToDownload())
			status?.let(onChange)
		}
		listener = newListener
		manager.registerListener(newListener)
	}

	override fun close() {
		listener?.let(manager::unregisterListener)
		listener = null
	}

	/** Shared download mapping; only a live cancellation event reports Canceled to rest the notice. */
	private fun downloadStatus(status: Int, done: Long, total: Long): UpdateStatus? = when (status) {
		InstallStatus.PENDING -> UpdateStatus.Downloading(null)
		InstallStatus.DOWNLOADING -> UpdateStatus.Downloading(fraction(done, total))
		InstallStatus.DOWNLOADED -> UpdateStatus.Downloaded
		InstallStatus.FAILED -> UpdateStatus.Failed
		InstallStatus.CANCELED -> UpdateStatus.Canceled
		else -> null
	}

	private fun fraction(done: Long, total: Long): Float? =
		if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else null

	private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
		addOnCompleteListener { task ->
			val error = task.exception
			when {
				error != null -> continuation.resumeWithException(error)
				task.isCanceled -> continuation.resumeWithException(IllegalStateException("Play task canceled"))
				else -> continuation.resume(task.result)
			}
		}
	}

	private companion object {
		val UNSUPPORTED_ERRORS = setOf(
			InstallErrorCode.ERROR_API_NOT_AVAILABLE,
			InstallErrorCode.ERROR_INSTALL_UNAVAILABLE,
			InstallErrorCode.ERROR_PLAY_STORE_NOT_FOUND,
			InstallErrorCode.ERROR_APP_NOT_OWNED,
		)
	}
}
