package com.kimjisub.launchpad.manager

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.IOException

/**
 * Lists folders of a backup folder for copies that must not miss anything.
 *
 * [androidx.documentfile.provider.DocumentFile.listFiles] catches every error and returns what it
 * had listed so far, often nothing, so an unreadable folder looked empty and a move deleted the
 * backup with it. These listings throw instead: when the documents provider returns no cursor, fails
 * part way, reports [DocumentsContract.EXTRA_ERROR] or is still loading
 * ([DocumentsContract.EXTRA_LOADING]).
 *
 * A provider that lists a folder it cannot read as empty (the system's local storage provider
 * does) cannot be told apart from a truly empty folder.
 */
object DocumentTree {

	class Entry(val uri: Uri, val name: String, val isDirectory: Boolean)

	private val CHILD_COLUMNS = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)

	/** The entries in the folder [dir]: a tree document URI, or a `file:` URI. */
	fun list(context: Context, dir: Uri): List<Entry> {
		if (dir.scheme == ContentResolver.SCHEME_FILE) {
			return FileManager.listEntries(File(requireNotNull(dir.path))).map { Entry(Uri.fromFile(it), it.name, it.isDirectory) }
		}
		val children = DocumentsContract.buildChildDocumentsUriUsingTree(dir, DocumentsContract.getDocumentId(dir))
		val cursor = context.contentResolver.query(children, CHILD_COLUMNS, null, null, null)
			?: throw IOException("Could not read the folder $dir")
		return cursor.use {
			it.extras?.getString(DocumentsContract.EXTRA_ERROR)?.let { error -> throw IOException("Could not read the folder $dir: $error") }
			if (it.extras?.getBoolean(DocumentsContract.EXTRA_LOADING) == true) throw IOException("The folder $dir is still loading")
			buildList {
				while (it.moveToNext()) {
					val id = it.getString(0)
					val name = it.getString(1) ?: throw IOException("An entry in $dir has no name")
					add(Entry(DocumentsContract.buildDocumentUriUsingTree(dir, id), name, it.getString(2) == Document.MIME_TYPE_DIR))
				}
			}
		}
	}
}
