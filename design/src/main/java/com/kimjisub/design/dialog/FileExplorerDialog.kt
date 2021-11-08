package com.kimjisub.design.dialog

import android.R.layout
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog.Builder
import com.kimjisub.design.R.string
import com.kimjisub.design.databinding.LayoutFileExplorerBinding
import java.io.File
import java.util.*

class FileExplorerDialog(
	internal var context: Context,
	internal var path: String,
	private val onEventListener: OnEventListener,
) {
	private val dialog = Builder(context).create()
	private var fileExplorerLayout = LayoutFileExplorerBinding.inflate(
		LayoutInflater.from(context)
	)

	private var mItem: MutableList<String> = ArrayList()
	private var mPath: MutableList<String> = ArrayList()


	fun show() {
		fileExplorerLayout.list.onItemClickListener =
			OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
				val file = File(mPath[position])
				if (file.isDirectory) {
					if (file.canRead())
						moveDir(mPath[position])
					else
						showDialog(file.name, getString(string.FE_folderErr))
				} else {
					if (file.canRead())
						onFileSelected(file.path)
					else
						showDialog(file.name, getString(string.FE_fileErr))
				}
			}
		moveDir(path)
		dialog.setView(fileExplorerLayout.root)
		dialog.show()
	}

	private fun moveDir(dirPath: String) {
		onPathChanged(dirPath)
		fileExplorerLayout.path.text = dirPath
		mItem.clear()
		mPath.clear()
		val f = File(dirPath)
		val files: Array<File> = f.listFiles()
		if (dirPath != "/") {
			mItem.add("../")
			mPath.add(f.parent)
		}
		for (file in files) {
			val name: String = file.name
			if (name.indexOf('.') != 0) {
				if (file.isDirectory) {
					mPath.add(file.path)
					mItem.add("$name/")
				} else if (name.lastIndexOf(".zip") == name.length - 4 || name.lastIndexOf(".uni") == name.length - 4) {
					mPath.add(file.path)
					mItem.add(file.name)
				}
			}
		}
		val fileList = ArrayAdapter(context, layout.simple_list_item_1, mItem)
		fileExplorerLayout.list.adapter = fileList
	}


	// Listener


	interface OnEventListener {
		fun onFileSelected(filePath: String)
		fun onPathChanged(folderPath: String)
	}

	private fun onFileSelected(filePath: String) {
		onEventListener.onFileSelected(filePath)
	}

	private fun onPathChanged(folderPath: String) {
		onEventListener.onPathChanged(folderPath)
	}

	// view

	private fun getString(id: Int): String {
		return context.getString(id)
	}

	private fun showDialog(title: String?, content: String) {
		Builder(context)
			.setTitle(title)
			.setMessage(content)
			.setPositiveButton(getString(string.FE_accept), null)
			.show()
	}

}