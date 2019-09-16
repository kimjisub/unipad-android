package com.kimjisub.launchpad.activity

import android.annotation.SuppressLint
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.os.Handler
import androidx.core.app.NotificationCompat.Builder
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
	private var downloadedSize: Long = 0
	private var identifyCode = ""
	private var errorMsg = ""
	private var unishare: UnishareVO? = null
	private var unipack: Unipack? = null
	private var throwable: Throwable? = null
	private var prevPercent = 0

	// Notification /////////////////////////////////////////////////////////////////////////////////////////

	private val notificationBuilder: Builder by lazy {
		val builder = Builder(this)
		builder.apply {
			setAutoCancel(true)
			setSmallIcon(mipmap.ic_launcher)
			if (VERSION.SDK_INT >= VERSION_CODES.O)
				setChannelId(Channel.DOWNLOAD)
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
			override fun onResponse(call: Call<UnishareVO?>, response: Response<UnishareVO?>) {
				if (response.isSuccessful) {
					unishare = response.body()
					log("title: " + unishare!!.title)
					log("producerName: " + unishare!!.producer)
					identifyCode = unishare!!.title + " #" + unishare!!._id
					setStatus(Status.GetInfo)
					UnishareDownloader(unishare!!, F_UniPackRootExt, object : UnishareDownloadListener {
						override fun onDownloadStart() {
							log("Download start")
							setStatus(Status.DownloadStart)
						}

						override fun onGetFileSize(contentSize: Long, realSize: Long) {
							fileSize = realSize
							log("fileSize: $contentSize → $realSize")
						}

						override fun onDownloading(downloadedSize_: Long) {
							downloadedSize = downloadedSize_
							setStatus(Status.Downloading)
						}

						override fun onDownloadEnd(zip: File) {
							log("Download End")
						}

						override fun onAnalyzeStart() {
							log("Analyzing Start")
							setStatus(Status.Analyzing)
						}

						override fun onAnalyzeSuccess(unipack_: Unipack) {
							log("Analyzing Success")
							unipack = unipack_
							setStatus(Status.Success)
						}

						override fun onAnalyzeFail(unipack: Unipack) {
							log("Analyzing Fail")
							Log.err(unipack.ErrorDetail)
							errorMsg = unipack.ErrorDetail
							setStatus(Status.Failed)
						}

						override fun onAnalyzeEnd(folder: File) {
							log("Analyzing End")
							delayFinish()
						}

						override fun onException(throwable_: Throwable) {
							throwable = throwable_
							throwable!!.printStackTrace()
							setStatus(Status.Exception)
							log("Exception: " + throwable!!.message)
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
				setStatus(Status.Failed)//, "server error\n" + t.getMessage());
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
	private fun setStatus(status: Status) {
		when (status) {
			Status.Prepare -> {
				TV_title.setText(status.titleStringId)
				TV_message.text = identifyCode
				notificationBuilder.apply {
					setContentText(identifyCode)
					setContentText(lang(status.titleStringId))
				}
			}
			Status.GetInfo -> {
				TV_title.setText(status.titleStringId)
				TV_message.text = identifyCode
				notificationBuilder.apply {
					setContentTitle(identifyCode)
					setContentText(lang(status.titleStringId))
				}
			}
			Status.DownloadStart -> {
				TV_title.setText(status.titleStringId)
				TV_message.text = "#${code}\n${unishare!!.title}\n${unishare!!.producer}"

				notificationBuilder.apply {
					setContentText(lang(status.titleStringId))
					setProgress(100, 0, true)
				}
			}
			Status.Downloading -> {
				val percent = (downloadedSize.toFloat() / fileSize * 100).toInt()
				if (prevPercent == percent) return

				val downloadedSizeMB: String = FileManager.byteToMB(downloadedSize.toFloat())
				val fileSizeMB: String = FileManager.byteToMB(fileSize.toFloat())

				prevPercent = percent
				TV_title.setText(status.titleStringId)
				TV_message.text = "${percent}%\n${downloadedSizeMB} / ${fileSizeMB} MB"
				notificationBuilder.apply {
					setContentTitle(identifyCode)
					setContentText(TV_message.text)
					setProgress(100, percent, false)
				}
			}
			Status.Analyzing -> {
				TV_title.setText(status.titleStringId)
				TV_message.text = "#${code}\n${unishare!!.title}\n${unishare!!.producer}"
				notificationBuilder.setContentTitle(identifyCode)
				notificationBuilder.setContentText(lang(status.titleStringId))
				notificationBuilder.setProgress(100, 0, true)
			}
			Status.Success -> {
				TV_title.setText(status.titleStringId)
				TV_message.text = unipack!!.getInfoText(this@ImportPackByUrlActivity)
				notificationBuilder.setContentTitle(identifyCode)
				notificationBuilder.setContentText(lang(status.titleStringId))
				notificationBuilder.setProgress(0, 0, false)
			}
			Status.Failed -> {
				TV_title.setText(status.titleStringId)
				TV_message.text = errorMsg
				notificationBuilder.setContentTitle(identifyCode)
				notificationBuilder.setContentText(lang(status.titleStringId))
				notificationBuilder.setProgress(0, 0, false)
			}
			Status.Exception -> {
				TV_title.setText(status.titleStringId)
				TV_message.text = throwable!!.message
				notificationBuilder.setContentTitle(identifyCode)
				notificationBuilder.setContentText(lang(status.titleStringId))
				notificationBuilder.setProgress(0, 0, false)
			}
			Status.NotFound -> {
				TV_title.setText(status.titleStringId)
				TV_message.text = "#${code}\n${unishare!!.title}\n${unishare!!.producer}"
				notificationBuilder.setContentTitle(identifyCode)
				notificationBuilder.setContentText(lang(status.titleStringId))
				notificationBuilder.setProgress(0, 0, false)
			}
		}
		notificationBuilder.setOngoing(status.ongoing)
		NotificationManager.getManager(this@ImportPackByUrlActivity).notify(notificationId, notificationBuilder.build())
	}

	private fun log(msg: String) {
		TV_info.append(msg + "\n")
	}

	private fun delayFinish() {
		log("delayFinish()")
		Handler().postDelayed({
			finish()
			overridePendingTransition(anim.activity_in, anim.activity_out)
		}, 3000)
		//restartApp(this);
	}
}