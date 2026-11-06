package com.kimjisub.launchpad.tool

import android.content.ContentResolver
import android.content.Context
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.PackStaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.UnzipParameters
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Moves or copies packs between the app folder and a SAF tree.
 *
 * The rules the transfer methods follow, learned from lost packs and from moves killed half-way:
 * - a name collision is a *skip*: nothing was written, so the source is never deleted, even in
 *   move mode (it used to be, and a stale copy at the destination was all that survived);
 * - a partial copy must never sit under the pack's name, otherwise the next attempt sees "exists"
 *   and skips the pack forever. Into the app's storage a pack is copied in the target's staging
 *   folder ([PackStaging]) and renamed into place once complete; into a backup folder it is written
 *   under a [PARTIAL_PREFIX] name and renamed once complete ([writeBackup]);
 * - a moved pack's source is deleted only after its copy holds the pack's name, and through
 *   [PackStaging.delete], so a kill while deleting never leaves a half-deleted pack in the list;
 * - temporary archives live in the cache's staging folder, which the next start clears.
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
					copyThroughStaging(targetDir, dest) { FileManager.copyDocumentTreeToFile(context, folder, it) }
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
		removePartialBackups(targetTreeDoc)

		sourceFiles.forEachIndexed { index, folder ->
			withContext(Dispatchers.Main) {
				onProgress(index + 1, sourceFiles.size, folder.name)
			}

			try {
				val existing = targetTreeDoc.findFile(folder.name)
				if (existing != null && existing.isDirectory) {
					skipped++
				} else {
					writeBackup(targetTreeDoc, folder.name, partialSuffix = "", create = DocumentFile::createDirectory) { dir ->
						folder.listFiles()?.forEach { FileManager.copyFileToDocumentTree(context, it, dir) }
					}
					if (deleteSource) deleteMovedSource(folder)
					transferred++
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
		removePartialBackups(targetTreeDoc)

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
					withTempArchive { tempZip ->
						ZipFile(tempZip).use { it.addFolder(folder) }
						writeBackup(targetTreeDoc, zipName, partialSuffix = ".zip", create = { parent, name -> parent.createFile(ZIP_MIME_TYPE, name) }) { doc ->
							context.contentResolver.openOutputStream(doc.uri)?.use { out ->
								tempZip.inputStream().use { it.copyTo(out) }
							} ?: throw IOException("openOutputStream returned null")
						}
					}
					if (deleteSource) deleteMovedSource(folder)
					transferred++
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
					withTempArchive { tempZip ->
						context.contentResolver.openInputStream(doc.uri)?.use { input ->
							tempZip.outputStream().use { input.copyTo(it) }
						} ?: throw IOException("openInputStream returned null")
						copyThroughStaging(targetDir, dest) { staged ->
							ZipFile(tempZip).use { zip ->
								zip.extractAll(staged.absolutePath, UnzipParameters().apply { isExtractSymbolicLinks = false })
							}
						}
					}
					transferred++
					if (deleteSource) doc.delete()
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
					copyThroughStaging(targetDir, dest) { FileManager.copyDirectory(folder, it) }
					if (deleteSource) deleteMovedSource(folder)
					transferred++
				}
			} catch (e: Exception) {
				failed++
				errors.add("${folder.name}: ${e.message}")
				Log.err("File transfer failed for ${folder.name}", e)
			}
		}

		TransferResult(sourceFiles.size, transferred, skipped, failed, errors)
	}

	/** Lets [copy] fill a staging folder on [targetDir]'s storage, then renames it to [dest]. */
	private suspend inline fun copyThroughStaging(targetDir: File, dest: File, copy: (File) -> Unit) {
		val staged = PackStaging.create(targetDir)
		try {
			copy(staged)
			if (!staged.renameTo(dest)) throw IOException("Could not move the copy to ${dest.path}")
		} finally {
			PackStaging.discard(staged)
		}
	}

	/** Gives [use] a path for a temporary archive in the cache's staging folder, which the next start clears. */
	private suspend inline fun <T> withTempArchive(use: (File) -> T): T {
		// A fresh folder per archive: zip4j *appends* to an existing archive, so a leftover from a
		// crashed run once produced a backup with two copies of every entry.
		val holder = PackStaging.create(context.cacheDir)
		try {
			return use(File(holder, TEMP_ARCHIVE_NAME))
		} finally {
			PackStaging.discard(holder)
		}
	}

	private suspend fun deleteMovedSource(source: File) {
		try {
			PackStaging.delete(source)
		} catch (e: IOException) {
			throw IOException("Copied, but the original was kept: ${e.message}", e)
		}
	}

	/**
	 * Creates one backup entry in [parent] with [create], fills it with [write] and only then names it
	 * [name], so a kill part way leaves a [PARTIAL_PREFIX] entry, which the restore list hides and the
	 * next export into the folder removes, rather than a broken backup that looks complete. Throws
	 * unless the entry ends up under exactly [name]: a provider may rename to "name (1)" instead.
	 * A provider that cannot rename gets the entry written under [name] directly.
	 */
	private fun writeBackup(
		parent: DocumentFile,
		name: String,
		partialSuffix: String,
		create: (DocumentFile, String) -> DocumentFile?,
		write: (DocumentFile) -> Unit,
	) {
		val partialName = PARTIAL_PREFIX + UUID.randomUUID()
		activePartials += partialName
		try {
			val entry = create(parent, partialName + partialSuffix)
				?: throw IOException("Could not create $name in the backup folder")
			if (!supportsRename(entry)) {
				entry.delete()
				writeOrRemove(create(parent, name) ?: throw IOException("Could not create $name in the backup folder"), write)
				return
			}
			writeOrRemove(entry, write)
			if (!entry.renameTo(name)) {
				runCatching { entry.delete() }
				throw IOException("Could not name the backup $name")
			}
			if (entry.name != name) throw IOException("The backup folder saved it as ${entry.name} instead of $name")
		} finally {
			activePartials -= partialName
		}
	}

	private inline fun writeOrRemove(entry: DocumentFile, write: (DocumentFile) -> Unit) {
		try {
			write(entry)
		} catch (e: Exception) {
			// The entry did not exist before this attempt, so removing it only drops the partial copy.
			runCatching { entry.delete() }
			throw e
		}
	}

	private fun supportsRename(entry: DocumentFile): Boolean =
		try {
			// A file-backed DocumentFile renames through java.io.File.
			entry.uri.scheme != ContentResolver.SCHEME_CONTENT || context.contentResolver
				.query(entry.uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)
				?.use { it.moveToFirst() && it.getInt(0) and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0 } == true
		} catch (e: Exception) {
			Log.err("Could not read the backup folder's rename support", e)
			false
		}

	/** Removes partial entries a killed export left in [parent], except those of exports still running. */
	private fun removePartialBackups(parent: DocumentFile) {
		parent.listFiles()
			.filter { doc ->
				val name = doc.name
				name != null && isPartialBackup(name) && activePartials.none { name.startsWith(it) }
			}
			.forEach { runCatching { it.delete() } }
	}

	companion object {
		private const val PARTIAL_PREFIX = ".unipad-partial-"
		private const val ZIP_MIME_TYPE = "application/zip"
		private const val TEMP_ARCHIVE_NAME = "transfer.zip"
		private const val LEGACY_TEMP_ARCHIVE_PREFIX = "transfer-"

		/** Base names of the partial entries exports in this process are writing. */
		private val activePartials: MutableSet<String> = ConcurrentHashMap.newKeySet()

		/** An export that has not finished; never a pack to restore. */
		fun isPartialBackup(name: String?): Boolean = name?.startsWith(PARTIAL_PREFIX) == true

		/**
		 * Startup cleanup for [cacheDir]: sets aside temporary archives killed transfers left (see
		 * [PackStaging.setAsideLeftovers]) and returns them for deletion. Earlier versions kept them as
		 * `transfer-*.zip` directly in the cache, which no transfer creates any more.
		 */
		fun setAsideTempArchives(cacheDir: File): List<File> =
			PackStaging.setAsideLeftovers(cacheDir) + cacheDir.listFiles().orEmpty().filter {
				it.isFile && it.name.startsWith(LEGACY_TEMP_ARCHIVE_PREFIX) && it.name.endsWith(".zip")
			}
	}
}
