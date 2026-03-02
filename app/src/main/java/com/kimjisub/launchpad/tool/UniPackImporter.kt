package com.kimjisub.launchpad.tool

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.SplashActivity
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

class UniPackImporter(
	private var context: Context,
	private var uri: Uri,
	workspace: File,
	private var onEventListener: OnEventListener,
	scope: CoroutineScope,
) {
	private val fileName = DocumentFile.fromSingleUri(context, uri)?.name
	private val zipNameWithoutExt = fileName?.split('.')?.first() ?: "unknown"
	private val targetFolder: File = FileManager.makeNextPath(workspace, zipNameWithoutExt, "/")

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
			// minSdk 29+ always requires FLAG_IMMUTABLE (introduced in API 23)
			val pIntent: PendingIntent =
				PendingIntent.getActivity(
					context,
					1,
					intent,
					PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
				)
			setContentIntent(pIntent)
		}
		builder
	}

	init {
		scope.launch(Dispatchers.IO) {
			try {
				withContext(Dispatchers.Main) { onImportStart() }

				targetFolder.mkdir()

				val tempZip = File.createTempFile("unipack_import_", ".zip", context.cacheDir)
				try {
					context.contentResolver.openInputStream(uri)?.use { inputStream ->
						tempZip.outputStream().use { output ->
							inputStream.copyTo(output)
						}
					}
					ZipFile(tempZip).use { zip ->
						zip.extractAll(targetFolder.path)
					}
				} finally {
					tempZip.delete()
				}

				val unipack = UniPackFolder(targetFolder).load()
				if (unipack.criticalError) {
					val errorMsg = unipack.errorDetail ?: "Unknown error"
					Log.err(errorMsg)
					FileManager.deleteDirectory(targetFolder)
					throw UniPackCriticalErrorException(errorMsg)
				}

				withContext(Dispatchers.Main) { onImportComplete(targetFolder, unipack) }
			} catch (e: Exception) {
				Log.err("Import failed", e)
				withContext(Dispatchers.Main) { onException(e) }
				FileManager.deleteDirectory(targetFolder)
			}
		}
	}

	private fun onImportStart() {
		notificationBuilder.apply {
			setContentTitle(fileName)
			setContentText(context.getString(R.string.importing))
			setProgress(100, 0, false)
			setOngoing(true)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		onEventListener.onImportStart()
	}

	private fun onImportComplete(folder: File, unipack: UniPack) {
		notificationBuilder.apply {
			setContentTitle(fileName)
			setContentText(context.getString(R.string.success))
			setProgress(0, 0, false)
			setOngoing(false)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		onEventListener.onImportComplete(folder, unipack)
	}

	private fun onException(throwable: Throwable) {
		notificationBuilder.apply {
			setContentTitle(fileName)
			setContentText(context.getString(R.string.downloadWaiting))
			setProgress(0, 0, false)
			setOngoing(false)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		onEventListener.onException(throwable)
	}

	interface OnEventListener {
		fun onImportStart()

		fun onImportComplete(folder: File, unipack: UniPack)

		fun onException(throwable: Throwable)
	}

	class UniPackCriticalErrorException(message: String) : Exception(message)
}