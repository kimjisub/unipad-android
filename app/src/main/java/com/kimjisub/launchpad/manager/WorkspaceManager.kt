package com.kimjisub.launchpad.manager

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.kimjisub.launchpad.adapter.UniPackItem
import com.kimjisub.launchpad.db.AppDatabase
import com.kimjisub.launchpad.unipack.UniPackFolder
import kotlinx.coroutines.*
import java.io.File

class WorkspaceManager(val context: Context) {
	val p by lazy { PreferenceManager(context) }
	private val db: AppDatabase by lazy { AppDatabase.getInstance(context)!! }

	data class Workspace(
		val name: String,
		val file: File,
	) {

		override fun toString(): String {
			return "UniPackWorkspace(name='$name', file=${file.path})"
		}
	}

	// 사용 가능한 모든 Workspaces
	val availableWorkspaces: Array<Workspace>
		get() {
			val uniPackWorkspaces = ArrayList<Workspace>()

			val legacyWorkspace = File(Environment.getExternalStorageDirectory(), "Unipad")
			if (legacyWorkspace.canWrite()) {
				FileManager.makeDirWhenNotExist(legacyWorkspace)
				FileManager.makeNomedia(legacyWorkspace)
				uniPackWorkspaces.add(
					Workspace(
						"Legacy",
						legacyWorkspace
					)
				)
			}

			/*val persistedUriPermissions = context.contentResolver.persistedUriPermissions
			persistedUriPermissions.forEachIndexed { index, uriPermission ->
				uniPackWorkspaces.add(
					WorkspaceUri(
						"Folder #$index",
						uriPermission.uri,
					)
				)
			}*/

			/*uniPackWorkspaces.add(
				Workspace(
					"앱 내부 저장소",
					File(context.filesDir, "UniPack")
				)
			)*/

			val dirs = context.getExternalFilesDirs("UniPack")

			dirs.forEachIndexed { index, file ->

				val name = when {
					file.absolutePath.contains("/storage/emulated/0") -> "내장 SD" // todo string.xml
					else -> "SD 카드 $index"
				}

				uniPackWorkspaces.add(
					Workspace(name, file)
				)
			}

			return uniPackWorkspaces.toTypedArray()
		}

	val activeWorkspaces: Array<Workspace>
		get() {
			validateWorkspace()

			return availableWorkspaces.filter { p.storageActive.contains(it.file.path) }
				.toTypedArray()
		}

	suspend fun getActiveWorkspacesSize() =
		coroutineScope {
			var size = 0L
			val works = activeWorkspaces.map {
				async { FileManager.getFolderSize(activeWorkspaces[0].file) }
			}

			works.awaitAll().sum()
		}

	fun validateWorkspace() {
		// 활성화된 작업공간이 없다면 사용가능한 작업공간 중 첫번째 활성화
		if (p.storageActive.isEmpty())
			p.storageActive = setOf(availableWorkspaces[0].file.path)
	}


	// 다운로드 시 저장되는 Workspace
	val mainWorkspace: Workspace
		get() {
			return activeWorkspaces[0]
		}

	fun setMainWorkspace(uri: Uri) {
		/*val index = availableWorkspaces.indexOfFirst {
			it.uri == uri
		}
		if (index != -1)
			p.mainStorage = availableWorkspaces[index].uri.path!!
		else
			p.mainStorage = availableWorkspaces[0].uri.path!!*/
	}

	// 유니팩 로딩에 사용될 Workspaces

	/*val workspaces: Array<Workspace>
		get() {
			availableWorkspaces.filter {
				return p.activeStorage.contains(it.uri)
			}

		}*/

	suspend fun getUnipacks() =
		withContext(Dispatchers.IO) {
			val unipacks = mutableListOf<UniPackItem>()

			for (workspace in activeWorkspaces) {
				val folder = workspace.file
				val files = folder.listFiles()
				files?.forEach {
					if (!it.isDirectory) return@forEach

					val unipack = UniPackFolder(it).load()
					val unipackENT = db.unipackDAO()!!.getOrCreate(unipack.id)

					val packItem = UniPackItem(unipack, unipackENT)
					unipacks.add(packItem)
				}
			}

			unipacks.toList()
		}
}