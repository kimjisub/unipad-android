package com.kimjisub.launchpad.activity

import android.os.Bundle
import androidx.core.net.toFile
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.api.unipad.UniPadApi.Companion.service
import com.kimjisub.launchpad.api.unipad.vo.UnishareVO
import com.kimjisub.launchpad.databinding.ActivityImportpackBinding
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.tool.UniPackDownloader
import com.kimjisub.launchpad.unipack.UniPack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class ImportPackByUrlActivity : BaseActivity() {
	private lateinit var b: ActivityImportpackBinding

	private val code: String? by lazy { intent?.data?.getQueryParameter("code") }

	private var fileSize: Long = 0
	private var identifyCode = ""


	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivityImportpackBinding.inflate(layoutInflater)
		setContentView(b.root)

		log("code: $code")
		identifyCode = "#$code"

		b.title.setText(string.wait_a_sec)
		b.message.text = identifyCode
		service.getUnishare(code)!!.enqueue(object : Callback<UnishareVO?> {
			override fun onResponse(call: Call<UnishareVO?>, response: Response<UnishareVO?>) {
				if (response.isSuccessful) {
					val unishare = response.body()!!
					identifyCode = "${unishare.title} #${unishare._id}"
					log("title: " + unishare.title)
					log("producerName: " + unishare.producer)

					startInstall(unishare)
				} else {
					when (response.code()) {
						404 -> {
							log("404 Not Found")

							b.title.setText(string.unipackNotFound)
							b.message.text = identifyCode
						}
					}
				}
			}

			override fun onFailure(call: Call<UnishareVO?>?, t: Throwable?) {
				log("server error")
				b.title.setText(string.failed)
				b.message.text = "server error\n${t?.message}"
			}
		})
	}

	fun startInstall(unishare: UnishareVO) {
		UniPackDownloader(
			context = this,
			title = "${unishare.title} #${unishare._id}",
			url = "https://api.unipad.io/unishare/${unishare._id}/download",
			workspace = workspace.mainWorkspace.toFile(),
			folderName = "${unishare.title} #${unishare._id}",
			listener = object : UniPackDownloader.Listener {
				override fun onInstallStart() {
					log("Install start")
					b.title.setText(string.downloadWaiting)
					b.message.text = "#${code}\n${unishare.title}\n${unishare.producer}"
				}

				override fun onGetFileSize(
					fileSize: Long,
					contentLength: Long,
					preKnownFileSize: Long
				) {
					log("fileSize: $contentLength → $fileSize")
				}

				override fun onDownloadProgress(
					percent: Int,
					downloadedSize: Long,
					fileSize: Long
				) {
				}

				override fun onDownloadProgressPercent(
					percent: Int,
					downloadedSize: Long,
					fileSize: Long
				) {
					val downloadedMB: String = FileManager.byteToMB(downloadedSize)
					val fileSizeMB: String = FileManager.byteToMB(fileSize)

					b.title.setText(string.downloading)
					b.message.text = "${percent}%\n${downloadedMB} / $fileSizeMB MB"
				}

				override fun onImportStart(zip: File) {
					log("Import Start")

					b.title.setText(string.importing)
					b.message.text = "#${code}\n${unishare.title}\n${unishare.producer}"
				}

				override fun onInstallComplete(folder: File, unipack: UniPack) {
					log("Install Success")

					b.title.setText(string.success)
					b.message.text = unipack.toString(this@ImportPackByUrlActivity)

					delayFinish()
				}

				override fun onException(throwable: Throwable) {
					log("Exception: " + throwable.message)
					throwable.printStackTrace()

					b.title.setText(string.exceptionOccurred)
					b.message.text = throwable.message
				}
			}
		)
	}

	private fun log(msg: String) {
		b.info.append(msg + "\n")
		Log.download(msg)
	}

	private fun delayFinish() {
		log("delayFinish()")
		CoroutineScope(Dispatchers.Main).launch {
			delay(3000)

			finish()
			overridePendingTransition(anim.activity_in, anim.activity_out)
		}
	}
}