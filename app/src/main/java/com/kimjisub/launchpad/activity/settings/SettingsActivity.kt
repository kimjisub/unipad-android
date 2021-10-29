package com.kimjisub.launchpad.activity.settings

import android.os.Bundle
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kimjisub.launchpad.R.xml
import com.kimjisub.launchpad.activity.BaseActivity
import com.kimjisub.launchpad.databinding.ActivitySettingsBinding

class SettingsActivity : BaseActivity(),
	PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
	private lateinit var b: ActivitySettingsBinding


	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivitySettingsBinding.inflate(layoutInflater)
		setContentView(b.root)

		val categoryFragment = CategoryFragment {
			supportFragmentManager.commit {
				replace(b.settings.id, it)
				// addToBackStack(null)
				setReorderingAllowed(true)
			}
		}

		supportFragmentManager.commit {


			replace(b.category.id, categoryFragment)
			setReorderingAllowed(true)
		}
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
		supportFragmentManager.commit {
			replace(b.settings.id, fragment)
			// addToBackStack(null)
			setReorderingAllowed(true)
		}
		title = pref.title
		return true
	}



	class MessagesFragment : PreferenceFragmentCompat() {
		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			setPreferencesFromResource(xml.messages_preferences, rootKey)
		}

	}

	class SyncFragment : PreferenceFragmentCompat() {
		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			setPreferencesFromResource(xml.sync_preferences, rootKey)
		}
	}
}