package com.kimjisub.launchpad.network

import com.kimjisub.launchpad.api.file.FileApi.Companion.service
import com.kimjisub.launchpad.api.unipad.vo.UnishareVO
import com.kimjisub.launchpad.manager.Unipack
import com.kimjisub.manager.FileManager
import com.kimjisub.manager.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

class UnishareDownloader(
		private val unishare: UnishareVO,
		UniPackRootExt: File,
		private var listener: UnishareDownloadListener) {
	private val zip: File = FileManager.makeNextPath(UniPackRootExt, unishare.title + " #" + unishare._id, ".zip")
	private val folder: File = FileManager.makeNextPath(UniPackRootExt, unishare.title + " #" + unishare._id, "/")

	interface UnishareDownloadListener {
		fun onDownloadStart()
		fun onGetFileSize(contentSize: Long, realSize: Long)
		fun onDownloading(downloadedSize: Long)
		fun onDownloadEnd(zip: File)
		fun onAnalyzeStart()
		fun onAnalyzeSuccess(unipack: Unipack)
		fun onAnalyzeFail(unipack: Unipack)
		fun onAnalyzeEnd(folder: File)
		fun onException(throwable: Throwable)
	}

	init {
		CoroutineScope(Dispatchers.Main).launch {
			try {
				listener.onDownloadStart()

				var inputStream: InputStream? = null
				var outputStream: OutputStream?

				var fileSize: Long? = null

				withContext(Dispatchers.IO) {
					val call = service.unishare_download(unishare._id)
					val responseBody = call!!.execute().body()
					inputStream = responseBody!!.byteStream()
					fileSize = responseBody.contentLength()
				}
				listener.onGetFileSize(fileSize!!, if (fileSize!! > 0) fileSize!! else unishare.fileSize)

				withContext(Dispatchers.IO) {
					outputStream = FileOutputStream(zip)
					val buf = ByteArray(1024)
					var n: Int
					var downloadSize = 0L
					var loop = 0
					while (true) {
						n = inputStream!!.read(buf)
						if (n == -1)
							break

						outputStream!!.write(buf, 0, n)
						downloadSize += n.toLong()
						if (loop % 100 == 0)
							withContext(Dispatchers.Main) { listener.onDownloading(downloadSize) }
						loop++
					}
					inputStream!!.close()
					outputStream!!.close()
				}

				listener.onDownloadEnd(zip)
				listener.onAnalyzeStart()

				var unipack: Unipack? = null
				withContext(Dispatchers.IO) {
					FileManager.unZipFile(zip.path, folder.path)
					unipack = Unipack(folder, true)
				}
				if (unipack!!.CriticalError) {
					Log.err(unipack!!.ErrorDetail)
					listener.onAnalyzeFail(unipack!!)
					withContext(Dispatchers.IO) {
						FileManager.deleteDirectory(folder)
					}
				} else
					listener.onAnalyzeSuccess(unipack!!)
				listener.onAnalyzeEnd(folder)
			} catch (e: Exception) {
				e.printStackTrace()
				listener.onException(e)
				withContext(Dispatchers.IO) {
					FileManager.deleteDirectory(folder)
				}
			}
			withContext(Dispatchers.IO) {
				FileManager.deleteDirectory(zip)
			}
		}
	}
}