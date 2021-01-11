package com.kimjisub.launchpad.tool

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.SplashActivity
import com.kimjisub.launchpad.manager.NotificationManager
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.manager.FileManager
import com.kimjisub.manager.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class UniPackImporter(
	private val context: Context,
	private val zip: File,
	workspace: File,
	private val listener: Listener? = null
) {
	private val zipNameWithoutExt = zip.name.split('.').last()
	private val folder: File = FileManager.makeNextPath(workspace, zipNameWithoutExt, "/")

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
			val pIntent:PendingIntent = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT)
			setContentIntent(pIntent)
		}
		builder
	}

	init {
		CoroutineScope(Dispatchers.IO).launch {
			try {
				withContext(Dispatchers.Main) { onImportStart() }

				FileManager.unZipFile(zip.path, folder.path)
				val unipack = UniPack(folder, true)
				if (unipack.criticalError) {
					Log.err(unipack.errorDetail!!)
					throw UniPackCriticalErrorException(unipack.errorDetail!!)
				}

				withContext(Dispatchers.Main) { onInstallComplete(folder, unipack) }
			} catch (e: Exception) {
				e.printStackTrace()
				withContext(Dispatchers.Main) { onException(e) }
				FileManager.deleteDirectory(folder)
			}
		}
	}

	private fun onImportStart() {
		notificationBuilder.apply {
			setContentTitle(zip.name)
			setContentText(context.getString(R.string.importing))
			setProgress(100, 0, true)
			setOngoing(true)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		listener?.onImportStart(zip)
	}

	private fun onInstallComplete(folder: File, unipack: UniPack) {
		notificationBuilder.apply {
			setContentTitle(zip.name)
			setContentText(context.getString(R.string.success))
			setProgress(0, 0, false)
			setOngoing(false)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		listener?.onImportComplete(folder, unipack)
	}

	private fun onException(throwable: Throwable) {
		notificationBuilder.apply {
			setContentTitle(zip.name)
			setContentText(context.getString(R.string.downloadWaiting))
			setProgress(0, 0, false)
			setOngoing(false)
		}
		notificationManager.notify(notificationId, notificationBuilder.build())

		listener?.onException(throwable)
	}

	interface Listener {
		fun onImportStart(zip: File)
		fun onImportComplete(folder: File, unipack: UniPack)

		fun onException(throwable: Throwable)
	}

	class UniPackCriticalErrorException(message: String) : Exception(message)
}