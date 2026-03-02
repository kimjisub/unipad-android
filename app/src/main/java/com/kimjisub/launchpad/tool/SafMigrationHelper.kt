package com.kimjisub.launchpad.tool

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.manager.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import java.io.File

class SafMigrationHelper(
	private val context: Context,
) {

	data class TransferResult(
		val total: Int,
		val transferred: Int,
		val skipped: Int,
		val failed: Int,
		val errors: List<String>,
	)

	fun getSubfolders(treeRoot: DocumentFile): List<DocumentFile> =
		treeRoot.listFiles().filter { it.isDirectory }

	suspend fun transferSafToFile(
		folders: List<DocumentFile>,
		targetDir: File,
		deleteSource: Boolean,
		onProgress: (current: Int, total: Int, name: String) -> Unit,
	): TransferResult = withContext(Dispatchers.IO) {
		if (!targetDir.exists()) targetDir.mkdirs()
		if (!targetDir.canWrite()) {
			return@withContext TransferResult(folders.size, 0, 0, folders.size, listOf("Target directory not writable: ${targetDir.path}"))
		}

		var transferred = 0
		var skipped = 0
		var failed = 0
		val errors = mutableListOf<String>()

		folders.forEachIndexed { index, folder ->
			val name = folder.name ?: "unknown"
			withContext(Dispatchers.Main) {
				onProgress(index + 1, folders.size, name)
			}

			try {
				val dest = File(targetDir, name)
				if (dest.exists()) {
					skipped++
					if (deleteSource) folder.delete()
				} else {
					FileManager.copyDocumentTreeToFile(context, folder, dest)
					transferred++
					if (deleteSource) folder.delete()
				}
			} catch (e: Exception) {
				failed++
				errors.add("$name: ${e.message}")
				Log.err("SAF transfer failed for $name", e)
			}
		}

		TransferResult(folders.size, transferred, skipped, failed, errors)
	}

	suspend fun transferFileToSaf(
		sourceFiles: List<File>,
		targetTreeDoc: DocumentFile,
		deleteSource: Boolean,
		onProgress: (current: Int, total: Int, name: String) -> Unit,
	): TransferResult = withContext(Dispatchers.IO) {
		var transferred = 0
		var skipped = 0
		var failed = 0
		val errors = mutableListOf<String>()

		sourceFiles.forEachIndexed { index, folder ->
			withContext(Dispatchers.Main) {
				onProgress(index + 1, sourceFiles.size, folder.name)
			}

			try {
				val existing = targetTreeDoc.findFile(folder.name)
				if (existing != null && existing.isDirectory) {
					skipped++
					if (deleteSource) folder.deleteRecursively()
				} else {
					FileManager.copyFileToDocumentTree(context, folder, targetTreeDoc)
					transferred++
					if (deleteSource) folder.deleteRecursively()
				}
			} catch (e: Exception) {
				failed++
				errors.add("${folder.name}: ${e.message}")
				Log.err("SAF transfer failed for ${folder.name}", e)
			}
		}

		TransferResult(sourceFiles.size, transferred, skipped, failed, errors)
	}

	suspend fun transferFileToSafZip(
		sourceFiles: List<File>,
		targetTreeDoc: DocumentFile,
		deleteSource: Boolean,
		onProgress: (current: Int, total: Int, name: String) -> Unit,
	): TransferResult = withContext(Dispatchers.IO) {
		var transferred = 0
		var skipped = 0
		var failed = 0
		val errors = mutableListOf<String>()
		val cacheDir = context.cacheDir

		sourceFiles.forEachIndexed { index, folder ->
			withContext(Dispatchers.Main) {
				onProgress(index + 1, sourceFiles.size, folder.name)
			}

			try {
				val zipName = "${folder.name}.zip"
				val existing = targetTreeDoc.findFile(zipName)
				if (existing != null) {
					skipped++
					if (deleteSource) folder.deleteRecursively()
				} else {
					val tempZip = File(cacheDir, zipName)
					try {
						ZipFile(tempZip).addFolder(folder)
						val newDoc = targetTreeDoc.createFile("application/zip", zipName)
						if (newDoc != null) {
							context.contentResolver.openOutputStream(newDoc.uri)?.use { out ->
								tempZip.inputStream().use { it.copyTo(out) }
							}
							transferred++
							if (deleteSource) folder.deleteRecursively()
						} else {
							failed++
							errors.add("${folder.name}: Failed to create ZIP in SAF")
						}
					} finally {
						tempZip.delete()
					}
				}
			} catch (e: Exception) {
				failed++
				errors.add("${folder.name}: ${e.message}")
				Log.err("SAF ZIP transfer failed for ${folder.name}", e)
			}
		}

		TransferResult(sourceFiles.size, transferred, skipped, failed, errors)
	}

	suspend fun transferSafZipToFile(
		zipDocFiles: List<DocumentFile>,
		targetDir: File,
		deleteSource: Boolean,
		onProgress: (current: Int, total: Int, name: String) -> Unit,
	): TransferResult = withContext(Dispatchers.IO) {
		if (!targetDir.exists()) targetDir.mkdirs()
		if (!targetDir.canWrite()) {
			return@withContext TransferResult(zipDocFiles.size, 0, 0, zipDocFiles.size, listOf("Target directory not writable: ${targetDir.path}"))
		}

		var transferred = 0
		var skipped = 0
		var failed = 0
		val errors = mutableListOf<String>()
		val cacheDir = context.cacheDir

		zipDocFiles.forEachIndexed { index, doc ->
			val zipName = doc.name ?: "unknown.zip"
			val folderName = zipName.removeSuffix(".zip")
			withContext(Dispatchers.Main) {
				onProgress(index + 1, zipDocFiles.size, folderName)
			}

			try {
				val dest = File(targetDir, folderName)
				if (dest.exists()) {
					skipped++
					if (deleteSource) doc.delete()
				} else {
					val tempZip = File(cacheDir, zipName)
					try {
						context.contentResolver.openInputStream(doc.uri)?.use { input ->
							tempZip.outputStream().use { input.copyTo(it) }
						}
						ZipFile(tempZip).extractAll(dest.absolutePath)
						transferred++
						if (deleteSource) doc.delete()
					} finally {
						tempZip.delete()
					}
				}
			} catch (e: Exception) {
				failed++
				errors.add("$folderName: ${e.message}")
				Log.err("SAF ZIP extract failed for $folderName", e)
			}
		}

		TransferResult(zipDocFiles.size, transferred, skipped, failed, errors)
	}

	suspend fun transferFileToFile(
		sourceFiles: List<File>,
		targetDir: File,
		deleteSource: Boolean,
		onProgress: (current: Int, total: Int, name: String) -> Unit,
	): TransferResult = withContext(Dispatchers.IO) {
		if (!targetDir.exists()) targetDir.mkdirs()

		var transferred = 0
		var skipped = 0
		var failed = 0
		val errors = mutableListOf<String>()

		sourceFiles.forEachIndexed { index, folder ->
			withContext(Dispatchers.Main) {
				onProgress(index + 1, sourceFiles.size, folder.name)
			}

			try {
				val dest = File(targetDir, folder.name)
				if (dest.exists()) {
					skipped++
					if (deleteSource) folder.deleteRecursively()
				} else {
					FileManager.copyDirectory(folder, dest)
					transferred++
					if (deleteSource) folder.deleteRecursively()
				}
			} catch (e: Exception) {
				failed++
				errors.add("${folder.name}: ${e.message}")
				Log.err("File transfer failed for ${folder.name}", e)
			}
		}

		TransferResult(sourceFiles.size, transferred, skipped, failed, errors)
	}
}
