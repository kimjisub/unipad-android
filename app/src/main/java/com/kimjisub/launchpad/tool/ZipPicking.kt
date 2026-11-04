package com.kimjisub.launchpad.tool

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/** How a zip archive (a UniPack or a theme) is picked on this Android version. */
enum class ZipPickRoute {
	/** The system picker returns the zip that was tapped. */
	DOCUMENT,

	/**
	 * Android 7.0 and 7.1's picker opens a tapped zip like a folder instead of returning it
	 * (`DocumentsActivity.onDocumentPicked` opens every container first, and an archive is one), so
	 * the folder holding the zips is picked and UniPad lists them itself.
	 */
	FOLDER,
	;

	companion object {
		fun forSdk(sdk: Int): ZipPickRoute = if (sdk <= Build.VERSION_CODES.N_MR1) FOLDER else DOCUMENT
	}
}

/** One child of a picked folder, as the documents provider describes it. */
data class FolderEntry(
	val documentId: String,
	val name: String?,
	val mimeType: String?,
	val lastModified: Long,
)

object FolderZips {
	/** The entries a person can import from a folder: zip files, newest first, then by name. */
	fun select(entries: List<FolderEntry>): List<FolderEntry> = entries
		.filter { it.mimeType != DIRECTORY_MIME_TYPE && it.name?.endsWith(".zip", ignoreCase = true) == true }
		.sortedWith(compareByDescending<FolderEntry> { it.lastModified }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name.orEmpty() })

	/** A zip the person can pick from a folder; [uri] reads it under the folder's grant. */
	data class Pickable(val name: String, val uri: Uri)

	/** Lists the zips directly inside the picked folder [treeUri] with one provider query. Call off the main thread. */
	fun read(resolver: ContentResolver, treeUri: Uri): List<Pickable> {
		val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
		val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_LAST_MODIFIED)
		val entries = resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
			buildList<FolderEntry> {
				while (cursor.moveToNext()) {
					val id = cursor.getString(0) ?: continue
					add(FolderEntry(id, cursor.getString(1), cursor.getString(2), if (cursor.isNull(3)) 0L else cursor.getLong(3)))
				}
			}
		} ?: throw IllegalStateException("The folder could not be read")
		return select(entries).map { Pickable(it.name.orEmpty(), DocumentsContract.buildDocumentUriUsingTree(treeUri, it.documentId)) }
	}

	/** `DocumentsContract.Document.MIME_TYPE_DIR`, kept here so the selection runs without Android. */
	const val DIRECTORY_MIME_TYPE = "vnd.android.document/directory"
}
