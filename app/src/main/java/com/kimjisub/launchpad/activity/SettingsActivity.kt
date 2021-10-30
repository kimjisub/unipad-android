package com.kimjisub.launchpad.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kimjisub.launchpad.fragment.HeaderFragment
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








}