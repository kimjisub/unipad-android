package com.kimjisub.launchpad.network

import com.kimjisub.launchpad.api.file.FileApi
import com.kimjisub.launchpad.manager.Unipack
import com.kimjisub.manager.FileManager
import com.kimjisub.manager.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class UnipackInstaller(//todo notification manage
		private val url: String,
		workspace: File,
		folderName: String,
		preKnownFileSize: Long = 0,
		private var listener: Listener) {
	private val zip: File = FileManager.makeNextPath(workspace, folderName, ".zip")
	private val folder: File = FileManager.makeNextPath(workspace, folderName, "/")

	interface Listener {
		fun onInstallStart()
		fun onGetFileSize(fileSize: Long, contentLength: Long, preKnownFileSize: Long)
		fun onDownloadProgress(percent: Int, downloadedSize: Long, fileSize: Long)
		fun onDownloadProgressPercent(percent: Int, downloadedSize: Long, fileSize: Long)
		fun onAnalyzeStart(zip: File)
		fun onInstallComplete(folder: File, unipack: Unipack)

		fun onException(throwable: Throwable)
	}

	init {
		CoroutineScope(Dispatchers.IO).launch {
			try {
				withContext(Dispatchers.Main) { listener.onInstallStart() }

				val call = FileApi.service.download(url)
				val responseBody = call.execute().body()!!
				val contentLength = responseBody.contentLength()
				val fileSize = contentLength.coerceAtLeast(preKnownFileSize)
				withContext(Dispatchers.Main) { listener.onGetFileSize(fileSize, contentLength, preKnownFileSize) }
				// todo async

				val inputStream = responseBody.byteStream()
				val outputStream = FileOutputStream(zip)
				val buf = ByteArray(1024)
				var downloadedSize = 0L
				var n: Int
				var prevPercent: Int = -1
				val tickSize : Long = fileSize / 10000
				var prevTick : Long = -1 // 1/1000 of file size
				while (true) {
					n = inputStream.read(buf)
					if (n == -1)
						break

					outputStream.write(buf, 0, n)
					downloadedSize += n.toLong()
					val tick = downloadedSize/tickSize
					if (tick != prevTick) {
						val percent = (downloadedSize.toFloat() / fileSize * 100).toInt()
						withContext(Dispatchers.Main) { listener.onDownloadProgress(percent, downloadedSize, fileSize) }
						prevTick = tick

						if(prevPercent != percent){
							withContext(Dispatchers.Main) { listener.onDownloadProgressPercent(percent, downloadedSize, fileSize) }
							prevPercent = percent
						}
					}
				}
				inputStream.close()
				outputStream.close()

				withContext(Dispatchers.Main) { listener.onAnalyzeStart(zip) }

				FileManager.unZipFile(zip.path, folder.path)
				val unipack = Unipack(folder, true)
				if (unipack.CriticalError) {
					Log.err(unipack.ErrorDetail)
					FileManager.deleteDirectory(folder)
					throw UniPackCriticalErrorException(unipack.ErrorDetail)
				}

				withContext(Dispatchers.Main) { listener.onInstallComplete(folder, unipack) }
			} catch (e: Exception) {
				e.printStackTrace()
				withContext(Dispatchers.Main) { listener.onException(e) }
				FileManager.deleteDirectory(folder)
			}
			FileManager.deleteDirectory(zip)
		}
	}

	class UniPackCriticalErrorException(message: String) : Exception(message)
}