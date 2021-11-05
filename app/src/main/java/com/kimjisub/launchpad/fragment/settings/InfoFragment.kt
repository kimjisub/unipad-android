package com.kimjisub.launchpad.fragment.settings

import android.os.Bundle
import androidx.core.content.pm.PackageInfoCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.google.firebase.messaging.FirebaseMessaging
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.manager.putClipboard
import splitties.activities.start
import splitties.toast.toast

class InfoFragment : PreferenceFragmentCompat() {
	private val appPreference: Preference by lazy { findPreference("app")!! }
	private val fcmTokenPreference: Preference by lazy { findPreference("fcm_token")!! }
	private val ossLicencePreference: Preference by lazy { findPreference("oss_licence")!! }

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.preferences_info, rootKey)

		val context = requireContext()

		val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0);
		val appName = getString(R.string.app_name)
		val versionName = packageInfo.versionName
		val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
		appPreference.title = "$appName $versionName ($versionCode)"

		fcmTokenPreference.setOnPreferenceClickListener {
			try {
				FirebaseMessaging.getInstance().token.addOnCompleteListener {
					putClipboard(it.result)
					toast(R.string.copied)
				}
			} catch (e: Exception) {
				e.printStackTrace()
				toast(e.toString())
			}
			false
		}

		ossLicencePreference.setOnPreferenceClickListener {
			requireActivity().start<OssLicensesMenuActivity>()
			true
		}
	}
}