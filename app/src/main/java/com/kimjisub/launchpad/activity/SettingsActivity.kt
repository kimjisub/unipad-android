package com.kimjisub.launchpad.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kimjisub.launchpad.R.xml
import com.kimjisub.launchpad.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity(),
	PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

	lateinit var b: ActivitySettingsBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivitySettingsBinding.inflate(layoutInflater)
		setContentView(b.root)

		supportFragmentManager
			.beginTransaction()
			.replace(b.category.id, HeaderFragment())
			.commit()
	}

	override fun onSupportNavigateUp(): Boolean {
		if (supportFragmentManager.popBackStackImmediate()) {
			return true
		}
		return super.onSupportNavigateUp()
	}

	override fun onPreferenceStartFragment(
		caller: PreferenceFragmentCompat,
		pref: Preference
	): Boolean {
		// Instantiate the new Fragment
		val args = pref.extras
		val fragment = supportFragmentManager.fragmentFactory.instantiate(
			classLoader,
			pref.fragment
		).apply {
			arguments = args
			setTargetFragment(caller, 0)
		}
		// Replace the existing Fragment with the new Fragment
		supportFragmentManager.beginTransaction()
			.replace(b.settings.id, fragment)
			//.addToBackStack(null)
			.commit()
		title = pref.title
		return true
	}

	class HeaderFragment : PreferenceFragmentCompat() {
		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			setPreferencesFromResource(xml.preferences_header, rootKey)
		}
	}

	class InfoFragment : PreferenceFragmentCompat() {
		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			setPreferencesFromResource(xml.preferences_info, rootKey)
		}
	}

	class AdsPaymentFragment : PreferenceFragmentCompat() {
		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			setPreferencesFromResource(xml.preferences_ads_payment, rootKey)
		}
	}

	class StorageFragment : PreferenceFragmentCompat() {
		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			setPreferencesFromResource(xml.preferences_storage, rootKey)
		}
	}

	class ThemeFragment : PreferenceFragmentCompat() {
		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			setPreferencesFromResource(xml.preferences_theme, rootKey)
		}
	}
}