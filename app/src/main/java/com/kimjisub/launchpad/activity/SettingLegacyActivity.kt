package com.kimjisub.launchpad.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.preference.*
import com.google.firebase.messaging.FirebaseMessaging
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.adapter.DialogListAdapter
import com.kimjisub.launchpad.adapter.DialogListItem
import com.kimjisub.launchpad.databinding.ActivitySettingLegacyBinding
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.putClipboard
import com.kimjisub.launchpad.tool.splitties.browse
import splitties.activities.start
import splitties.toast.toast
import java.util.*

class SettingLegacyActivity : BaseActivity() {
	private lateinit var b: ActivitySettingLegacyBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivitySettingLegacyBinding.inflate(layoutInflater)
		setContentView(b.root)


		if (savedInstanceState == null) {
			supportFragmentManager
				.beginTransaction()
				.add(R.id.frame, SettingsFragment.newInstance())
				.commit()
		}
	}

	class SettingsFragment : PreferenceFragmentCompat() {
		private lateinit var settingLegacyActivity: SettingLegacyActivity
		private lateinit var p: PreferenceManager

		// Preferences
		private lateinit var selectThemePreference: Preference
		private lateinit var storageLocationPreference: Preference
		private lateinit var storageLocationTestPreference: Preference
		private lateinit var githubPreference: Preference
		private lateinit var communityPreference: Preference
		private lateinit var openSourceLicensePreference: Preference
		private lateinit var fcmTokenPreference: Preference
		private lateinit var languagePreference: Preference
		private lateinit var copyrightPreference: Preference

		// https://developer.android.com/training/data-storage/shared/documents-files#perform-operations
		private val folderResultLauncher =
			registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
				if (result.resultCode == Activity.RESULT_OK) {
					// There are no request codes
					val data: Intent? = result.data
					data?.data.also { uri ->
						if (uri != null) {
							val contentResolver = requireContext().contentResolver

							val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
									Intent.FLAG_GRANT_WRITE_URI_PERMISSION
							// Check for the freshest data.
							contentResolver.takePersistableUriPermission(uri, takeFlags)
							for (persistedUriPermission in contentResolver.persistedUriPermissions) {
								// Log.test(persistedUriPermission.uri.path + " " + persistedUriPermission.persistedTime)
							}


						}
					}

				}
			}

		// PreferenceFragmentCycle

		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			addPreferencesFromResource(R.xml.setting)
			settingLegacyActivity = activity as SettingLegacyActivity
			p = settingLegacyActivity.p

			initPreference()
			addPreferenceListener()
		}


		override fun setPreferenceScreen(preferenceScreen: PreferenceScreen?) {
			super.setPreferenceScreen(preferenceScreen)

			if (preferenceScreen != null) {
				for (i in 0 until preferenceScreen.preferenceCount)
					removeIconSpace(preferenceScreen.getPreference(i))
			}
		}

		override fun onResume() {
			super.onResume()
			setPreferenceValues()
		}

		override fun onDestroy() {
			super.onDestroy()
		}

		//

		private fun initPreference() {
			selectThemePreference = findPreference("selectTheme")!!
			storageLocationPreference = findPreference("storageLocation")!!
			storageLocationTestPreference = findPreference("storageLocationTest")!!
			githubPreference = findPreference("github")!!
			communityPreference = findPreference("community")!!
			openSourceLicensePreference = findPreference("openSourceLicense")!!
			fcmTokenPreference = findPreference("fcmToken")!!
			languagePreference = findPreference("language")!!
			copyrightPreference = findPreference("copyright")!!
		}

		private fun addPreferenceListener() {
			selectThemePreference.onPreferenceClickListener =
				Preference.OnPreferenceClickListener {
					requireContext().start<ThemeActivity>()
					false
				}


			storageLocationPreference.onPreferenceClickListener =
				Preference.OnPreferenceClickListener {
					val list = settingLegacyActivity.ws.availableWorkspaces
					val listView = ListView(context)
					val data = list
						.map { DialogListItem(it.name, it.file.path) }
						.toTypedArray()

					listView.adapter = DialogListAdapter(data)
					listView.onItemClickListener =
						AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
							/*p.storageIndex = list[position].index // todo 다른 방식으로 저장하기
							storageLocationPreference.summary =
								settingActivity.workspace.uniPackWorkspace.absolutePath
							// todo 다이얼로그 닫히게
							// todo 유니팩 복사 진행*/
						}
					val builder = AlertDialog.Builder(context)
					builder.setTitle(getString(R.string.storage_location))
					builder.setView(listView)
					builder.show()


					false
				}

			storageLocationTestPreference.onPreferenceClickListener =
				Preference.OnPreferenceClickListener {


					// Choose a directory using the system's file picker.
					val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
						// Provide read access to files and sub-directories in the user-selected
						// directory.
						flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

						// Optionally, specify a URI for the directory that should be opened in
						// the system file picker when it loads.
						if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
							// putExtra(DocumentsContract.EXTRA_INITIAL_URI, pickerInitialUri)
						}
						putExtra("android.content.extra.SHOW_ADVANCED", true)
					}

					folderResultLauncher.launch(intent)

					false
				}

			githubPreference.onPreferenceClickListener =
				Preference.OnPreferenceClickListener {
					requireContext().browse("https://github.com/kimjisub/unipad-android")
					false
				}

			communityPreference.onPreferenceClickListener =
				Preference.OnPreferenceClickListener {
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
							"http://cafe.naver.com/unipad",
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
							"http://qr.kakao.com/talk/R4p8KwFLXRZsqEjA1FrAnACDyfc-",
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
					val data = ArrayList<DialogListItem>()
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
			openSourceLicensePreference.onPreferenceClickListener =
				Preference.OnPreferenceClickListener {
					class Item(
						val title: String,
						val subtitle: String,
						val url: String,
					) {
						constructor(
							title: Int,
							purchaseId: String,
						) : this(getString(title), purchaseId, purchaseId)

						constructor(
							title: Int,
							subtitle: Int,
							url: String,
						) : this(getString(title), getString(subtitle), url)

						fun toListItem() = DialogListItem(title, subtitle)
					}

					val list = arrayOf(
						Item(
							"CarouselLayoutManager",
							"Apache License 2.0",
							"https://github.com/Azoft/CarouselLayoutManager"
						),
						Item(
							"FloatingActionButton",
							"Apache License 2.0",
							"https://github.com/Clans/FloatingActionButton"
						),
						Item(
							"TedPermission",
							"Apache License 2.0",
							"https://github.com/ParkSangGwon/TedPermission"
						),
						Item(
							"Android In-App Billing v3 Library",
							"Apache License 2.0",
							"https://github.com/anjlab/android-inapp-billing-v3"
						),
						Item(
							"Retrofit",
							"Apache License 2.0",
							"https://github.com/square/retrofit"
						),
						Item(
							"Carousel View",
							"Apache License 2.0",
							"https://github.com/alirezat775/carousel-view"
						)
					)
					val listView = ListView(context)
					val data = ArrayList<DialogListItem>()
					for (i in list.indices) data.add(list[i].toListItem())
					listView.adapter = DialogListAdapter(data.toTypedArray())
					listView.onItemClickListener =
						AdapterView.OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
							requireContext().browse(list[position].url)
						}
					val builder = AlertDialog.Builder(context)
					builder.setTitle(getString(R.string.openSourceLicense))
					builder.setView(listView)
					builder.show()
					false
				}
			fcmTokenPreference.onPreferenceClickListener =
				Preference.OnPreferenceClickListener { preference: Preference? ->
					try {
						putClipboard(
							FirebaseMessaging.getInstance().token.result
						)
						context?.toast(R.string.copied)
					} catch (e: Exception) {
					}
					false
				}
		}

		private fun setPreferenceValues() {
			selectThemePreference.summary = p.selectedTheme
			storageLocationPreference.summary =
				settingLegacyActivity.ws.mainWorkspace.file.path

			val systemLocale: Locale = activity?.application?.resources?.configuration?.locale!!
			val displayCountry: String = systemLocale.displayCountry //국가출력

			val country: String = systemLocale.country // 국가 코드 출력 ex) KR

			val language: String = systemLocale.language // 언어 코드 출력 ex) ko

			languagePreference.title =
				getString(R.string.language) + " (" + getString(R.string.languageCode) + ")"
			languagePreference.summary =
				"$displayCountry ($country) - $language"
			copyrightPreference.summary =
				getString(R.string.translated_by)
		}

		private fun removeIconSpace(preference: Preference?) {
			if (preference != null) {
				preference.isIconSpaceReserved = false
				if (preference is PreferenceCategory)
					for (i in 0 until preference.preferenceCount)
						removeIconSpace(preference.getPreference(i))
			}
		}


		companion object {
			fun newInstance(rootKey: String = "root") =
				SettingsFragment().apply {
					arguments = Bundle().apply { putString(ARG_PREFERENCE_ROOT, rootKey) }
				}
		}
	}

}

