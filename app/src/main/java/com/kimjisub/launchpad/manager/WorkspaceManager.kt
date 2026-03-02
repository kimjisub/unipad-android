package com.kimjisub.launchpad.manager

import android.content.Context
import android.os.Build
import android.os.Environment
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.db.repository.UnipackRepository
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.kimjisub.launchpad.R
import java.io.File

class WorkspaceManager(val context: Context) : KoinComponent {
	val repo: UnipackRepository by inject()
	val preferenceManager by lazy { PreferenceManager(context) }

	data class Workspace(
		val name: String,
		val file: File,
	)

	// All available workspaces
	val availableWorkspaces: Array<Workspace>
		get() {
			val uniPackWorkspaces = mutableListOf<Workspace>()

			// All versions: app-specific external storage (no permission required, deleted with app uninstall)
			context.getExternalFilesDir(null)?.let { appExternalDir ->
				val appWorkspace = File(appExternalDir, "UniPack")
				if (!appWorkspace.exists()) {
					appWorkspace.mkdirs()
				}
				FileManager.makeDirWhenNotExist(appWorkspace)
				FileManager.makeNomedia(appWorkspace)
				uniPackWorkspaces.add(
					Workspace(
						context.getString(R.string.workspace_app_storage),
						appWorkspace
					)
				)
			}

			// External SD cards only (skip internal storage which duplicates App Storage)
			val dirs = context.getExternalFilesDirs("UniPack")
			var externalIndex = 1

			dirs.forEach { file ->
				if (file.absolutePath.contains("/storage/emulated/0")) return@forEach

				val name = context.getString(R.string.workspace_external_sd_card_format, externalIndex)
				externalIndex++

				uniPackWorkspaces.add(
					Workspace(name, file)
				)
			}

			// Android 10 (API 29): Documents directory — legacy, listed last
			// Not accessible on Android 11+ (API 30+) due to Scoped Storage
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
				@Suppress("DEPRECATION")
				val externalStoragePublicPath =
					Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
				val externalStoragePublic = File(externalStoragePublicPath, "Unipad")
				if (!externalStoragePublic.exists()) {
					externalStoragePublic.mkdirs()
				}
				if (externalStoragePublic.canWrite()) {
					FileManager.makeDirWhenNotExist(externalStoragePublic)
					FileManager.makeNomedia(externalStoragePublic)
					uniPackWorkspaces.add(
						Workspace(
							context.getString(R.string.workspace_documents_android10),
							externalStoragePublic
						)
					)
				}
			}

			return uniPackWorkspaces.toTypedArray()
		}

	suspend fun getAvailableWorkspacesSize() =
		coroutineScope {
			val works = availableWorkspaces.map {
				async { FileManager.getFolderSize(it.file) }
			}

			works.awaitAll().sum()
		}

	fun validateWorkspace() {
		// Default download location to App Storage if not set or invalid
		val downloadPath = preferenceManager.downloadStoragePath
		val availablePaths = availableWorkspaces.map { it.file.path }.toSet()
		if (downloadPath == null || !availablePaths.contains(downloadPath)) {
			val appStorage = getAppStorageWorkspaceDir()
			val appStorageWorkspace = availableWorkspaces.firstOrNull {
				it.file.path == appStorage?.path
			}
			preferenceManager.downloadStoragePath =
				appStorageWorkspace?.file?.path ?: availableWorkspaces[0].file.path
		}
	}

	// Workspace used for downloads
	val downloadWorkspace: Workspace
		get() {
			val downloadPath = preferenceManager.downloadStoragePath
			if (downloadPath != null) {
				val match = availableWorkspaces.firstOrNull { it.file.path == downloadPath }
				if (match != null) return match
			}
			return availableWorkspaces[0]
		}

	// -- Old "Unipad" → new "UniPack" folder migration --

	/** Migrate old "Unipad" app storage folder to new "UniPack" folder. Safe to call repeatedly. */
	fun migrateOldAppStorageFolder() {
		val appExternalDir = context.getExternalFilesDir(null) ?: return
		val oldDir = File(appExternalDir, "Unipad")
		val newDir = File(appExternalDir, "UniPack")

		if (!oldDir.exists()) return

		if (!newDir.exists()) {
			// Simple rename
			oldDir.renameTo(newDir)
		} else {
			// Both exist: move contents from old to new (skip existing)
			oldDir.listFiles()?.forEach { child ->
				val dest = File(newDir, child.name)
				if (!dest.exists()) {
					child.renameTo(dest)
				}
			}
			// Clean up old directory if empty
			if (oldDir.listFiles()?.isEmpty() != false) {
				oldDir.delete()
			}
		}
	}

	/** Old "Unipad" app storage dir (for migration detection) */
	fun getLegacyAppStorageDir(): File? {
		val appExternalDir = context.getExternalFilesDir(null) ?: return null
		val oldDir = File(appExternalDir, "Unipad")
		return if (oldDir.exists() && oldDir.canRead()) oldDir else null
	}

	fun getLegacyAppStorageFolders(): List<File> {
		val dir = getLegacyAppStorageDir() ?: return emptyList()
		return dir.listFiles()
			?.filter { it.isDirectory && it.name != ".nomedia" }
			?: emptyList()
	}

	// Workspaces used for loading unipacks

	fun getUnipackCount(workspace: Workspace): Int {
		return workspace.file.listFiles()?.count { it.isDirectory && it.name != ".nomedia" } ?: 0
	}

	fun getDocumentsWorkspaceDir(): File? {
		@Suppress("DEPRECATION")
		val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
		val uniPackDir = File(documentsDir, "UniPack")
		if (uniPackDir.exists() && uniPackDir.canRead()) return uniPackDir
		val unipadDir = File(documentsDir, "Unipad")
		return if (unipadDir.exists() && unipadDir.canRead()) unipadDir else null
	}

	fun getAppStorageWorkspaceDir(): File? {
		val appExternalDir = context.getExternalFilesDir(null) ?: return null
		val unipackDir = File(appExternalDir, "UniPack")
		if (!unipackDir.exists()) unipackDir.mkdirs()
		return unipackDir
	}

	fun getDocumentsUnipackFolders(): List<File> {
		val dir = getDocumentsWorkspaceDir() ?: return emptyList()
		return dir.listFiles()
			?.filter { it.isDirectory && it.name != ".nomedia" }
			?: emptyList()
	}

	/** Legacy UniPack directory: getExternalFilesDirs("UniPack") on internal storage */
	fun getLegacyUniPackDir(): File? {
		val dirs = context.getExternalFilesDirs("UniPack")
		val internal = dirs.firstOrNull { it.absolutePath.contains("/storage/emulated/0") }
		return if (internal != null && internal.exists() && internal.canRead()) internal else null
	}

	fun getLegacyUniPackFolders(): List<File> {
		val dir = getLegacyUniPackDir() ?: return emptyList()
		return dir.listFiles()
			?.filter { it.isDirectory && it.name != ".nomedia" }
			?: emptyList()
	}

	fun getInternalStorageWorkspaceDir(): File? {
		val unipackDir = File(context.filesDir, "UniPack")
		return if (unipackDir.exists() && unipackDir.canRead()) unipackDir else null
	}

	fun getInternalStorageUnipackFolders(): List<File> {
		val dir = getInternalStorageWorkspaceDir() ?: return emptyList()
		return dir.listFiles()
			?.filter { it.isDirectory && it.name != ".nomedia" }
			?: emptyList()
	}

	suspend fun getUnipacks() =
		withContext(Dispatchers.IO) {
			val unipacks = mutableListOf<UniPackItem>()

			for (workspace in availableWorkspaces) {
				val folder = workspace.file
				val files = folder.listFiles()
				files?.forEach {
					if (!it.isDirectory) return@forEach

					val unipack = UniPackFolder(it).load()
					val unipackENT = repo.getOrCreate(unipack.id)

					val packItem = UniPackItem(unipack, unipackENT)
					unipacks.add(packItem)
				}
			}

			unipacks.toList()
		}
}