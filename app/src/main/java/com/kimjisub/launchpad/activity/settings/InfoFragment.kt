package com.kimjisub.launchpad.activity.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.oss.licenses.OssLicensesActivity
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.kimjisub.launchpad.R
import splitties.activities.start

class InfoFragment : PreferenceFragmentCompat() {
	private val ossLicencePreferenceCategory: Preference by lazy { findPreference("oss_licence")!! }

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.preferences_info, rootKey)

		ossLicencePreferenceCategory.setOnPreferenceClickListener {
			requireActivity().start<OssLicensesMenuActivity>()
			true
		}
	}
}