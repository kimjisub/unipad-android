package com.kimjisub.launchpad.activity

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.kimjisub.launchpad.R.anim
import com.kimjisub.launchpad.ui.theme.Background1
import com.kimjisub.launchpad.ui.theme.Gray1
import com.kimjisub.launchpad.ui.theme.OverlayLight
import com.kimjisub.launchpad.R.string
import com.kimjisub.launchpad.api.unipad.UniPadApi.service
import com.kimjisub.launchpad.api.unipad.vo.UnishareVO
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.tool.Log
import com.kimjisub.launchpad.tool.UniPackDownloader
import com.kimjisub.launchpad.unipack.UniPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ImportPackByUrlActivity : BaseActivity() {
	companion object {
		private const val INSTALL_COMPLETE_DELAY_MS = 3000L

		/** Shown with the question so the person can see where the file comes from. */
		private const val UNISHARE_HOST = "api.unipad.io"
	}

	private val titleText = mutableStateOf("")
	private val messageText = mutableStateOf("")
	private val infoText = mutableStateOf("")

	/**
	 * The pack a deep link wants to install, held while the user decides.
	 *
	 * A `unipad://` link used to go straight from the metadata response into the
	 * download. Anything that can open a link on this device could therefore make
	 * the app fetch and unpack a file with no say from the person holding it. The
	 * metadata call already ran by this point, so the question can name the pack
	 * and its producer instead of asking about an anonymous download.
	 */
	private val pendingInstall = mutableStateOf<UnishareVO?>(null)

	private val code: String? by lazy { intent?.data?.getQueryParameter("code") }

	private var identifyCode = ""

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		titleText.value = getString(string.wait_a_sec)

		log("code: $code")
		identifyCode = getString(string.import_pack_code_format, code.orEmpty())
		messageText.value = identifyCode

		setContent {
			val title by titleText
			val message by messageText
			val info by infoText
			val pending by pendingInstall
			ImportPackScreen(
				titleText = title,
				messageText = message,
				infoText = info,
				confirming = pending != null,
				onAccept = { pending?.let { startInstall(it) } },
				onCancel = { finish() },
			)
		}

		val requestCode = code ?: return
		lifecycleScope.launch {
			try {
				val response = withContext(Dispatchers.IO) {
					service.getUnishare(requestCode)
				}
				if (response.isSuccessful) {
					val unishare = response.body() ?: return@launch
					identifyCode = getString(string.import_pack_title_format, unishare.title, unishare._id)
					log("title: ${unishare.title}")
					log("producerName: ${unishare.producer}")

					// Ask first. The download starts from onAccept, not from here.
					titleText.value = unishare.title.orEmpty()
					messageText.value = unishare.producer.orEmpty()
					infoText.value = getString(string.import_pack_confirm_source, UNISHARE_HOST)
					pendingInstall.value = unishare
				} else {
					when (response.code()) {
						404 -> {
							log("404 Not Found")
							titleText.value = getString(string.unipackNotFound)
							messageText.value = identifyCode
						}
					}
				}
			} catch (e: IOException) {
				log("server error")
				Log.err("Unishare API call failed", e)
				titleText.value = getString(string.failed)
				messageText.value = getString(string.server_error_format, e.message.orEmpty())
			}
		}
	}

	fun startInstall(unishare: UnishareVO) {
		pendingInstall.value = null
		val packTitle = getString(string.import_pack_title_format, unishare.title, unishare._id)
		UniPackDownloader(
			context = this,
			title = packTitle,
			url = "https://api.unipad.io/unishare/${unishare._id}/download",
			workspace = ws.downloadWorkspace.file,
			folderName = packTitle,
			listener = object : UniPackDownloader.Listener {
				override fun onInstallStart() {
					log("Install start")
					titleText.value = getString(string.downloadWaiting)
					messageText.value = getString(string.import_pack_info_format, code.orEmpty(), unishare.title, unishare.producer)
				}

				override fun onGetFileSize(
					fileSize: Long,
					contentLength: Long,
					preKnownFileSize: Long,
				) {
					log("fileSize: $contentLength → $fileSize")
				}

				override fun onDownloadProgress(
					percent: Int,
					downloadedSize: Long,
					fileSize: Long,
				) {
				}

				override fun onDownloadProgressPercent(
					percent: Int,
					downloadedSize: Long,
					fileSize: Long,
				) {
					val downloadedMB: String = FileManager.byteToMB(downloadedSize)
					val fileSizeMB: String = FileManager.byteToMB(fileSize)

					titleText.value = getString(string.downloading)
					messageText.value = getString(string.download_progress_format, percent, downloadedMB, fileSizeMB)
				}

				override fun onImportStart(zip: File) {
					log("Import Start")
					titleText.value = getString(string.importing)
					messageText.value = getString(string.import_pack_info_format, code.orEmpty(), unishare.title, unishare.producer)
				}

				override fun onInstallComplete(folder: File, unipack: UniPack) {
					log("Install Success")
					titleText.value = getString(string.success)
					messageText.value = unipack.infoToString(this@ImportPackByUrlActivity)
					delayFinish()
				}

				override fun onException(throwable: Throwable) {
					log("Exception: ${throwable.message}")
					Log.err("ImportPackByUrl download failed", throwable)
					titleText.value = getString(string.exceptionOccurred)
					messageText.value = throwable.message ?: ""
				}
			},
			scope = lifecycleScope,
		)
	}

	private fun log(msg: String) {
		infoText.value += msg + "\n"
		Log.download(msg)
	}

	private fun delayFinish() {
		log("delayFinish()")
		lifecycleScope.launch {
			delay(INSTALL_COMPLETE_DELAY_MS)

			finish()
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
				overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, anim.activity_in, anim.activity_out)
			} else {
				@Suppress("DEPRECATION")
				overridePendingTransition(anim.activity_in, anim.activity_out)
			}
		}
	}
}

private val grayColor = Gray1

@Composable
private fun ImportPackScreen(
	titleText: String,
	messageText: String,
	infoText: String,
	confirming: Boolean = false,
	onAccept: () -> Unit = {},
	onCancel: () -> Unit = {},
) {
	Box(
		modifier = Modifier
			.fillMaxSize()
			.background(OverlayLight),
		contentAlignment = Alignment.Center,
	) {
		Box(
			modifier = Modifier
				.width(300.dp)
				.height(200.dp)
				.background(Background1),
		) {
			if (infoText.isNotEmpty()) {
				Text(
					text = infoText,
					color = grayColor,
					fontSize = 12.sp,
					modifier = Modifier
						.align(Alignment.TopStart)
						.alpha(0.5f),
				)
			}

			Column(
				modifier = Modifier
					.align(Alignment.Center)
					.verticalScroll(rememberScrollState()),
				horizontalAlignment = Alignment.CenterHorizontally,
			) {
				Text(
					text = titleText,
					color = grayColor,
					fontSize = 25.sp,
				)
				Text(
					text = messageText,
					color = grayColor,
					fontSize = 18.sp,
					textAlign = TextAlign.Center,
				)

				if (confirming) {
					Text(
						text = stringResource(string.import_pack_confirm),
						color = grayColor,
						fontSize = 14.sp,
						textAlign = TextAlign.Center,
						modifier = Modifier.padding(top = 12.dp),
					)
					Row(
						modifier = Modifier.padding(top = 8.dp),
						horizontalArrangement = Arrangement.spacedBy(8.dp),
					) {
						TextButton(onClick = onCancel) {
							Text(text = stringResource(string.cancel), color = grayColor)
						}
						TextButton(onClick = onAccept) {
							Text(text = stringResource(string.accept), color = grayColor)
						}
					}
				}
			}
		}
	}
}
