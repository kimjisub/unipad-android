package com.kimjisub.launchpad.manager

import android.annotation.SuppressLint
import android.media.MediaPlayer
import androidx.documentfile.provider.DocumentFile
import java.io.*
import java.util.*

object FileManager {

	fun removeDoubleFolder(path: String) {
		try {
			val rootFolder = File(path)
			if (rootFolder.isDirectory) {
				val childFileList: Array<File> = rootFolder.listFiles()
				if (childFileList.size == 1) {
					val innerFolder = childFileList[0]
					if (innerFolder.isDirectory) {
						moveDirectory(innerFolder, rootFolder)
					}
				}
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	// ============================================================================================= Tools


	fun sortByTime(files: Array<DocumentFile>): Array<DocumentFile> {
		for (i in 0 until files.size - 1) {
			for (j in 0 until files.size - (i + 1)) {
				if (getInnerFileLastModified(files[j]) < getInnerFileLastModified(files[j + 1])) {
					val tmp = files[j + 1]
					files[j + 1] = files[j]
					files[j] = tmp
				}
			}
		}
		return files
	}

	fun sortByName(files: Array<File>): Array<File> {
		Arrays.sort(
			files,
			Comparator { object1: Any, object2: Any ->
				(object1 as File).name.lowercase(Locale.getDefault())
					.compareTo((object2 as File).name.lowercase(Locale.getDefault()))
			} as Comparator<Any>
		)
		return files
	}

	fun getInnerFileLastModified(target: DocumentFile): Long {
		var time: Long = 0
		if (target.isDirectory) for (file in target.listFiles()) {
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

	fun filterFilename(orgnStr: String): String {
		val regExpr = "[|\\\\?*<\":>/]+"
		return orgnStr.replace(regExpr.toRegex(), "")

		//return tmpStr.replaceAll("[ ]", "_");

	}

	// ============================================================================================= Make, Move, Copy, Delete


	fun moveDirectory(F_source: File, F_target: File) {
		try {
			if (!F_target.isDirectory) F_target.mkdir()
			val sourceList: Array<File> = F_source.listFiles()
			for (source in sourceList) {
				val target = File(F_target.absolutePath + "/" + source.name)
				if (source.isDirectory) {
					target.mkdir()
					moveDirectory(source, target)
				} else {
					var fis: FileInputStream? = null
					var fos: FileOutputStream? = null
					try {
						fis = FileInputStream(source)
						fos = FileOutputStream(target)
						val b = ByteArray(4096)
						var cnt = 0
						while (fis.read(b).also { cnt = it } != -1) {
							fos.write(b, 0, cnt)
						}
					} catch (e: Exception) {
						e.printStackTrace()
					} finally {
						try {
							fis!!.close()
							fos!!.close()
						} catch (e: IOException) {
							e.printStackTrace()
						}
					}
				}
				target.setLastModified(source.lastModified())
			}
			F_target.setLastModified(F_source.lastModified())
			deleteDirectory(F_source)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	fun deleteDirectory(file: DocumentFile) {
		try {
			if (file.isDirectory) {
				val childFileList: Array<DocumentFile> = file.listFiles()
				for (childFile in childFileList) deleteDirectory(childFile)
				file.delete()
			} else file.delete()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	fun deleteDirectory(file: File) {
		try {
			if (file.isDirectory) {
				val childFileList: Array<File> = file.listFiles()
				for (childFile in childFileList) deleteDirectory(childFile)
				file.delete()
			} else file.delete()
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

	fun makeDirWhenNotExist(dir: File) {
		try {
			if (!dir.isDirectory) {
				if (dir.isFile) dir.delete()
				dir.mkdir()
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}

	}

	// ============================================================================================= Get Info


	fun makeNomedia(parent: File?) {
		val nomedia = File(parent, ".nomedia")
		if (!nomedia.isFile) {
			try {
				FileWriter(nomedia).close()
			} catch (e: IOException) {
				e.printStackTrace()
			}
		}
	}

	@SuppressLint("DefaultLocale")
	fun byteToMB(b: Long, format: String = "%.2f"): String {
		return String.format(format, b.toFloat() / 1024 / 1024)
	}

	fun getFolderSize(file: DocumentFile): Long {
		var totalMemory: Long = 0
		if (file.isFile) {
			return file.length()
		} else if (file.isDirectory) {
			val childFileList: Array<out DocumentFile> = file.listFiles() ?: return 0
			for (childFile in childFileList) totalMemory += getFolderSize(childFile)
			return totalMemory
		} else return 0
	}

	// ============================================================================================= Etc


	fun wavDuration(mplayer: MediaPlayer, URL: String?): Int {
		return try {
			mplayer.reset()
			mplayer.setDataSource(URL)
			mplayer.prepare()
			mplayer.duration
		} catch (e: IOException) {
			e.printStackTrace()
			10000
		}
	}
}