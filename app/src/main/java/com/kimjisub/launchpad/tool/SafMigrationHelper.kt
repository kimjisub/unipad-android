package com.kimjisub.launchpad.tool

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.manager.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import java.io.File

/**
 * Moves or copies packs between the app folder and a SAF tree.
 *
 * Two rules the transfer methods follow, both learned from lost packs:
 * - a name collision is a *skip*: nothing was written, so the source is never deleted, even in
 *   move mode (it used to be, and a stale copy at the destination was all that survived);
 * - a copy that throws half-way removes the partial destination before the error is recorded,
 *   otherwise the next attempt sees "exists" and skips the pack forever.
 */
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
				} else {
					try {
						FileManager.copyDocumentTreeToFile(context, folder, dest)
					} catch (e: Exception) {
						FileManager.deleteDirectory(dest)
						throw e
					}
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
				} else {
					try {
						FileManager.copyFileToDocumentTree(context, folder, targetTreeDoc)
					} catch (e: Exception) {
						// The directory did not exist before this attempt, so removing it only drops
						// the partial copy.
						runCatching { targetTreeDoc.findFile(folder.name)?.takeIf { it.isDirectory }?.delete() }
						throw e
					}
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
				} else {
					// A unique temp name: zip4j *appends* to an existing archive, so a leftover from a
					// crashed run produced a backup with two copies of every entry.
					val tempZip = File.createTempFile("transfer-", ".zip", cacheDir).also { it.delete() }
					try {
						ZipFile(tempZip).addFolder(folder)
						val newDoc = targetTreeDoc.createFile("application/zip", zipName)
						if (newDoc != null) {
							try {
								context.contentResolver.openOutputStream(newDoc.uri)?.use { out ->
									tempZip.inputStream().use { it.copyTo(out) }
								} ?: throw java.io.IOException("openOutputStream returned null")
							} catch (e: Exception) {
								runCatching { newDoc.delete() }
								throw e
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
				} else {
					val tempZip = File.createTempFile("transfer-", ".zip", cacheDir)
					try {
						context.contentResolver.openInputStream(doc.uri)?.use { input ->
							tempZip.outputStream().use { input.copyTo(it) }
						} ?: throw java.io.IOException("openInputStream returned null")
						try {
							ZipFile(tempZip).extractAll(dest.absolutePath)
						} catch (e: Exception) {
							FileManager.deleteDirectory(dest)
							throw e
						}
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
				} else {
					try {
						FileManager.copyDirectory(folder, dest)
					} catch (e: Exception) {
						FileManager.deleteDirectory(dest)
						throw e
					}
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
