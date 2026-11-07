package com.kimjisub.launchpad.manager

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.tool.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.util.Locale

object FileManager {
	private const val COPY_BUFFER_SIZE = 4096
	private const val BYTES_PER_MB = 1024 * 1024
	private const val MAX_UNWRAP_DEPTH = 8
	private const val MACOSX_FOLDER = "__MACOSX"
	private val FILENAME_FILTER_REGEX = "[|\\\\?*<\":>/]+".toRegex()

	/** Throws [IOException] when a file cannot be moved up, so the pack is not kept with it missing. */
	fun removeDoubleFolder(path: String) {
		val rootFolder = File(path)
		if (!isDirectoryWithoutLinks(rootFolder)) return

		// Repeats like iOS (Pack/Pack/Pack/info used to stay broken here), and Finder's
		// __MACOSX sibling does not count as content (every Mac-zipped pack was rejected).
		// A readme or cover image beside the pack folder is not part of the pack and is dropped,
		// as long as only one folder holds an info; two packs in one archive are left unopened.
		repeat(MAX_UNWRAP_DEPTH) {
			val children = rootFolder.listFiles() ?: return
			val content = children.filter { !isHiddenEntry(it) }
			val packFolder = content.singleOrNull()?.takeIf { isDirectoryWithoutLinks(it) }
				?: soleFolderHoldingInfo(rootFolder, content)
				?: return
			children.filter { it.name == MACOSX_FOLDER || (it in content && it != packFolder) }
				.forEach { deleteDirectory(it) }
			moveDirectory(renameAside(packFolder), rootFolder)
		}
	}

	private fun isHiddenEntry(file: File) = file.name.startsWith(".") || file.name == MACOSX_FOLDER

	/** Resolve the parent first: an alias in a parent is not a link at this entry (API 24 too). */
	private fun isSymbolicLink(file: File): Boolean {
		val absolute = file.absoluteFile
		val parent = absolute.parentFile ?: return false
		val entry = File(parent.canonicalFile, absolute.name)
		return entry.canonicalFile != entry ||
			// Canonical paths do not resolve dangling links, but those still have a directory entry.
			(!entry.exists() && parent.list()?.contains(absolute.name) == true)
	}

	/** Without an exact entry, refuse an alias if any canonical match is a link. */
	private fun isLinkedDestination(
		file: File,
		entries: Map<String, File> = file.absoluteFile.parentFile?.listFiles()?.associateBy { it.name }
			?: throw IOException("Could not list parent of ${file.path}"),
	): Boolean {
		entries[file.name]?.let { return isSymbolicLink(it) }
		if (!file.exists()) return false
		val canonical = file.canonicalFile
		return entries.values.any { it.canonicalFile == canonical && isSymbolicLink(it) }
	}

	private fun isDirectoryWithoutLinks(file: File) = file.isDirectory && !isSymbolicLink(file)

	private fun hasInfoFile(dir: File) = dir.listFiles().orEmpty().any {
		it.isFile && it.name.equals("info", ignoreCase = true) && !isSymbolicLink(it)
	}

	private fun holdsInfo(dir: File, depth: Int): Boolean =
		hasInfoFile(dir) || depth > 0 && dir.listFiles().orEmpty()
			.any { isDirectoryWithoutLinks(it) && !isHiddenEntry(it) && holdsInfo(it, depth - 1) }

	private fun soleFolderHoldingInfo(rootFolder: File, content: List<File>): File? {
		if (hasInfoFile(rootFolder)) return null
		return content.filter { isDirectoryWithoutLinks(it) && holdsInfo(it, MAX_UNWRAP_DEPTH) }.singleOrNull()
	}

	/**
	 * Renames [dir] to a hidden name that is free both beside it and inside it, so no entry of
	 * [dir] (an inner folder of the same name, or one named like the temporary name) is moved
	 * onto the folder it is being moved out of.
	 */
	private fun renameAside(dir: File): File {
		val taken = { aside: File -> aside.exists() || File(dir, aside.name).exists() }
		return claimNextPath(dir.parentFile!!, ".unwrap", "", taken) { !taken(it) && dir.renameTo(it) }
	}

	fun sortByTime(files: Array<File>): Array<File> {
		return files.sortedByDescending { getInnerFileLastModified(it) }.toTypedArray()
	}

	fun getInnerFileLastModified(target: File): Long {
		var time: Long = 0
		if (target.isDirectory) for (file in target.listFiles().orEmpty()) {
			if (file.isFile) {
				time = file.lastModified()
				break
			}
		}
		return time
	}

	fun makeNextPath(dir: File?, name: String, extension: String): File {
		val newName = filterFilename(name)
		var i = 1
		while (numberedPath(dir, newName, extension, i).exists()) i++
		return numberedPath(dir, newName, extension, i)
	}

	/**
	 * Renames the finished folder [staged] to the first free name in [dir], numbered as
	 * [makeNextPath] does. The rename puts the whole pack in place at once and fails on a name
	 * taken in the meantime, so two installs of the same name never share a folder; a name that is
	 * taken, even by an empty folder, is left alone.
	 */
	fun moveToNextFolder(staged: File, dir: File, name: String): File =
		claimNextPath(dir, name, "") { !it.exists() && staged.renameTo(it) }

	/** Tries [create] on each numbered name in turn, skipping the names [isTaken] reports. */
	private fun claimNextPath(
		dir: File,
		name: String,
		extension: String,
		isTaken: (File) -> Boolean = File::exists,
		create: (File) -> Boolean,
	): File {
		val newName = filterFilename(name)
		var i = 1
		while (true) {
			val candidate = numberedPath(dir, newName, extension, i)
			if (create(candidate)) return candidate
			if (!isTaken(candidate)) throw IOException("Could not create ${candidate.path}")
			i++
		}
	}

	private fun numberedPath(dir: File?, name: String, extension: String, index: Int): File =
		if (index == 1) File(dir, name + extension) else File(dir, "$name ($index)$extension")

	fun filterFilename(originalStr: String): String {
		return originalStr.replace(FILENAME_FILTER_REGEX, "")
	}

	/**
	 * Copies [sourceDir] into [targetDir] and deletes it. A file that cannot be copied throws
	 * [IOException] and [sourceDir] is kept; the copy used to be skipped and the source deleted, so
	 * the file was lost while the import reported success.
	 */
	fun moveDirectory(sourceDir: File, targetDir: File) {
		if (isSymbolicLink(sourceDir)) {
			sourceDir.delete()
			return
		}
		if (isLinkedDestination(targetDir)) throw IOException("Cannot move into a symbolic link: ${targetDir.path}")
		if (!targetDir.isDirectory && !targetDir.mkdir()) throw IOException("Could not create ${targetDir.path}")
		val targetEntries = targetDir.listFiles()?.associateBy { it.name }
			?: throw IOException("Could not list ${targetDir.path}")
		val sourceList = sourceDir.listFiles() ?: throw IOException("Could not list ${sourceDir.path}")
		for (source in sourceList) {
			if (isSymbolicLink(source)) continue
			val target = File(targetDir, source.name)
			if (isLinkedDestination(target, targetEntries)) {
				throw IOException("Cannot move onto a symbolic link: ${target.path}")
			}
			if (source.isDirectory) {
				target.mkdir()
				moveDirectory(source, target)
			} else {
				FileInputStream(source).use { fis ->
					// Exclusive creation sees dangling links even when exists() follows them to nothing.
					if (!target.createNewFile() && !target.exists()) {
						throw IOException("Cannot move onto a dangling symbolic link: ${target.path}")
					}
					FileOutputStream(target).use { fos ->
						val b = ByteArray(COPY_BUFFER_SIZE)
						var cnt = 0
						while (fis.read(b).also { cnt = it } != -1) {
							fos.write(b, 0, cnt)
						}
					}
				}
			}
			target.setLastModified(source.lastModified())
		}
		targetDir.setLastModified(sourceDir.lastModified())
		deleteDirectory(sourceDir)
	}

	fun deleteDirectory(file: File) {
		try {
			if (isDirectoryWithoutLinks(file)) {
				val childFileList = file.listFiles() ?: return
				for (childFile in childFileList) deleteDirectory(childFile)
				file.delete()
			} else file.delete()
		} catch (e: SecurityException) {
			Log.err("deleteDirectory failed", e)
		} catch (e: IOException) {
			Log.err("deleteDirectory failed", e)
		}
	}

	fun makeDirWhenNotExist(dir: File) {
		try {
			if (!dir.isDirectory) {
				if (dir.isFile) dir.delete()
				dir.mkdir()
			}
		} catch (e: SecurityException) {
			Log.err("makeDirWhenNotExist failed", e)
		}

	}

	fun makeNomedia(parent: File?) {
		try {
			val nomedia = File(parent, ".nomedia")
			if (!nomedia.isFile) {
				try {
					FileWriter(nomedia).use { }
				} catch (e: IOException) {
					Log.err("makeNomedia: write failed", e)
				}
			}
		} catch (e: SecurityException) {
			Log.err("makeNomedia failed", e)
		}
	}

	fun byteToMB(b: Long, format: String = "%.2f"): String {
		return String.format(Locale.US, format, b.toFloat() / BYTES_PER_MB)
	}

	suspend fun getFolderSize(file: File): Long = withContext(Dispatchers.IO) {
		when {
			file.isFile -> file.length()
			file.isDirectory -> {
				val childFileList: Array<out File> = file.listFiles() ?: return@withContext 0L
				childFileList.sumOf { getFolderSize(it) }
			}
			else -> 0L
		}
	}

	/**
	 * Lists [dir], throwing where [File.listFiles] returns null: a copy that skipped an unreadable
	 * folder used to report the pack as moved, and the original was deleted with that folder in it.
	 */
	fun listEntries(dir: File): Array<File> =
		dir.listFiles() ?: throw IOException("Could not read the folder ${dir.path}")

	/** Throws unless every folder under [dir] can be listed. */
	fun requireReadableTree(dir: File) {
		for (child in listEntries(dir)) if (child.isDirectory) requireReadableTree(child)
	}

	fun copyDirectory(source: File, target: File) {
		if (source.isDirectory) {
			if (!target.isDirectory && !target.mkdirs()) throw IOException("Could not create ${target.path}")
			listEntries(source).forEach { child ->
				copyDirectory(child, File(target, child.name))
			}
		} else {
			source.inputStream().use { input ->
				FileOutputStream(target).use { output ->
					val buffer = ByteArray(COPY_BUFFER_SIZE)
					var count: Int
					while (input.read(buffer).also { count = it } != -1) {
						output.write(buffer, 0, count)
					}
				}
			}
		}
	}

	fun copyDocumentTreeToFile(context: Context, source: DocumentFile, target: File) {
		if (source.isDirectory) {
			if (!target.exists()) target.mkdirs()
			for (child in source.listFiles()) {
				copyDocumentTreeToFile(context, child, File(target, child.name ?: "unknown"))
			}
		} else {
			context.contentResolver.openInputStream(source.uri)?.use { input ->
				FileOutputStream(target).use { output ->
					val buffer = ByteArray(COPY_BUFFER_SIZE)
					var count: Int
					while (input.read(buffer).also { count = it } != -1) {
						output.write(buffer, 0, count)
					}
				}
			}
		}
	}

	fun copyFileToDocumentTree(context: Context, source: File, targetParent: DocumentFile) {
		if (source.isDirectory) {
			val dirDoc = targetParent.createDirectory(source.name)
				?: targetParent.findFile(source.name)?.takeIf { it.isDirectory }
				?: throw IOException("Could not create the folder ${source.name} in ${targetParent.uri}")
			listEntries(source).forEach { child ->
				copyFileToDocumentTree(context, child, dirDoc)
			}
		} else {
			// The provider derives the stored name from (mimeType, displayName): it keeps the name
			// when the extension already matches the type and appends one otherwise. Passing the
			// name without its extension lost every ".m4a"/".json" and keySound could no longer
			// find the sounds; the mime type is therefore looked up from the real extension and
			// the full name is passed, which leaves "info", "keySound", "clip.m4a" untouched.
			val mimeType = android.webkit.MimeTypeMap.getSingleton()
				.getMimeTypeFromExtension(source.extension.lowercase())
				?: "application/octet-stream"
			val fileDoc = targetParent.createFile(mimeType, source.name)
				?: throw IOException("Could not create ${source.name} in ${targetParent.uri}")
			context.contentResolver.openOutputStream(fileDoc.uri)?.use { output ->
				source.inputStream().use { input -> input.copyTo(output, COPY_BUFFER_SIZE) }
			} ?: throw IOException("Could not open ${fileDoc.uri} for writing")
		}
	}
}
