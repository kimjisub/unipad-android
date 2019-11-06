package com.kimjisub.manager

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.media.MediaPlayer
import android.os.Environment
import java.io.*
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object FileManager {
	@Throws(IOException::class)
	fun unZipFile(zipFileURL: String?, location: String) {
		var location = location
		val zipFile: InputStream = FileInputStream(zipFileURL)
		var size: Int
		val buffer = ByteArray(1024)
		try {
			if (!location.endsWith("/")) {
				location += "/"
			}
			val f = File(location)
			if (!f.isDirectory) f.mkdirs()
			val zin = ZipInputStream(BufferedInputStream(zipFile, 1024))
			try {
				var ze: ZipEntry
				while (zin.nextEntry.also { ze = it } != null) {
					val path = location + ze.name
					val unzipFile = File(path)
					if (ze.isDirectory) {
						if (!unzipFile.isDirectory) unzipFile.mkdirs()
					} else {
						val parentDir: File? = unzipFile.parentFile
						if (null != parentDir) {
							if (!parentDir.isDirectory) parentDir.mkdirs()
						}
						val out = FileOutputStream(unzipFile, false)
						val fout = BufferedOutputStream(out, 1024)
						try {
							while (zin.read(buffer, 0, 1024).also { size = it } != -1) {
								fout.write(buffer, 0, size)
							}
							zin.closeEntry()
						} catch (e: Exception) {
							e.printStackTrace()
						}
						fout.flush()
						fout.close()
					}
				}
			} catch (e: Exception) {
				e.printStackTrace()
			}
			zin.close()
			removeDoubleFolder(location)
		} catch (e: Exception) {
			e.printStackTrace()
		}
	}

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


	fun sortByTime(files: Array<File>): Array<File> {
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
				(object1 as File).name.toLowerCase().compareTo((object2 as File).name.toLowerCase())
			} as Comparator<Any>
		)
		return files
	}

	fun getInnerFileLastModified(target: File): Long {
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
			ret = if (i == 1) File(dir, newName + extension) else File(dir, "$newName ($i)$extension")
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

	fun isInternalFile(context: Context, file: File): Boolean {
		val target: String? = getInternalUniPackRoot(context).path
		val source: String = file.path
		return source.contains(target!!)
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
		if (!dir.isDirectory) {
			if (dir.isFile) dir.delete()
			dir.mkdir()
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
	fun byteToMB(b: Long): String {
		return String.format("%.2f", b.toFloat() / 1024 / 1024)
	}

	fun getFolderSize(file: File): Long {
		var totalMemory: Long = 0
		if (file.isFile) {
			return file.length()
		} else if (file.isDirectory) {
			val childFileList: Array<out File> = file.listFiles() ?: return 0
			for (childFile in childFileList) totalMemory += getFolderSize(childFile)
			return totalMemory
		} else return 0
	}

	fun getExternalUniPackRoot(): File = File(Environment.getExternalStorageDirectory(), "Unipad")


	fun getInternalUniPackRoot(context: Context): File = context.getDir("UniPack", MODE_PRIVATE)


	/*public static String getInternalStoragePath() {
		return Environment.getExternalStorageDirectory().getPath();
	}

	public static String getExternalSDCardPath() {
		HashSet<String> hs = getExternalMounts();
		for (String extSDCardPath : hs) {
			return extSDCardPath;
		}
		return null;
	}

	public static HashSet<String> getExternalMounts() {
		final HashSet<String> out = new HashSet<String>();
		String reg = "(?i).*media_rw.*(storage).*(sdcardfs).*rw.*";
		String s = "";
		try {
			final Process process = new ProcessBuilder().command("mount").redirectErrorStream(true).start();
			process.waitFor();
			final InputStream is = process.getInputStream();
			final byte[] buffer = new byte[1024];
			while (is.read(buffer) != -1) {
				s = s + new String(buffer);
			}
			is.close();
		} catch (final Exception e) {
			e.printStackTrace();
		}

		final String[] lines = s.split("\n");
		for (String line : lines) {
			if (!line.toLowerCase(Locale.US).contains("asec")) {
				if (line.matches(reg)) {
					String[] parts = line.split(" ");
					for (String part : parts) {
						if (part.startsWith("/")) {
							if (!part.toLowerCase(Locale.US).contains("vold") && !part.toLowerCase(Locale.US).contains("/mnt/")) {
								out.add(part);
							}
						}
					}
				}
			}
		}

		return out;
	}*/

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