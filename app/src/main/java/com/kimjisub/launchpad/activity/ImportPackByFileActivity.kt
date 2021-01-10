package com.kimjisub.launchpad.activity

import android.app.ProgressDialog
import android.os.Bundle
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.R.layout
import com.kimjisub.launchpad.tool.UniPackImporter
import com.kimjisub.launchpad.tool.UniPackInstaller
import com.kimjisub.launchpad.unipack.UniPack
import com.kimjisub.manager.FileManager
import com.kimjisub.manager.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.alertdialog.alertDialog
import splitties.alertdialog.message
import splitties.alertdialog.okButton
import splitties.alertdialog.title
import java.io.File

class ImportPackByFileActivity : BaseActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		Log.test("intent?.data?.path!!: " + intent?.data?.path!!)

		UniPackImporter(
			context = this,
			File(intent?.data?.path!!),
			F_UniPackRootExt
		)
		finish()
	}
}