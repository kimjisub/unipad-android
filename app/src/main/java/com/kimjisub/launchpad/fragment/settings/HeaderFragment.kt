package com.kimjisub.launchpad.fragment.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kimjisub.launchpad.R

class HeaderFragment(val callback: (Category) -> Unit) : PreferenceFragmentCompat() {
	private val infoPreference: Preference by lazy { findPreference("info")!! }
	private val storagePreference: Preference by lazy { findPreference("storage")!! }
	private val themePreference: Preference by lazy { findPreference("theme")!! }

	private val preferences by lazy {
		arrayOf(infoPreference, storagePreference, themePreference)
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.preferences_header, rootKey)

		select = 0
		preferences.forEachIndexed { index, preference ->
			preference.setOnPreferenceClickListener {
				select = index
				true
			}
		}
	}

	var select: Int = 0
		set(value) {
			field = value
			preferences.forEachIndexed { index, preference ->
				if (field == index) {
					callback(Category.valueOf(preference.key.uppercase()))
				}
			}
		}

	companion object {
		enum class Category(val key: String) {
			INFO("info"), STORAGE("storage"), THEME("theme")
		}
	}
}