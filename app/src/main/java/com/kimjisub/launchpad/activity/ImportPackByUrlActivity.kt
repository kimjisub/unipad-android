package com.kimjisub.launchpad.activity

import android.os.Bundle
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.api.unipad.UniPadApi.Companion.service
import com.kimjisub.launchpad.api.unipad.vo.UnishareVO
import com.kimjisub.launchpad.tool.UniPackInstaller
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.manager.FileManager
import com.kimjisub.manager.Log
import kotlinx.android.synthetic.main.activity_importpack.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class ImportPackByUrlActivity : BaseActivity() {
	private val code: String? by lazy { intent?.data?.getQueryParameter("code") }

	private var fileSize: Long = 0
	private var identifyCode = ""


	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(layout.activity_importpack)

		log("code: $code")
		identifyCode = "#$code"

		TV_title.setText(string.wait_a_sec)
		TV_message.text = identifyCode
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

							TV_title.setText(string.unipackNotFound)
							TV_message.text = identifyCode
						}
					}
				}
			}

			override fun onFailure(call: Call<UnishareVO?>?, t: Throwable?) {
				log("server error")
				TV_title.setText(string.failed)
				TV_message.text = "server error\n${t?.message}"
			}
		})
	}

	fun startInstall(unishare: UnishareVO) {
		UniPackInstaller(
			context = this,
			title = "${unishare.title} #${unishare._id}",
			url = "https://api.unipad.io/unishare/${unishare._id}/download",
			workspace = uniPackExt,
			folderName = "${unishare.title} #${unishare._id}",
			listener = object : UniPackInstaller.Listener {
				override fun onInstallStart() {
					log("Install start")
					TV_title.setText(string.downloadWaiting)
					TV_message.text = "#${code}\n${unishare.title}\n${unishare.producer}"
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

					TV_title.setText(string.downloading)
					TV_message.text = "${percent}%\n${downloadedMB} / $fileSizeMB MB"
				}

				override fun onImportStart(zip: File) {
					log("Import Start")

					TV_title.setText(string.importing)
					TV_message.text = "#${code}\n${unishare.title}\n${unishare.producer}"
				}

				override fun onInstallComplete(folder: File, unipack: UniPack) {
					log("Install Success")

					TV_title.setText(string.success)
					TV_message.text = unipack.toString(this@ImportPackByUrlActivity)

					delayFinish()
				}

				override fun onException(throwable: Throwable) {
					log("Exception: " + throwable.message)
					throwable.printStackTrace()

					TV_title.setText(string.exceptionOccurred)
					TV_message.text = throwable.message
				}
			}
		)
	}

	private fun log(msg: String) {
		TV_info.append(msg + "\n")
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