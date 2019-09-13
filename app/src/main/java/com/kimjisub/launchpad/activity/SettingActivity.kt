package com.kimjisub.launchpad.activity

import android.os.Bundle
import android.preference.PreferenceActivity
import com.kimjisub.launchpad.R.xml

class SettingActivity : PreferenceActivity() {
	public override fun onCreate(savedInstanceState: Bundle) {
		BaseActivity.startActivity(this)
		super.onCreate(savedInstanceState)
		addPreferencesFromResource(xml.setting)
	}
}