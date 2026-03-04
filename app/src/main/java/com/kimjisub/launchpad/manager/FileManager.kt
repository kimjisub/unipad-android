package com.kimjisub.launchpad.manager

import android.content.Context
import android.media.MediaPlayer
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
	private const val DEFAULT_DURATION_MS = 10000
	private val FILENAME_FILTER_REGEX = "[|\\\\?*<\":>/]+".toRegex()

	fun removeDoubleFolder(path: String) {
		try {
			val rootFolder = File(path)
			if (!rootFolder.isDirectory) return

			val children = rootFolder.listFiles() ?: return
			val nonHidden = children.filter { !it.name.startsWith(".") }

			if (nonHidden.size == 1 && nonHidden[0].isDirectory) {
				moveDirectory(nonHidden[0], rootFolder)
			}
		} catch (e: IOException) {
			Log.err("removeDoubleFolder failed", e)
		}
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
		var ret: File
		val newName = filterFilename(name)
		var i = 1
		while (true) {
			ret =
				if (i == 1) File(dir, newName + extension) else File(dir, "$newName ($i)$extension")
			if (!ret.exists()) break
			i++
		}
		return ret
	}

	fun filterFilename(originalStr: String): String {
		return originalStr.replace(FILENAME_FILTER_REGEX, "")
	}

	fun moveDirectory(sourceDir: File, targetDir: File) {
		try {
			if (!targetDir.isDirectory) targetDir.mkdir()
			val sourceList = sourceDir.listFiles() ?: return
			for (source in sourceList) {
				val target = File(targetDir, source.name)
				if (source.isDirectory) {
					target.mkdir()
					moveDirectory(source, target)
				} else {
					try {
						FileInputStream(source).use { fis ->
							FileOutputStream(target).use { fos ->
								val b = ByteArray(COPY_BUFFER_SIZE)
								var cnt = 0
								while (fis.read(b).also { cnt = it } != -1) {
									fos.write(b, 0, cnt)
								}
							}
						}
					} catch (e: IOException) {
						Log.err("moveDirectory: copy failed", e)
					}
				}
				target.setLastModified(source.lastModified())
			}
			targetDir.setLastModified(sourceDir.lastModified())
			deleteDirectory(sourceDir)
		} catch (e: IOException) {
			Log.err("moveDirectory failed", e)
		}
	}

	fun deleteDirectory(file: File) {
		try {
			if (file.isDirectory) {
				val childFileList = file.listFiles() ?: return
				for (childFile in childFileList) deleteDirectory(childFile)
				file.delete()
			} else file.delete()
		} catch (e: SecurityException) {
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

	fun wavDuration(mplayer: MediaPlayer, url: String?): Int {
		return try {
			mplayer.reset()
			mplayer.setDataSource(url)
			mplayer.prepare()
			mplayer.duration
		} catch (e: IOException) {
			Log.err("wavDuration failed", e)
			mplayer.reset()
			DEFAULT_DURATION_MS
		}
	}

	fun copyDirectory(source: File, target: File) {
		if (source.isDirectory) {
			if (!target.exists()) target.mkdirs()
			source.listFiles()?.forEach { child ->
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
				?: targetParent.findFile(source.name) ?: return
			source.listFiles()?.forEach { child ->
				copyFileToDocumentTree(context, child, dirDoc)
			}
		} else {
			val mimeType = when (source.extension.lowercase()) {
				"wav" -> "audio/wav"; "mp3" -> "audio/mpeg"; "ogg" -> "audio/ogg"
				else -> "application/octet-stream"
			}
			val fileDoc = targetParent.createFile(mimeType, source.nameWithoutExtension) ?: return
			context.contentResolver.openOutputStream(fileDoc.uri)?.use { output ->
				source.inputStream().use { input -> input.copyTo(output, COPY_BUFFER_SIZE) }
			}
		}
	}
}