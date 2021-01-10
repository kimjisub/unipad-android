package com.kimjisub.launchpad.tool

import android.content.Context
import androidx.core.app.NotificationCompat
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.api.file.FileApi
import com.kimjisub.launchpad.manager.NotificationManager
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.manager.FileManager
import com.kimjisub.manager.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class UniPackInstaller(
	private val context: Context,
	private val title: String,
	private val url: String,
	workspace: File,
	folderName: String,
	preKnownFileSize: Long = 0,
	private var listener: Listener
) {
	private val zip: File = FileManager.makeNextPath(workspace, folderName, ".zip")
	private val folder: File = FileManager.makeNextPath(workspace, folderName, "/")

	private val notificationId = (Math.random() * Integer.MAX_VALUE).toInt()
	private val notificationManager = NotificationManager.getManager(context)
	private val notificationBuilder: NotificationCompat.Builder by lazy {
		val builder = NotificationCompat.Builder(context, NotificationManager.Channel.Download.name)
		builder.apply {
			setAutoCancel(true)
			setSmallIcon(R.mipmap.ic_launcher)
		}
		builder
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

	init {
		CoroutineScope(Dispatchers.IO).launch {
			try {
				withContext(Dispatchers.Main) { onInstallStart() }

				val call = FileApi.service.download(url)
				val responseBody = call.execute().body()!!
				val contentLength = responseBody.contentLength()
				val fileSize = contentLength.coerceAtLeast(preKnownFileSize)
				withContext(Dispatchers.Main) {
					onGetFileSize(
						fileSize,
						contentLength,
						preKnownFileSize
					)
				}

				val inputStream = responseBody.byteStream()
				val outputStream = FileOutputStream(zip)
				val buf = ByteArray(1024)
				var downloadedSize = 0L
				var n: Int
				var prevPercent: Int = -1
				var prevMillis = System.currentTimeMillis()
				while (true) {
					n = inputStream.read(buf)
					if (n == -1)
						break

					outputStream.write(buf, 0, n)
					downloadedSize += n.toLong()
					val millis = System.currentTimeMillis()
					if (millis - prevMillis > 20) {
						val percent = (downloadedSize.toFloat() / fileSize * 100).toInt()
						Log.test(
							"${percent}%    ${FileManager.byteToMB(downloadedSize)} / ${
								FileManager.byteToMB(
									fileSize
								)
							} MB"
						)
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
				inputStream.close()
				outputStream.close()

				withContext(Dispatchers.Main) { onImportStart(zip) }

				FileManager.unZipFile(zip.path, folder.path)
				val unipack = UniPack(folder, true)
				if (unipack.criticalError) {
					Log.err(unipack.errorDetail!!)
					FileManager.deleteDirectory(folder)
					throw UniPackCriticalErrorException(unipack.errorDetail!!)
				}

				withContext(Dispatchers.Main) { onInstallComplete(folder, unipack) }

			} catch (e: Exception) {
				e.printStackTrace()
				withContext(Dispatchers.Main) { onException(e) }
				FileManager.deleteDirectory(folder)
			}
			FileManager.deleteDirectory(zip)
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