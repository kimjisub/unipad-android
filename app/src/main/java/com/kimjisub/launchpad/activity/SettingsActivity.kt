package com.kimjisub.launchpad.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.kimjisub.launchpad.databinding.ActivitySettingsBinding
import com.kimjisub.launchpad.fragment.settings.AdsPaymentFragment
import com.kimjisub.launchpad.fragment.settings.HeaderFragment
import com.kimjisub.launchpad.fragment.settings.HeaderFragment.Companion.Category
import com.kimjisub.launchpad.fragment.settings.InfoFragment
import com.kimjisub.launchpad.fragment.settings.StorageFragment
import splitties.activities.start

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
					Category.THEME-> {
						start<ThemeActivity>()
						// todo 이런식으로 fragment 띄우지 않는 경우에는
						//  HeaderFragment 내부에서 select 값 바꾸지 않도록
						// ThemeFragment()
						null
					}
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