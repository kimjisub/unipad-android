package com.kimjisub.launchpad.manager

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kimjisub.launchpad.activity.BaseActivity
import com.kimjisub.launchpad.tool.Log

class UniPackWorkspaceManager(val activity: BaseActivity) {
	val context: Context = activity.baseContext
	val p by lazy { PreferenceManager(context) }


	data class Workspace(
		val name: String,
		val uri: Uri
	) {
		override fun toString(): String {
			return "UniPackWorkspace(name='$name', uri=$uri)"
		}
	}

	val mainWorkspace: Uri
		get() {
			return workspaces[0].uri
		}

	fun setMainWorkspace(uri: Uri) {
		val index = workspaces.indexOfFirst {
			it.uri == uri
		}
		if (index != -1)
			p.mainStorage = workspaces[index].uri.path!!
		else
			p.mainStorage = workspaces[0].uri.path!!
	}

	val workspaces: Array<Workspace>
		get() {
			val uniPackWorkspaces = ArrayList<Workspace>()

			for (persistedUriPermission in context.contentResolver.persistedUriPermissions) {
				uniPackWorkspaces.add(
					Workspace(
						"Folder",
						persistedUriPermission.uri,
					)
				)
			}

			/*uniPackWorkspaces.add(
				UniPackWorkspace(
					"앱 내부 저장소",
					File(context.filesDir, "UniPack")
				)
			)

			val dirs = context.getExternalFilesDirs("UniPack")

			dirs.forEachIndexed { index, file ->

				val name = when {
					file.absolutePath.contains("/storage/emulated/0") -> "내장 SD" // todo string.xml
					else -> "SD 카드 $index"
				}

				uniPackWorkspaces.add(
					UniPackWorkspace(name, file)
				)
			}*/

			return uniPackWorkspaces.toTypedArray()
		}

	fun setActiveWorkspace() {

	}

	/*val unipacks: Array<File>
		get() {
			val unipacks = ArrayList<File>()

			for (workspace in workspaces) {

				Log.test("workspace: " + workspace.file.path)
				try {
					unipacks.addAll(workspace.file.listFiles()!!)
					Log.test("files: " + workspace.file.path)
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}


			return unipacks.toArray() as Array<File>
		}*/

	fun getUnipacks(): Array<DocumentFile> {
		val unipacks = mutableListOf<DocumentFile>()

		for (workspace in workspaces) {

			Log.test("workspace: " + workspace.uri.path)
			val folder = DocumentFile.fromTreeUri(context, workspace.uri)!!
			val files = folder.listFiles()
			files.forEach {
				unipacks.add(it)
				Log.test("file: " + it.uri)
			}
		}


		return unipacks.toTypedArray()
	}


	companion object {
		const val REQUEST_CODE = 0x0100
	}
}