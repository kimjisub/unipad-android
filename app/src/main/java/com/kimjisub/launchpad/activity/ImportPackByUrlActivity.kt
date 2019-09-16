package com.kimjisub.launchpad.activity

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import androidx.core.app.NotificationCompat
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.api.unipad.UniPadApi.Companion.service
import com.kimjisub.launchpad.api.unipad.vo.UnishareVO
import com.kimjisub.launchpad.manager.NotificationManager
import com.kimjisub.launchpad.manager.NotificationManager.Channel
import com.kimjisub.launchpad.manager.Unipack
import com.kimjisub.launchpad.network.UnishareDownloader
import com.kimjisub.launchpad.network.UnishareDownloader.UnishareDownloadListener
import com.kimjisub.manager.FileManager
import com.kimjisub.manager.Log
import kotlinx.android.synthetic.main.activity_importpack.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File

class ImportPackByUrlActivity : BaseActivity() {
	private val code: String? by lazy { intent?.data?.getQueryParameter("code") }

	private var fileSize: Long = 0
	private var identifyCode = ""

	// Notification /////////////////////////////////////////////////////////////////////////////////////////

	private val notificationBuilder: NotificationCompat.Builder by lazy {
		val builder = NotificationCompat.Builder(this, Channel.DOWNLOAD)
		builder.apply {
			setAutoCancel(true)
			setSmallIcon(mipmap.ic_launcher)
		}
		builder
	}
	private val notificationId = (Math.random() * Integer.MAX_VALUE).toInt()

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(layout.activity_importpack)

		log("code: $code")
		identifyCode = "#$code"

		setStatus(Status.Prepare)
		service.unishare_get(code)!!.enqueue(object : Callback<UnishareVO?> {
			var prevPercent: Int = 0

			override fun onResponse(call: Call<UnishareVO?>, response: Response<UnishareVO?>) {
				if (response.isSuccessful) {
					val unishare = response.body()!!
					identifyCode = "${unishare.title} #${unishare._id}"
					log("title: " + unishare.title)
					log("producerName: " + unishare.producer)
					setStatus(Status.GetInfo)
					UnishareDownloader(unishare, F_UniPackRootExt, object : UnishareDownloadListener {
						override fun onDownloadStart() {
							log("Download start")
							setStatus(Status.DownloadStart, unishare.title, unishare.producer)
						}

						override fun onGetFileSize(contentSize: Long, realSize: Long) {
							fileSize = realSize
							log("fileSize: $contentSize → $realSize")
						}

						override fun onDownloading(downloadedSize: Long) {
							val percent = (downloadedSize.toFloat() / fileSize * 100).toInt()
							if (prevPercent == percent) return
							val downloadedMB: String = FileManager.byteToMB(downloadedSize)
							val fileSizeMB: String = FileManager.byteToMB(fileSize)

							prevPercent = percent

							setStatus(Status.Downloading, percent.toString(), downloadedMB, fileSizeMB)
						}

						override fun onDownloadEnd(zip: File) {
							log("Download End")
						}

						override fun onAnalyzeStart() {
							log("Analyzing Start")
							setStatus(Status.Analyzing, unishare.title, unishare.producer)
						}

						override fun onAnalyzeSuccess(unipack: Unipack) {
							log("Analyzing Success")
							setStatus(Status.Success, unipack.getInfoText(this@ImportPackByUrlActivity))
						}

						override fun onAnalyzeFail(unipack: Unipack) {
							log("Analyzing Fail")
							Log.err(unipack.ErrorDetail)
							setStatus(Status.Failed, unipack.ErrorDetail)
						}

						override fun onAnalyzeEnd(folder: File) {
							log("Analyzing End")
							delayFinish()
						}

						override fun onException(throwable: Throwable) {
							throwable.printStackTrace()
							setStatus(Status.Exception, throwable.message)
							log("Exception: " + throwable.message)
						}
					})
				} else {
					when (response.code()) {
						404 -> {
							log("404 Not Found")
							setStatus(Status.NotFound)//, "Not Found");
						}
					}
				}
			}

			override fun onFailure(call: Call<UnishareVO?>?, t: Throwable?) {
				log("server error")
				setStatus(Status.Failed, "server error\n" + t?.message)
			}
		})
	}

	private enum class Status(var titleStringId: Int, var ongoing: Boolean = true) {
		Prepare(string.wait_a_sec),
		GetInfo(string.wait_a_sec),
		DownloadStart(string.downloadWaiting),
		Downloading(string.downloading),
		Analyzing(string.analyzing),
		Success(string.success, false),
		Failed(string.failed, false),
		Exception(string.exceptionOccurred, false),
		NotFound(string.unipackNotFound, false);
	}

	@SuppressLint("SetTextI18n")
	private fun setStatus(status: Status, vararg args: String?) {

		var title = 0
		var message = ""
		var notificationTitle = ""
		var notificationText = ""
		when (status) {
			Status.Prepare -> {
				title = status.titleStringId
				message = identifyCode
				notificationTitle = identifyCode
				notificationText = lang(status.titleStringId)
			}
			Status.GetInfo -> {
				title = status.titleStringId
				message = identifyCode
				notificationTitle = identifyCode
				notificationText = lang(status.titleStringId)
			}
			Status.DownloadStart -> {
				val unishareTitle = args[0]!!
				val unishareProducer = args[1]!!

				title = status.titleStringId
				message = "#${code}\n${unishareTitle}\n${unishareProducer}"
				notificationTitle = identifyCode
				notificationText = lang(status.titleStringId)

				notificationBuilder.apply {
					setProgress(100, 0, true)
				}
			}
			Status.Downloading -> {
				val percent = args[0]
				val downloadedMB = args[1]
				val fileSizeMB = args[2]

				title = status.titleStringId
				message = "${percent}%\n${downloadedMB} / $fileSizeMB MB"
				notificationTitle = identifyCode
				notificationText = "${percent}%\n${downloadedMB} / $fileSizeMB MB"

				notificationBuilder.apply {
					setProgress(100, percent!!.toInt(), false)
				}
			}
			Status.Analyzing -> {
				val unishareTitle = args[0]!!
				val unishareProducer = args[1]!!

				title = status.titleStringId
				message = "#${code}\n${unishareTitle}\n${unishareProducer}"
				notificationTitle = identifyCode
				notificationText = lang(status.titleStringId)

				notificationBuilder.apply {
					setProgress(100, 0, true)
				}
			}
			Status.Success -> {
				val unipackToString = args[0]!!

				title = status.titleStringId
				message = unipackToString
				notificationTitle = identifyCode
				notificationText = lang(status.titleStringId)

				notificationBuilder.apply {
					setProgress(0, 0, false)
				}
			}
			Status.Failed -> {
				val errorMsg = args[0]!!

				title = status.titleStringId
				message = errorMsg
				notificationTitle = identifyCode
				notificationText = lang(status.titleStringId)

				notificationBuilder.apply {
					setProgress(0, 0, false)
				}
			}
			Status.Exception -> {
				val throwableMessage = args[0]!!

				title = status.titleStringId
				message = throwableMessage
				notificationTitle = identifyCode
				notificationText = lang(status.titleStringId)

				notificationBuilder.apply {
					setProgress(0, 0, false)
				}
			}
			Status.NotFound -> {
				title = status.titleStringId
				message = identifyCode
				notificationTitle = identifyCode
				notificationText = lang(status.titleStringId)
			}
		}
		TV_title.setText(title)
		TV_message.text = message
		notificationBuilder.apply {
			setContentTitle(notificationTitle)
			setContentText(notificationText)
			setOngoing(status.ongoing)
		}
		NotificationManager.getManager(this).notify(notificationId, notificationBuilder.build())
	}

	private fun log(msg: String) {
		TV_info.append(msg + "\n")
		Log.download(msg)
	}

	private fun delayFinish() {
		log("delayFinish()")
		Handler().postDelayed({
			finish()
			overridePendingTransition(anim.activity_in, anim.activity_out)
		}, 3000)
	}
}