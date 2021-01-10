package com.kimjisub.launchpad.fragment

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import androidx.core.net.toUri
import androidx.preference.*
import com.anjlab.android.iab.v3.BillingProcessor
import com.anjlab.android.iab.v3.TransactionDetails
import com.google.firebase.iid.FirebaseInstanceId
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.activity.ThemeActivity
import com.kimjisub.launchpad.adapter.DialogListAdapter
import com.kimjisub.launchpad.adapter.DialogListItem
import com.kimjisub.launchpad.manager.BillingManager
import com.kimjisub.launchpad.manager.Constant
import com.kimjisub.launchpad.manager.Functions
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.manager.Log
import com.kimjisub.manager.splitties.browse
import splitties.activities.start
import splitties.toast.toast
import java.util.*

class SettingFragment : PreferenceFragmentCompat() {
	private lateinit var bm: BillingManager
	private val preference: PreferenceManager by lazy { PreferenceManager(requireContext()) }

	override fun setPreferenceScreen(preferenceScreen: PreferenceScreen?) {
		super.setPreferenceScreen(preferenceScreen)

		if (preferenceScreen != null) {
			for (i in 0 until preferenceScreen.preferenceCount)
				removeIconSpace(preferenceScreen.getPreference(i))
		}
	}

	private fun removeIconSpace(preference: Preference?) {
		if (preference != null) {
			preference.isIconSpaceReserved = false
			if (preference is PreferenceCategory)
				for (i in 0 until preference.preferenceCount)
					removeIconSpace(preference.getPreference(i))
		}
	}

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(xml.setting)
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

		findPreference<Preference>("select_theme")?.onPreferenceClickListener =
			Preference.OnPreferenceClickListener {
				requireContext().start<ThemeActivity>()
				false
			}
		findPreference<Preference>("change_storage_location")?.onPreferenceClickListener =
			Preference.OnPreferenceClickListener {
				val i = Intent()
				i.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
				i.addCategory(Intent.CATEGORY_DEFAULT)
				i.data = Uri.parse("package:" + requireContext().packageName)
				i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
				i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
				i.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
				startActivity(i)
				false
			}
		findPreference<Preference>("community")?.onPreferenceClickListener =
			Preference.OnPreferenceClickListener {

				Log.test("test: ${getString(string.officialFacebook)}")

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
						string.officialHomepage,
						string.officialHomepage_,
						drawable.community_web,
						"https://unipad.io",
						Intent.ACTION_VIEW
					),
					Item(
						string.officialFacebook,
						string.officialFacebook_,
						drawable.community_facebook,
						"https://www.facebook.com/playunipad",
						Intent.ACTION_VIEW
					),
					Item(
						string.facebookCommunity,
						string.facebookCommunity_,
						drawable.community_facebook_group,
						"https://www.facebook.com/groups/playunipad",
						Intent.ACTION_VIEW
					),
					Item(
						string.naverCafe,
						string.naverCafe_,
						drawable.community_cafe,
						"http://cafe.naver.com/unipad",
						Intent.ACTION_VIEW
					),
					Item(
						string.discord,
						string.discord_,
						drawable.community_discord,
						"https://discord.gg/ESDgyNs",
						Intent.ACTION_VIEW
					),
					Item(
						string.kakaotalk,
						string.kakaotalk_,
						drawable.community_kakaotalk,
						"http://qr.kakao.com/talk/R4p8KwFLXRZsqEjA1FrAnACDyfc-",
						Intent.ACTION_VIEW
					),
					Item(
						string.email,
						string.email_,
						drawable.community_mail,
						"mailto:0226unipad@gmail.com",
						Intent.ACTION_SENDTO
					)
				)
				val listView = ListView(context)
				val data = ArrayList<DialogListItem>()
				for (i in list.indices) data.add(list[i].toListItem())
				listView.adapter = DialogListAdapter(data)
				listView.onItemClickListener =
					AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
						startActivity(Intent(list[position].action, list[position].url.toUri()))
					}
				val builder = AlertDialog.Builder(context)
				builder.setTitle(getString(string.community))
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
				listView.adapter = DialogListAdapter(data)
				listView.onItemClickListener =
					AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
						bm.purchase(
							list[position].purchaseId
						)
					}
				val builder = AlertDialog.Builder(context)
				builder.setTitle(getString(string.donation))
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
					Item("Retrofit", "Apache License 2.0", "https://github.com/square/retrofit"),
					Item(
						"Carousel View",
						"Apache License 2.0",
						"https://github.com/alirezat775/carousel-view"
					)
				)
				val listView = ListView(context)
				val data = ArrayList<DialogListItem>()
				for (i in list.indices) data.add(list[i].toListItem())
				listView.adapter = DialogListAdapter(data)
				listView.onItemClickListener =
					AdapterView.OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
						requireContext().browse(list[position].url)
					}
				val builder = AlertDialog.Builder(context)
				builder.setTitle(getString(string.openSourceLicense))
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
					context?.toast(string.copied)
				} catch (ignore: Exception) {
				}
				false
			}
	}

	internal fun updateBilling() {
		(findPreference<Preference>("pro") as CheckBoxPreference).isChecked =
			bm.isPro
	}


	override fun onResume() {
		findPreference<Preference>("select_theme")?.summary = preference.selectedTheme
		findPreference<Preference>("FCMToken")?.summary = FirebaseInstanceId.getInstance().token
		val systemLocale: Locale = activity?.application?.resources?.configuration?.locale!!
		val displayCountry: String = systemLocale.displayCountry //국가출력

		val country: String = systemLocale.country // 국가 코드 출력 ex) KR

		val language: String = systemLocale.language // 언어 코드 출력 ex) ko

		findPreference<Preference>("language")?.title =
			getString(string.language) + " (" + getString(string.languageCode) + ")"
		findPreference<Preference>("language")?.summary = "$displayCountry ($country) - $language"
		findPreference<Preference>("copyright")?.summary =
			String.format(getString(string.translatedBy), getString(string.translator))
		super.onResume()
	}

	override fun onDestroy() {
		super.onDestroy()
		bm.release()
	}
}