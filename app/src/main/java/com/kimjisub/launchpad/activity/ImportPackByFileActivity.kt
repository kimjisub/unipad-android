package com.kimjisub.launchpad.activity

import android.os.Bundle
import com.kimjisub.manager.Log

class ImportPackByFileActivity : BaseActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		Log.test("intent?.data?.path!!: " + intent?.data?.path!!)
// Todo
//		UniPackImporter(
//			context = this,
//			File(intent?.data?.path!!),
//			uniPackWorkspace
//		)
		finish()
	}
}