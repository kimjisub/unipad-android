package com.kimjisub.launchpad.fragment.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.kimjisub.launchpad.tool.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.adapter.DialogListAdapter
import com.kimjisub.launchpad.adapter.DialogListItem
import com.kimjisub.launchpad.manager.putClipboard
import splitties.activities.start
import com.google.android.material.snackbar.Snackbar

class InfoFragment : PreferenceFragmentCompat() {
	private val appPreference: Preference by lazy { checkNotNull(findPreference("app")) }
	private val fcmTokenPreference: Preference by lazy { checkNotNull(findPreference("fcm_token")) }
	private val githubPreference: Preference by lazy { checkNotNull(findPreference("github")) }
	private val ossLicensePreference: Preference by lazy { checkNotNull(findPreference("oss_license")) }
	private val communityPreference: Preference by lazy { checkNotNull(findPreference("community")) }

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		setPreferencesFromResource(R.xml.preferences_info, rootKey)

		val context = requireContext()

		val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
		} else {
			@Suppress("DEPRECATION")
			context.packageManager.getPackageInfo(context.packageName, 0)
		}
		val appName = getString(R.string.app_name)
		val versionName = packageInfo.versionName
		val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
		appPreference.title = "$appName $versionName ($versionCode)"

		fcmTokenPreference.setOnPreferenceClickListener {
			try {
				FirebaseMessaging.getInstance().token.addOnCompleteListener {
					putClipboard(it.result)
					Snackbar.make(requireView(), R.string.copied, Snackbar.LENGTH_SHORT).show()
				}
			} catch (e: IllegalStateException) {
				Log.err("FCM token copy failed", e)
				Snackbar.make(requireView(), e.toString(), Snackbar.LENGTH_SHORT).show()
			}
			false
		}

		githubPreference.setOnPreferenceClickListener {
			startActivity(
				Intent(
					Intent.ACTION_VIEW,
					"https://github.com/kimjisub/unipad-android".toUri()
				)
			)

			false
		}

		ossLicensePreference.setOnPreferenceClickListener {
			requireActivity().start<OssLicensesMenuActivity>()
			false
		}
		communityPreference.setOnPreferenceClickListener {
			class Item(
				val title: String,
				val subtitle: String,
				val iconResId: Int,
				val url: String,
				val action: String,
			) {
				constructor(
					title: Int,
					subtitle: Int,
					iconResId: Int,
					url: String,
					action: String,
				) : this(getString(title), getString(subtitle), iconResId, url, action)

				fun toListItem() = DialogListItem(title, subtitle, iconResId)
			}

			val list = arrayOf(
				Item(
					R.string.officialHomepage,
					R.string.officialHomepage_,
					R.drawable.community_web,
					"https://unipad.io",
					Intent.ACTION_VIEW
				),
				Item(
					R.string.officialFacebook,
					R.string.officialFacebook_,
					R.drawable.community_facebook,
					"https://www.facebook.com/playunipad",
					Intent.ACTION_VIEW
				),
				Item(
					R.string.facebookCommunity,
					R.string.facebookCommunity_,
					R.drawable.community_facebook_group,
					"https://www.facebook.com/groups/playunipad",
					Intent.ACTION_VIEW
				),
				Item(
					R.string.naverCafe,
					R.string.naverCafe_,
					R.drawable.community_cafe,
					"https://cafe.naver.com/unipad",
					Intent.ACTION_VIEW
				),
				Item(
					R.string.discord,
					R.string.discord_,
					R.drawable.community_discord,
					"https://discord.gg/ESDgyNs",
					Intent.ACTION_VIEW
				),
				Item(
					R.string.kakaotalk,
					R.string.kakaotalk_,
					R.drawable.community_kakaotalk,
					"https://qr.kakao.com/talk/R4p8KwFLXRZsqEjA1FrAnACDyfc-",
					Intent.ACTION_VIEW
				),
				Item(
					R.string.email,
					R.string.email_,
					R.drawable.community_mail,
					"mailto:0226unipad@gmail.com",
					Intent.ACTION_SENDTO
				)
			)
			val listView = ListView(context)
			val data = mutableListOf<DialogListItem>()
			for (i in list.indices) data.add(list[i].toListItem())
			listView.adapter = DialogListAdapter(data.toTypedArray())
			listView.onItemClickListener =
				AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
					startActivity(Intent(list[position].action, list[position].url.toUri()))
				}
			val builder = AlertDialog.Builder(context)
			builder.setTitle(getString(R.string.community))
			builder.setView(listView)
			builder.show()
			false
		}

	}
}