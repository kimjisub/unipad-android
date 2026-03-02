package com.kimjisub.launchpad.tool

import android.app.PendingIntent
import android.os.SystemClock
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.SplashActivity
import com.kimjisub.launchpad.api.file.FileApi
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.NotificationManager
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
	workspace: File,
	folderName: String,
	preKnownFileSize: Long = 0,
	private var listener: Listener,
	scope: CoroutineScope,
) {
	companion object {
		private const val DOWNLOAD_BUFFER_SIZE = 1024
		private const val PROGRESS_UPDATE_INTERVAL_MS = 20
		private const val PERCENT_MULTIPLIER = 100
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

	private val unipackFile: File = FileManager.makeNextPath(workspace, folderName, ".zip")

	private val folder: File = FileManager.makeNextPath(workspace, folderName, "/")

	private val notificationId = kotlin.random.Random.nextInt(Int.MAX_VALUE)
	private val notificationManager = NotificationManager.getManager(context)
	private val notificationBuilder: NotificationCompat.Builder by lazy {
		val builder = NotificationCompat.Builder(context, NotificationManager.Channel.Download.name)
		builder.apply {
			setAutoCancel(true)
			setSmallIcon(R.mipmap.ic_launcher)

			val intent = Intent(context, SplashActivity::class.java)
			intent.action = Intent.ACTION_MAIN
			intent.addCategory(Intent.CATEGORY_LAUNCHER)
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			val pIntent: PendingIntent =
				PendingIntent.getActivity(
					context,
					1,
					intent,
					PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
				)
			setContentIntent(pIntent)
		}
		builder
	}

	val contentResolver: android.content.ContentResolver = context.contentResolver


	init {
		scope.launch(Dispatchers.IO) {
			try {
				withContext(Dispatchers.Main) { onInstallStart() }

				val call = FileApi.service.download(url)
				val responseBody = call.execute().body()
				?: throw IOException("Empty response body")
				val contentLength = responseBody.contentLength()
				val fileSize = contentLength.coerceAtLeast(preKnownFileSize)
				withContext(Dispatchers.Main) {
					onGetFileSize(
						fileSize,
						contentLength,
						preKnownFileSize
					)
				}

				contentResolver.openFileDescriptor(unipackFile.toUri(), "w")?.use {
					FileOutputStream(it.fileDescriptor).use { outputStream ->
						responseBody.byteStream().use { inputStream ->
							val buf = ByteArray(DOWNLOAD_BUFFER_SIZE)
							var downloadedSize = 0L
							var n: Int
							var prevPercent = -1
							var prevMillis = SystemClock.elapsedRealtime()
							while (true) {
								n = inputStream.read(buf)
								if (n == -1)
									break

								outputStream.write(buf, 0, n)
								downloadedSize += n.toLong()
								val millis = SystemClock.elapsedRealtime()
								if (millis - prevMillis > PROGRESS_UPDATE_INTERVAL_MS) {
									val percent = (downloadedSize.toFloat() / fileSize * PERCENT_MULTIPLIER).toInt()
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

						withContext(Dispatchers.Main) { onImportStart(unipackFile) }

						ZipFile(unipackFile).use { zip ->
							zip.extractAll(folder.path)
						}
						FileManager.removeDoubleFolder(folder.path)
						val unipack = UniPackFolder(folder).loadDetail()
						if (unipack.criticalError) {
							val errorMsg = unipack.errorDetail ?: "Unknown error"
							Log.err(errorMsg)
							FileManager.deleteDirectory(folder)
							throw UniPackCriticalErrorException(errorMsg)
						}

						withContext(Dispatchers.Main) { onInstallComplete(folder, unipack) }

					}
				}


			} catch (e: Exception) {
				Log.err("Download failed", e)
				withContext(Dispatchers.Main) { onException(e) }
				FileManager.deleteDirectory(folder)
			}
			FileManager.deleteDirectory(unipackFile)
		}
	}

	private fun onInstallStart() {
		notificationBuilder.apply {
			setContentTitle(title)
			setContentText(context.getString(R.string.downloadWaiting))
			setProgress(100, 0, true)
			setOngoing(true)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		listener.onInstallStart()
	}

	private fun onGetFileSize(fileSize: Long, contentLength: Long, preKnownFileSize: Long) {
		listener.onGetFileSize(fileSize, contentLength, preKnownFileSize)
	}

	private fun onDownloadProgress(percent: Int, downloadedSize: Long, fileSize: Long) {
		listener.onDownloadProgress(percent, downloadedSize, fileSize)
	}

	private fun onDownloadProgressPercent(percent: Int, downloadedSize: Long, fileSize: Long) {
		notificationBuilder.apply {
			setContentTitle(title)
			setContentText(
				"${FileManager.byteToMB(downloadedSize)} / ${
					FileManager.byteToMB(
						fileSize
					)
				} MB"
			)
			setProgress(100, percent, false)
			setOngoing(true)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		listener.onDownloadProgressPercent(percent, downloadedSize, fileSize)
	}

	private fun onImportStart(zip: File) {
		notificationBuilder.apply {
			setContentTitle(title)
			setContentText(context.getString(R.string.importing))
			setProgress(100, 0, true)
			setOngoing(true)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		listener.onImportStart(zip)
	}

	private fun onInstallComplete(folder: File, unipack: UniPack) {
		notificationBuilder.apply {
			setContentTitle(title)
			setContentText(context.getString(R.string.success))
			setProgress(0, 0, false)
			setOngoing(false)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		listener.onInstallComplete(folder, unipack)
	}

	private fun onException(throwable: Throwable) {
		Log.err("Download exception", throwable)
		notificationBuilder.apply {
			setContentTitle(title)
			setContentText(context.getString(R.string.downloadWaiting))
			setProgress(0, 0, false)
			setOngoing(false)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		listener.onException(throwable)
	}

	class UniPackCriticalErrorException(message: String) : Exception(message)
}