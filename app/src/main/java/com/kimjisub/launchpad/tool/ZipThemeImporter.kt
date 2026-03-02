package com.kimjisub.launchpad.tool

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.manager.FileManager
import net.lingala.zip4j.ZipFile
import java.io.File

object ZipThemeImporter {

	class InvalidThemeException(message: String) : Exception(message)

	fun getThemesDir(context: Context): File {
		val dir = File(context.getExternalFilesDir(null), "themes")
		if (!dir.exists()) dir.mkdirs()
		return dir
	}

	fun import(context: Context, uri: Uri): String {
		val fileName = DocumentFile.fromSingleUri(context, uri)?.name ?: "unknown.zip"
		val nameWithoutExt = fileName.removeSuffix(".zip").removeSuffix(".ZIP")
		val themesDir = getThemesDir(context)
		val targetDir = FileManager.makeNextPath(themesDir, nameWithoutExt, "/")

		try {
			targetDir.mkdirs()

			val tempZip = File.createTempFile("theme_import_", ".zip", context.cacheDir)
			try {
				context.contentResolver.openInputStream(uri)?.use { input ->
					tempZip.outputStream().use { output ->
						input.copyTo(output)
					}
				}
				ZipFile(tempZip).use { zip ->
					zip.extractAll(targetDir.path)
				}
			} finally {
				tempZip.delete()
			}

			// Validate: theme.json and theme_ic.png must exist
			val themeJson = File(targetDir, "theme.json")
			val themeIcon = File(targetDir, "theme_ic.png")
			if (!themeJson.exists()) {
				throw InvalidThemeException("theme.json not found")
			}
			if (!themeIcon.exists()) {
				throw InvalidThemeException("theme_ic.png not found")
			}

			return targetDir.name
		} catch (e: Exception) {
			FileManager.deleteDirectory(targetDir)
			throw e
		}
	}

	fun isBundled(context: Context, folderName: String): Boolean {
		val assetThemes = try {
			context.assets.list("themes")
				?.filter { it.endsWith(".zip") }
				?.map { it.removeSuffix(".zip") }
				?: emptyList()
		} catch (_: Exception) {
			emptyList()
		}
		return folderName in assetThemes
	}

	fun delete(context: Context, folderName: String): Boolean {
		if (isBundled(context, folderName)) return false
		val dir = File(getThemesDir(context), folderName)
		return if (dir.exists() && dir.isDirectory) {
			FileManager.deleteDirectory(dir)
			true
		} else false
	}
}
