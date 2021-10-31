package com.kimjisub.launchpad.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.kimjisub.launchpad.databinding.ActivitySettingsBinding
import com.kimjisub.launchpad.fragment.*
import com.kimjisub.launchpad.fragment.HeaderFragment.Companion.Category
import com.kimjisub.launchpad.tool.Log

class SettingsActivity : AppCompatActivity() {

	lateinit var b: ActivitySettingsBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivitySettingsBinding.inflate(layoutInflater)
		setContentView(b.root)

		supportFragmentManager
			.beginTransaction()
			.replace(b.category.id, HeaderFragment{
				val fragment = when(it){
					Category.INFO-> InfoFragment()
					Category.ADS_PAYMENT-> AdsPaymentFragment()
					Category.STORAGE-> StorageFragment()
					Category.THEME-> ThemeFragment()
					else-> null
				}

				if (fragment != null) {
					supportFragmentManager.beginTransaction()
						.replace(b.settings.id, fragment)
						.commit()
				}
			})
			.commit()
	}

	override fun onSupportNavigateUp(): Boolean {
		if (supportFragmentManager.popBackStackImmediate()) {
			return true
		}
		return super.onSupportNavigateUp()
	}
}