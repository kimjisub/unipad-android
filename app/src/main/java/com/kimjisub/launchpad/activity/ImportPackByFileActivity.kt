package com.kimjisub.launchpad.activity

import android.os.Bundle
import com.kimjisub.launchpad.tool.UniPackImporter
import com.kimjisub.manager.Log
import java.io.File

class ImportPackByFileActivity : BaseActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		Log.test("intent?.data?.path!!: " + intent?.data?.path!!)

		UniPackImporter(
			context = this,
			File(intent?.data?.path!!),
			uniPackExt
		)
		finish()
	}
}