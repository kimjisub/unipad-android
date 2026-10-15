package com.kimjisub.launchpad.tool

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.analytics.PackImportReport
import com.kimjisub.launchpad.manager.FileManager
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

	private val notification = PackInstallNotification(context)

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

				val byteSize = if (unipack.errorDetail == null) unipack.getByteSize() else 0L

				usage.succeeded()
				withContext(Dispatchers.Main) { onImportComplete(targetFolder, unipack, byteSize) }
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
		notification.showProgress(fileName, R.string.importing, 0, indeterminate = false)

		onEventListener.onImportStart()
	}

	private fun onImportComplete(folder: File, unipack: UniPack, byteSize: Long) {
		notification.showResult(fileName, R.string.success)

		onEventListener.onImportComplete(folder, unipack, byteSize)
	}

	private fun onException(throwable: Throwable) {
		notification.showResult(fileName, R.string.failed)

		onEventListener.onException(throwable)
	}

	interface OnEventListener {
		fun onImportStart()

		fun onImportComplete(folder: File, unipack: UniPack, byteSize: Long)

		fun onException(throwable: Throwable)
	}

	class UniPackCriticalErrorException(message: String) : Exception(message)
}