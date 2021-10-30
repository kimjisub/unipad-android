package com.kimjisub.launchpad.activity.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.kimjisub.launchpad.R

class InfoFragment : PreferenceFragmentCompat() {
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.preferences_info, rootKey)
	}
}