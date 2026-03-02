package com.kimjisub.launchpad.tool

import com.kimjisub.launchpad.manager.FileManager
import com.kimjisub.launchpad.manager.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DocumentsMigrationHelper(private val ws: WorkspaceManager) {

	data class MigrationResult(
		val total: Int,
		val moved: Int,
		val skipped: Int,
		val failed: Int,
		val errors: List<String>,
	)

	fun getFoldersToMigrate(): List<File> =
		ws.getDocumentsUnipackFolders() + ws.getInternalStorageUnipackFolders()

	suspend fun migrate(
		folders: List<File>,
		onProgress: (current: Int, total: Int) -> Unit,
	): MigrationResult = withContext(Dispatchers.IO) {
		val targetDir = ws.getAppStorageWorkspaceDir()
			?: return@withContext MigrationResult(folders.size, 0, 0, folders.size, listOf("App Storage unavailable"))

		var moved = 0
		var skipped = 0
		var failed = 0
		val errors = mutableListOf<String>()

		folders.forEachIndexed { index, folder ->
			withContext(Dispatchers.Main) {
				onProgress(index + 1, folders.size)
			}

			try {
				val dest = File(targetDir, folder.name)
				if (dest.exists()) {
					// Destination already exists — skip and remove source
					FileManager.deleteDirectory(folder)
					skipped++
				} else {
					FileManager.moveDirectory(folder, dest)
					moved++
				}
			} catch (e: Exception) {
				failed++
				errors.add("${folder.name}: ${e.message}")
				Log.err("Migration failed for ${folder.name}", e)
			}
		}

		// Clean up Documents/Unipad if empty
		cleanUpEmptyDir(ws.getDocumentsWorkspaceDir())
		// Clean up Internal Storage/Unipad if empty
		cleanUpEmptyDir(ws.getInternalStorageWorkspaceDir())

		MigrationResult(folders.size, moved, skipped, failed, errors)
	}

	private fun cleanUpEmptyDir(dir: File?) {
		if (dir == null) return
		val remaining = dir.listFiles()?.filter { it.name != ".nomedia" } ?: emptyList()
		if (remaining.isEmpty()) {
			FileManager.deleteDirectory(dir)
		}
	}
}
