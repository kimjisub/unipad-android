package com.kimjisub.launchpad.tool

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.activity.MainActivity
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.NotificationManager
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.lingala.zip4j.progress.ProgressMonitor
import java.io.File
import java.util.zip.ZipInputStream

class UniPackImporter(
	private var context: Context,
	private var uri: Uri,
	workspace: File,
	private var onEventListener: OnEventListener,
) {
	private val fileName = DocumentFile.fromSingleUri(context, uri)?.name
	private val zipNameWithoutExt = fileName?.split('.')?.first() ?: "unknown"
	private val targetFolder: File = FileManager.makeNextPath(workspace, zipNameWithoutExt, "/")

	private val notificationId = (Math.random() * Integer.MAX_VALUE).toInt()
	private val notificationManager = NotificationManager.getManager(context)
	private val notificationBuilder: NotificationCompat.Builder by lazy {
		val builder = NotificationCompat.Builder(context, NotificationManager.Channel.Download.name)
		builder.apply {
			setAutoCancel(true)
			setSmallIcon(R.mipmap.ic_launcher)

			val intent = Intent(context, MainActivity::class.java)
			intent.action = Intent.ACTION_MAIN
			intent.addCategory(Intent.CATEGORY_LAUNCHER)
			intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
			val pIntent: PendingIntent =
				PendingIntent.getActivity(
					context,
					1,
					intent,
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
						PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
					} else {
						PendingIntent.FLAG_UPDATE_CURRENT
					}
				)
			setContentIntent(pIntent)
		}
		builder
	}

	init {
		CoroutineScope(Dispatchers.IO).launch {
			try {
				withContext(Dispatchers.Main) { onImportStart() }

				targetFolder.mkdir()

				context.contentResolver.openInputStream(uri)?.use { inputStream ->
					ZipInputStream(inputStream).use { input ->
						var entry = input.nextEntry
						while (entry != null) {
							File(targetFolder.path, entry.name).apply {
								if (entry.isDirectory) {
									mkdirs()
								} else {
									outputStream().use { output ->
										input.copyTo(output)
									}
								}
							}
							entry = input.nextEntry
						}
					}
				}

				val unipack = UniPackFolder(targetFolder).load()
				if (unipack.criticalError) {
					Log.err(unipack.errorDetail!!)
					FileManager.deleteDirectory(targetFolder)
					throw UniPackCriticalErrorException(unipack.errorDetail!!)
				}

				withContext(Dispatchers.Main) { onImportComplete(targetFolder, unipack) }
			} catch (e: Exception) {
				e.printStackTrace()
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

	private fun onImportProgress(processMonitor: ProgressMonitor) {
		notificationBuilder.apply {
			setProgress(100, processMonitor.percentDone, false)
			setOngoing(false)
		}

		onEventListener.onImportProgress(processMonitor)
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

		fun onImportProgress(processMonitor: ProgressMonitor)

		fun onImportComplete(folder: File, unipack: UniPack)

		fun onException(throwable: Throwable)
	}

	class UniPackCriticalErrorException(message: String) : Exception(message)

	companion object {
		const val PROGRESS_INTERVAL = 10L
	}
}