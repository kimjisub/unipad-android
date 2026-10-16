package com.kimjisub.launchpad.tool

import android.os.SystemClock
import android.content.Context
import androidx.core.net.toUri
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.analytics.FailureStage
import com.kimjisub.launchpad.analytics.PackImportReport
import com.kimjisub.launchpad.api.file.FileApi
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.PackStaging
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class UniPackDownloader(
	private val context: Context,
	private val title: String,
	private val url: String,
	/** Resolved on the download's IO thread: listing workspaces creates folders. */
	workspace: () -> File,
	private val folderName: String,
	preKnownFileSize: Long = 0,
	private var listener: Listener,
	private val usage: PackImportReport,
	scope: CoroutineScope,
) {
	companion object {
		private const val DOWNLOAD_BUFFER_SIZE = 1024
		private const val PROGRESS_UPDATE_INTERVAL_MS = 20
		private const val PERCENT_MULTIPLIER = 100
		private const val ARCHIVE_NAME = "download.zip"

		/** Share of [fileSize] received, or null when the server did not say how large the file is. */
		fun fraction(downloadedSize: Long, fileSize: Long): Float? =
			if (fileSize > 0) (downloadedSize.toFloat() / fileSize).coerceIn(0f, 1f) else null
	}

	interface Listener {
		fun onInstallStart()
		fun onGetFileSize(fileSize: Long, contentLength: Long, preKnownFileSize: Long)
		fun onDownloadProgress(percent: Int, downloadedSize: Long, fileSize: Long)
		fun onDownloadProgressPercent(percent: Int, downloadedSize: Long, fileSize: Long)
		fun onImportStart(zip: File)
		fun onInstallComplete(folder: File, unipack: UniPack)

		fun onException(throwable: Throwable)
	}

	private val notification = PackInstallNotification(context)

	val contentResolver: android.content.ContentResolver = context.contentResolver


	init {
		scope.launch(Dispatchers.IO) {
			// Another download of the same name may run at the same time, so only paths claimed here
			// are written to or deleted. The archive is received and the pack unpacked and checked in
			// staging folders of their own, which the next start clears if the app is killed meanwhile,
			// and the pack appears in the list only once complete.
			var download: File? = null
			var staged: File? = null
			var claimedFolder: File? = null
			var cancellationWatcher: Job? = null
			// What the download was doing when it failed: the same I/O error is a broken connection while
			// the response is read and a storage problem while the file is written.
			var stage = FailureStage.FILE
			try {
				withContext(Dispatchers.Main) { onInstallStart() }

				val workspace = workspace()
				val downloadFolder = PackStaging.create(workspace)
				download = downloadFolder
				val unipackFile = File(downloadFolder, ARCHIVE_NAME)

				stage = FailureStage.NETWORK
				val call = FileApi.service.download(url)
				// Run cancellation on the cancelling thread, even while this IO thread is
				// blocked in execute/read. Cancelling the call closes its socket immediately.
				cancellationWatcher = launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) {
					try {
						awaitCancellation()
					} finally {
						call.cancel()
					}
				}
				val response = call.execute()
				val responseBody = response.body()
				?: throw HttpStatusException(response.code(), "Empty response body (HTTP ${response.code()})")
				if (!response.isSuccessful) {
					responseBody.close()
					throw HttpStatusException(response.code(), "HTTP ${response.code()}")
				}
				responseBody.use { body ->
					ensureActive()
					val contentLength = responseBody.contentLength()
					// -1 (chunked) or 0 with no pre-known size gave Infinity% below
					val fileSize = contentLength.coerceAtLeast(preKnownFileSize).coerceAtLeast(0L)
					withContext(Dispatchers.Main) {
						onGetFileSize(
							fileSize,
							contentLength,
							preKnownFileSize
						)
					}

					// responseBody.use: a failed openFileDescriptor used to skip the whole block silently
					// and leak the HTTP body; now it throws and the body is always closed.
					stage = FailureStage.FILE
					val pfd = contentResolver.openFileDescriptor(unipackFile.toUri(), "w")
						?: throw IOException("Could not open ${unipackFile.path} for writing")
					pfd.use {
						FileOutputStream(it.fileDescriptor).use { outputStream ->
							body.byteStream().use { inputStream ->
								val buf = ByteArray(DOWNLOAD_BUFFER_SIZE)
								var downloadedSize = 0L
								var n: Int
								var prevPercent = -1
								var prevMillis = SystemClock.elapsedRealtime()
								while (true) {
									ensureActive()
									stage = FailureStage.NETWORK
									n = inputStream.read(buf)
									ensureActive()
									stage = FailureStage.FILE
									if (n == -1)
										break

									outputStream.write(buf, 0, n)
									downloadedSize += n.toLong()
									val millis = SystemClock.elapsedRealtime()
									if (millis - prevMillis > PROGRESS_UPDATE_INTERVAL_MS) {
										val percent = fraction(downloadedSize, fileSize)?.let { (it * PERCENT_MULTIPLIER).toInt() } ?: -1
										withContext(Dispatchers.Main) {
											onDownloadProgress(
												percent,
												downloadedSize,
												fileSize
											)
										}
										prevMillis = millis

										if (prevPercent != percent) {
											withContext(Dispatchers.Main) {
												onDownloadProgressPercent(
													percent,
													downloadedSize,
													fileSize
												)
											}
											prevPercent = percent
										}
									}
								}
							}
						}
					}
				}

				withContext(Dispatchers.Main) { onImportStart(unipackFile) }

				val stagedFolder = PackStaging.create(workspace)
				staged = stagedFolder
				ZipFile(unipackFile).use { zip ->
					zip.extractAll(stagedFolder.path)
				}
				FileManager.removeDoubleFolder(stagedFolder.path)
				// load() runs checkFile + info; without it every parser returned early and criticalError
				// was always false, so any archive installed as a pack.
				val checked = UniPackFolder(stagedFolder).load().loadDetail()
				if (checked.criticalError) {
					val errorMsg = checked.errorDetail ?: "Unknown error"
					Log.err(errorMsg)
					throw UniPackCriticalErrorException(errorMsg)
				}

				ensureActive()
				val folder = FileManager.moveToNextFolder(stagedFolder, workspace, folderName)
				claimedFolder = folder
				val unipack = UniPackFolder(folder).load()

				// Whether the pack stays is settled on the main thread, where the hosting screen
				// closes. A screen already gone never runs this block and the download is discarded
				// below; a pack that was announced is released from cleanup and counted, so closing
				// the screen right after cannot delete a pack recorded as imported.
				withContext(Dispatchers.Main) {
					// Transfer ownership before a completion listener can close the screen or throw.
					claimedFolder = null
					onInstallComplete(folder, unipack)
				}

			} catch (e: CancellationException) {
				// The hosting scope was cancelled (activity destroyed): remove what was not announced
				// yet, but do not report it as a failure and do not swallow the cancellation.
				usage.cancelled()
				throw e
			} catch (e: Exception) {
				// Socket cancellation normally surfaces as IOException. Keep it a cancellation,
				// without notifying a screen that has already gone or counting a network failure.
				if (!isActive) usage.cancelled()
				ensureActive()
				Log.err("Download failed", e)
				usage.failed(e, stage)
				withContext(Dispatchers.Main) { onException(e) }
			} finally {
				cancellationWatcher?.cancel()
				notification.cancelProgress()
				// No suspending callbacks here: cancellation or a failing listener cannot skip
				// cleanup. Successful installation already released its folder above.
				claimedFolder?.let(FileManager::deleteDirectory)
				staged?.let(PackStaging::discard)
				download?.let(PackStaging::discard)
				PackStorageChanges.notifyChanged()
			}
		}
	}

	private fun onInstallStart() {
		notification.showProgress(title, R.string.downloadWaiting, 0, indeterminate = true)

		listener.onInstallStart()
	}

	private fun onGetFileSize(fileSize: Long, contentLength: Long, preKnownFileSize: Long) {
		listener.onGetFileSize(fileSize, contentLength, preKnownFileSize)
	}

	private fun onDownloadProgress(percent: Int, downloadedSize: Long, fileSize: Long) {
		listener.onDownloadProgress(percent, downloadedSize, fileSize)
	}

	private fun onDownloadProgressPercent(percent: Int, downloadedSize: Long, fileSize: Long) {
		notification.showProgress(
			title,
			"${FileManager.byteToMB(downloadedSize)} / ${FileManager.byteToMB(fileSize)} MB",
			percent,
			indeterminate = false
		)

		listener.onDownloadProgressPercent(percent, downloadedSize, fileSize)
	}

	private fun onImportStart(zip: File) {
		notification.showProgress(title, R.string.importing, 0, indeterminate = true)

		listener.onImportStart(zip)
	}

	private fun onInstallComplete(folder: File, unipack: UniPack) {
		notification.showResult(title, R.string.success)
		usage.succeeded()
		listener.onInstallComplete(folder, unipack)
	}

	private fun onException(throwable: Throwable) {
		Log.err("Download exception", throwable)
		notification.showResult(title, R.string.failed)
		listener.onException(throwable)
	}

	class UniPackCriticalErrorException(message: String) : Exception(message)

	/** The server answered, but not with the pack. Keeps the status so usage analytics can tell a missing pack from a server error. */
	class HttpStatusException(val status: Int, message: String) : IOException(message)
}