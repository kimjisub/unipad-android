package com.kimjisub.launchpad.tool

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.analytics.PackImportReport
import com.kimjisub.launchpad.activity.SplashActivity
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.NotificationManager
import com.kimjisub.launchpad.manager.PackStaging
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
	/** Resolved on the import's IO thread: listing workspaces creates folders. */
	workspace: () -> File,
	private var onEventListener: OnEventListener,
	private val usage: PackImportReport,
	scope: CoroutineScope,
) {
	private var fileName: String? = null

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
			// A download or another import of the same name may run at the same time, so only the
			// folders created here are written to or deleted. The pack is unpacked and checked in
			// its own staging folder and appears in the list only once complete.
			var staged: File? = null
			var claimedFolder: File? = null
			try {
				fileName = DocumentFile.fromSingleUri(context, uri)?.name
				val zipNameWithoutExt = fileName?.split('.')?.first() ?: "unknown"
				withContext(Dispatchers.Main) { onImportStart() }

				val targetWorkspace = workspace()
				val stagedFolder = PackStaging.create(targetWorkspace)
				staged = stagedFolder

				val tempZip = File.createTempFile("unipack_import_", ".zip", context.cacheDir)
				try {
					context.contentResolver.openInputStream(uri)?.use { inputStream ->
						tempZip.outputStream().use { output ->
							inputStream.copyTo(output)
						}
					}
					ZipFile(tempZip).use { zip ->
						zip.extractAll(stagedFolder.path)
					}
				} finally {
					tempZip.delete()
				}
				FileManager.removeDoubleFolder(stagedFolder.path)

				val checked = UniPackFolder(stagedFolder).load()
				if (checked.criticalError) {
					val errorMsg = checked.errorDetail ?: "Unknown error"
					Log.err(errorMsg)
					throw UniPackCriticalErrorException(errorMsg)
				}

				val targetFolder = FileManager.moveToNextFolder(stagedFolder, targetWorkspace, zipNameWithoutExt)
				claimedFolder = targetFolder
				val unipack = UniPackFolder(targetFolder).load()

				usage.succeeded()
				withContext(Dispatchers.Main) { onImportComplete(targetFolder, unipack) }
			} catch (e: Exception) {
				Log.err("Import failed", e)
				usage.failed(e)
				withContext(Dispatchers.Main) { onException(e) }
				claimedFolder?.let(FileManager::deleteDirectory)
			} finally {
				staged?.let(PackStaging::discard)
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