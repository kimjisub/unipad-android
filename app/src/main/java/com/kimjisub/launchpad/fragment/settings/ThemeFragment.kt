package com.kimjisub.launchpad.fragment.settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.kimjisub.launchpad.R

class ThemeFragment : PreferenceFragmentCompat() {
	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.preferences_theme, rootKey)
	}
}