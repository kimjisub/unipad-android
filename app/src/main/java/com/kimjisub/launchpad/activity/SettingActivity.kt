package com.kimjisub.launchpad.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import androidx.core.net.toUri
import androidx.preference.*
import com.anjlab.android.iab.v3.BillingProcessor
import com.anjlab.android.iab.v3.TransactionDetails
import com.google.firebase.iid.FirebaseInstanceId
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.adapter.DialogListAdapter
import com.kimjisub.launchpad.adapter.DialogListItem
import com.kimjisub.launchpad.databinding.ActivitySettingBinding
import com.kimjisub.launchpad.manager.BillingManager
import com.kimjisub.launchpad.manager.Constant
import com.kimjisub.launchpad.manager.Functions
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.manager.Log
import com.kimjisub.manager.splitties.browse
import splitties.activities.start
import splitties.toast.toast
import java.util.*

class SettingActivity : BaseActivity() {
	private lateinit var b: ActivitySettingBinding
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		b = ActivitySettingBinding.inflate(layoutInflater)
		setContentView(b.root)

		if (savedInstanceState == null) {
			supportFragmentManager
				.beginTransaction()
				.add(R.id.frame, SettingsFragment.newInstance())
				.commit()
		}
	}

	class SettingsFragment : PreferenceFragmentCompat() {
		private lateinit var settingActivity: SettingActivity
		private lateinit var p: PreferenceManager
		private lateinit var bm: BillingManager


		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			addPreferencesFromResource(R.xml.setting)
			settingActivity = activity as SettingActivity
			p = settingActivity.p
			bm = BillingManager(requireActivity(), object : BillingProcessor.IBillingHandler {
				override fun onProductPurchased(productId: String, details: TransactionDetails?) {
					updateBilling()
				}

				override fun onPurchaseHistoryRestored() {}
				override fun onBillingError(errorCode: Int, error: Throwable?) {}
				override fun onBillingInitialized() {
					updateBilling()
				}
			})
			bm.initialize()

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
			setPreferenceValues()
			super.onResume()
		}

		override fun onDestroy() {
			super.onDestroy()
			bm.release()
		}

		private fun addPreferenceListener() {
			findPreference<Preference>("select_theme")?.onPreferenceClickListener =
				Preference.OnPreferenceClickListener {
					requireContext().start<ThemeActivity>()
					false
				}


			findPreference<Preference>("storage_location")?.onPreferenceClickListener =
				Preference.OnPreferenceClickListener {
					val list = settingActivity.getUniPackWorkspaces()
					val listView = ListView(context)
					val data = list
						.map { DialogListItem(it.name, it.file.absolutePath) }
						.toTypedArray()

					listView.adapter = DialogListAdapter(data)
					listView.onItemClickListener =
						AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
							p.storageIndex = list[position].index
							findPreference<Preference>("storage_location")?.summary =
								settingActivity.uniPackWorkspace.absolutePath
							// todo 다이얼로그 닫히게
							// todo 유니팩 복사 진행
						}
					val builder = AlertDialog.Builder(context)
					builder.setTitle(getString(R.string.storage_location))
					builder.setView(listView)
					builder.show()
					false
				}

			findPreference<Preference>("storage_location")?.onPreferenceChangeListener =
				Preference.OnPreferenceChangeListener { _, newValue ->
					false
				}

			//		.onPreferenceClickListener =
			//			Preference.OnPreferenceClickListener {
			//
			//				false
			//			}
			findPreference<Preference>("community")?.onPreferenceClickListener =
				Preference.OnPreferenceClickListener {
					class Item(
						val title: String,
						val subtitle: String,
						val iconResId: Int,
						val url: String,
						val action: String
					) {
						constructor(
							title: Int,
							subtitle: Int,
							iconResId: Int,
							url: String,
							action: String
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
			findPreference<Preference>("donation")?.onPreferenceClickListener =
				Preference.OnPreferenceClickListener { preference: Preference? ->
					class Item(
						val title: String,
						val subtitle: String,
						val purchaseId: String
					) {
						constructor(
							title: Int,
							purchaseId: String
						) : this(getString(title), purchaseId, purchaseId)

						constructor(
							title: String,
							purchaseId: String
						) : this(title, purchaseId, purchaseId)

						fun toListItem() = DialogListItem(title, subtitle)
					}

					val list = arrayOf(
						Item("Donate $1", Constant.BILLING.DONATE_1),
						Item("Donate $5", Constant.BILLING.DONATE_5),
						Item("Donate $10", Constant.BILLING.DONATE_10),
						Item("Donate $50", Constant.BILLING.DONATE_50)
					)
					val listView = ListView(context)
					val data = ArrayList<DialogListItem>()
					for (i in list.indices) data.add(list[i].toListItem())
					listView.adapter = DialogListAdapter(data.toTypedArray())
					listView.onItemClickListener =
						AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
							bm.purchase(
								list[position].purchaseId
							)
						}
					val builder = AlertDialog.Builder(context)
					builder.setTitle(getString(R.string.donation))
					builder.setView(listView)
					builder.show()
					false
				}
			findPreference<Preference>("pro")?.onPreferenceClickListener =
				Preference.OnPreferenceClickListener { preference: Preference ->
					(preference as CheckBoxPreference).isChecked = bm.isPro
					bm.subscribePro()
					false
				}
			findPreference<Preference>("restoreBilling")?.onPreferenceClickListener =
				Preference.OnPreferenceClickListener {
					bm.loadOwnedPurchasesFromGoogle()
					false
				}
			findPreference<Preference>("OpenSourceLicense")?.onPreferenceClickListener =
				Preference.OnPreferenceClickListener {
					class Item(
						val title: String,
						val subtitle: String,
						val url: String
					) {
						constructor(
							title: Int,
							purchaseId: String
						) : this(getString(title), purchaseId, purchaseId)

						constructor(
							title: Int,
							subtitle: Int,
							url: String
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
			findPreference<Preference>("FCMToken")?.onPreferenceClickListener =
				Preference.OnPreferenceClickListener { preference: Preference? ->
					try {
						Functions.putClipboard(
							requireContext(),
							FirebaseInstanceId.getInstance().token!!
						)
						context?.toast(R.string.copied)
					} catch (ignore: Exception) {
					}
					false
				}
		}

		private fun setPreferenceValues() {
			findPreference<Preference>("select_theme")?.summary = p.selectedTheme
			findPreference<Preference>("storage_location")?.summary =
				settingActivity.uniPackWorkspace.absolutePath

			findPreference<Preference>("FCMToken")?.summary = FirebaseInstanceId.getInstance().token
			val systemLocale: Locale = activity?.application?.resources?.configuration?.locale!!
			val displayCountry: String = systemLocale.displayCountry //국가출력

			val country: String = systemLocale.country // 국가 코드 출력 ex) KR

			val language: String = systemLocale.language // 언어 코드 출력 ex) ko

			findPreference<Preference>("language")?.title =
				getString(R.string.language) + " (" + getString(R.string.languageCode) + ")"
			findPreference<Preference>("language")?.summary =
				"$displayCountry ($country) - $language"
			findPreference<Preference>("copyright")?.summary =
				String.format(getString(R.string.translatedBy), getString(R.string.translator))
		}

		private fun removeIconSpace(preference: Preference?) {
			if (preference != null) {
				preference.isIconSpaceReserved = false
				if (preference is PreferenceCategory)
					for (i in 0 until preference.preferenceCount)
						removeIconSpace(preference.getPreference(i))
			}
		}

		internal fun updateBilling() {
			(findPreference<Preference>("pro") as CheckBoxPreference).isChecked =
				bm.isPro
		}

		companion object {
			fun newInstance(rootKey: String = "root") =
				SettingsFragment().apply {
					arguments = Bundle().apply { putString(ARG_PREFERENCE_ROOT, rootKey) }
				}
		}
	}
}