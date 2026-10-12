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
		return when (info.installStatus()) {
			InstallStatus.PENDING -> UpdateStatus.Downloading(null)
			InstallStatus.DOWNLOADING -> UpdateStatus.Downloading(fraction(info.bytesDownloaded(), info.totalBytesToDownload()))
			InstallStatus.DOWNLOADED -> UpdateStatus.Downloaded
			InstallStatus.FAILED -> UpdateStatus.Failed
			else -> when (info.updateAvailability()) {
				UpdateAvailability.UPDATE_AVAILABLE ->
					if (info.isUpdateTypeAllowed(options)) UpdateStatus.Available else UpdateStatus.Unsupported
				UpdateAvailability.UPDATE_NOT_AVAILABLE -> UpdateStatus.None
				else -> throw IllegalStateException("update availability ${info.updateAvailability()}")
			}
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
			val status = when (state.installStatus()) {
				InstallStatus.PENDING -> UpdateStatus.Downloading(null)
				InstallStatus.DOWNLOADING -> UpdateStatus.Downloading(fraction(state.bytesDownloaded(), state.totalBytesToDownload()))
				InstallStatus.DOWNLOADED -> UpdateStatus.Downloaded
				InstallStatus.FAILED -> UpdateStatus.Failed
				InstallStatus.CANCELED -> UpdateStatus.Canceled
				else -> null
			}
			status?.let(onChange)
		}
		listener = newListener
		manager.registerListener(newListener)
	}

	override fun close() {
		listener?.let(manager::unregisterListener)
		listener = null
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
