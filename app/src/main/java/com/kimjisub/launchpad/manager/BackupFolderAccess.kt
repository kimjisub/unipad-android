package com.kimjisub.launchpad.manager

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.kimjisub.launchpad.tool.Log

/**
 * The backup folder (Documents/Unipad) chosen in the system folder picker, shared by Settings and
 * the backup/restore screen.
 *
 * A saved folder address does not mean UniPad can still open the folder: deleting the folder in a
 * Files app takes the permission away and leaves the address behind. Reading the folder then
 * returns nothing instead of failing, so callers use [usableUri] rather than the saved address.
 */
class BackupFolderAccess(
	private val resolver: ContentResolver,
	private val prefs: PreferenceManager,
) {
	enum class ConnectResult { CONNECTED, WRONG_FOLDER, PERMISSION_REFUSED }

	/** The saved folder address, or null when none is saved or UniPad no longer holds its permission. */
	val usableUri: String?
		get() {
			val saved = prefs.backupSafUri ?: return null
			val held = resolver.persistedUriPermissions.any {
				it.uri.toString() == saved && it.isReadPermission && it.isWritePermission
			}
			return saved.takeIf { held }
		}

	/**
	 * Keeps permission on [uri] and saves it, when [folderName] is the backup folder's name.
	 * The permission is refused when the app that handled the picker cannot hand out a lasting one.
	 */
	fun connect(uri: Uri, folderName: String?): ConnectResult {
		if (!isBackupFolderName(folderName)) return ConnectResult.WRONG_FOLDER
		try {
			resolver.takePersistableUriPermission(uri, PERMISSION_FLAGS)
		} catch (e: SecurityException) {
			Log.err("backup folder permission refused", e)
			return ConnectResult.PERMISSION_REFUSED
		}
		prefs.backupSafUri = uri.toString()
		return ConnectResult.CONNECTED
	}

	/** Forgets the saved folder, also when its permission is already gone. */
	fun disconnect() {
		prefs.backupSafUri?.let { saved ->
			try {
				resolver.releasePersistableUriPermission(saved.toUri(), PERMISSION_FLAGS)
			} catch (_: SecurityException) {
			}
		}
		prefs.backupSafUri = null
	}

	companion object {
		const val FOLDER_NAME = "Unipad"
		private const val PERMISSION_FLAGS =
			Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

		fun isBackupFolderName(name: String?): Boolean = name.equals(FOLDER_NAME, ignoreCase = true)

		/** Where the folder picker opens, so the backup folder is one tap away. */
		fun pickerStartUri(): Uri = DocumentsContract.buildDocumentUri(
			"com.android.externalstorage.documents",
			"primary:Documents",
		)
	}
}
