package com.kimjisub.launchpad.tool

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.SplashActivity
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.NotificationManager
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.coroutines.*
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.progress.ProgressMonitor
import java.io.File

class UniPackImporter(
	private var context: Context,
	private var unipackFile: File,
	workspace: File,
	private var onEventListener: OnEventListener
) {
	private val zipNameWithoutExt = unipackFile.name.split('.').first()
	private val targetFolder: File = FileManager.makeNextPath(workspace, zipNameWithoutExt, "/")

	private val notificationId = (Math.random() * Integer.MAX_VALUE).toInt()
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
			val pIntent: PendingIntent =
				PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
			setContentIntent(pIntent)
		}
		builder
	}

	init {
		CoroutineScope(Dispatchers.IO).launch {
			try {
				withContext(Dispatchers.Main) { onImportStart() }

				val zip = ZipFile(unipackFile)
				zip.isRunInThread = true
				zip.extractAll(targetFolder.path)

				while (zip.progressMonitor.state != ProgressMonitor.State.READY) {
					withContext(Dispatchers.Main) {
						onImportProgress(zip.progressMonitor)
					}
					delay(PROGRESS_INTERVAL)
				}


				val unipack = UniPackFolder(targetFolder).loadDetail()
				if (unipack.criticalError) {
					Log.err(unipack.errorDetail!!)
					FileManager.deleteDirectory(targetFolder)
					throw UniPackCriticalErrorException(unipack.errorDetail!!)
				}

				withContext(Dispatchers.Main) { onInstallComplete(targetFolder, unipack) }
			} catch (e: Exception) {
				e.printStackTrace()
				withContext(Dispatchers.Main) { onException(e) }
				FileManager.deleteDirectory(targetFolder)
			}
		}
	}

	private fun onImportStart() {
		notificationBuilder.apply {
			setContentTitle(unipackFile.name)
			setContentText(context.getString(R.string.importing))
			setProgress(100, 0, false)
			setOngoing(true)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		onEventListener?.onImportStart(unipackFile)
	}

	private fun onImportProgress(processMonitor: ProgressMonitor) {
		notificationBuilder.apply {
			setProgress(100, processMonitor.percentDone, false)
			setOngoing(false)
		}

		onEventListener?.onImportProgress(processMonitor)
	}

	private fun onInstallComplete(folder: File, unipack: UniPack) {
		notificationBuilder.apply {
			setContentTitle(this@UniPackImporter.unipackFile.name)
			setContentText(context.getString(R.string.success))
			setProgress(0, 0, false)
			setOngoing(false)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		onEventListener?.onImportComplete(folder, unipack)
	}

	private fun onException(throwable: Throwable) {
		notificationBuilder.apply {
			setContentTitle(unipackFile.name)
			setContentText(context.getString(R.string.downloadWaiting))
			setProgress(0, 0, false)
			setOngoing(false)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		onEventListener?.onException(throwable)
	}

	interface OnEventListener {

		fun onImportStart(zip: File)

		fun onImportProgress(processMonitor: ProgressMonitor)

		fun onImportComplete(folder: File, unipack: UniPack)

		fun onException(throwable: Throwable)
	}

	class UniPackCriticalErrorException(message: String) : Exception(message)

	companion object {
		const val PROGRESS_INTERVAL = 10L
	}
}