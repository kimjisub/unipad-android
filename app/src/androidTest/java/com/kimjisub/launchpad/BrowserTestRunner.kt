package com.kimjisub.launchpad

import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import com.kimjisub.launchpad.tool.BrowserLinks

/** Keep browser visits from this instrumented process out of website analytics. */
class BrowserTestRunner : AndroidJUnitRunner() {
	override fun onCreate(arguments: Bundle?) {
		BrowserLinks.isTestRun = true
		super.onCreate(arguments)
	}
}
