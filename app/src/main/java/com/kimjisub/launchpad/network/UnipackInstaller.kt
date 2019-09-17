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

class UnipackInstaller(
		private val url: String,
		workspace: File,
		folderName: String,
		forceContentLength: Long = 0,
		private var listener: Listener) {
	private val zip: File = FileManager.makeNextPath(workspace, folderName, ".zip")
	private val folder: File = FileManager.makeNextPath(workspace, folderName, "/")

	interface Listener {
		fun onDownloadStart()
		fun onGetFileSize(contentLength: Long)
		fun onDownloading(downloadedSize: Long)
		fun onDownloadEnd(zip: File)
		fun onAnalyzeStart()
		fun onAnalyzeSuccess(unipack: Unipack)
		fun onAnalyzeFail(unipack: Unipack)
		fun onAnalyzeEnd(folder: File)
		fun onException(throwable: Throwable)
	}

	init {
		CoroutineScope(Dispatchers.IO).launch {
			try {
				withContext(Dispatchers.Main) { listener.onDownloadStart() }

				val call = FileApi.service.download(url)
				val responseBody = call.execute().body()!!
				val contentLength = responseBody.contentLength().coerceAtLeast(forceContentLength)
				withContext(Dispatchers.Main) { listener.onGetFileSize(contentLength) }

				val inputStream = responseBody.byteStream()
				val outputStream = FileOutputStream(zip)
				val buf = ByteArray(1024)
				var downloadSize = 0L
				var loop = 0

				var n: Int
				while (true) {
					n = inputStream.read(buf)
					if (n == -1)
						break

					outputStream.write(buf, 0, n)
					downloadSize += n.toLong()
					if (loop % 100 == 0)
						withContext(Dispatchers.Main) { listener.onDownloading(downloadSize) }
					loop++
				}
				inputStream.close()
				outputStream.close()

				withContext(Dispatchers.Main) {
					listener.onDownloadEnd(zip)
					listener.onAnalyzeStart()
				}

				FileManager.unZipFile(zip.path, folder.path)
				val unipack = Unipack(folder, true)
				if (unipack.CriticalError) {
					Log.err(unipack.ErrorDetail)
					withContext(Dispatchers.Main) { listener.onAnalyzeFail(unipack) }
					FileManager.deleteDirectory(folder)
				} else
					withContext(Dispatchers.Main) { listener.onAnalyzeSuccess(unipack) }
				withContext(Dispatchers.Main) { listener.onAnalyzeEnd(folder) }
			} catch (e: Exception) {
				e.printStackTrace()
				withContext(Dispatchers.Main) { listener.onException(e) }
				FileManager.deleteDirectory(folder)
			}
			FileManager.deleteDirectory(zip)
		}
	}
}