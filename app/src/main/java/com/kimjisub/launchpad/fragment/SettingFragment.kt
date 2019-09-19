package com.kimjisub.launchpad.fragment

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import androidx.preference.*
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
import org.jetbrains.anko.toast
import java.util.*

class SettingFragment : PreferenceFragmentCompat() {
	private var billingManager: BillingManager? = null

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

		billingManager = BillingManager(activity, object : BillingManager.BillingEventListener {
			override fun onProductPurchased(productId: String, details: TransactionDetails?) {}
			override fun onPurchaseHistoryRestored() {}
			override fun onBillingError(errorCode: Int, error: Throwable?) {}
			override fun onBillingInitialized() {}
			override fun onRefresh() {
				updateBilling()
			}
		})


		findPreference<Preference>("select_theme")?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
			startActivity(Intent(context, ThemeActivity::class.java))
			false
		}
		findPreference<Preference>("community")?.onPreferenceClickListener = Preference.OnPreferenceClickListener {

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
				Item(string.officialHomepage, string.officialHomepage_, drawable.community_web, "https://unipad.io", Intent.ACTION_VIEW),
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
				Item(string.naverCafe, string.naverCafe_, drawable.community_cafe, "http://cafe.naver.com/unipad", Intent.ACTION_VIEW),
				Item(string.discord, string.discord_, drawable.community_discord, "https://discord.gg/ESDgyNs", Intent.ACTION_VIEW),
				Item(
					string.kakaotalk,
					string.kakaotalk_,
					drawable.community_kakaotalk,
					"http://qr.kakao.com/talk/R4p8KwFLXRZsqEjA1FrAnACDyfc-",
					Intent.ACTION_VIEW
				),
				Item(string.email, string.email_, drawable.community_mail, "mailto:0226unipad@gmail.com", Intent.ACTION_SENDTO)
			)
			val listView = ListView(context)
			val data = ArrayList<DialogListItem>()
			for (i in list.indices) data.add(list[i].toListItem())
			listView.adapter = DialogListAdapter(data)
			listView.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long ->
				startActivity(Intent(list[position].action, Uri.parse(list[position].url)))
			}
			val builder = AlertDialog.Builder(context)
			builder.setTitle(getString(string.community))
			builder.setView(listView)
			builder.show()
			false
		}
		findPreference<Preference>("donation")?.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference? ->
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
				AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long -> billingManager!!.purchase(list[position].purchaseId) }
			val builder = AlertDialog.Builder(context)
			builder.setTitle(getString(string.donation))
			builder.setView(listView)
			builder.show()
			false
		}
		findPreference<CheckBoxPreference>("removeAds")?.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference ->
			(preference as CheckBoxPreference).isChecked = billingManager!!.isPurchaseRemoveAds
			billingManager!!.subscribe_removeAds()
			false
		}
		findPreference<Preference>("proTools")?.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference ->
			(preference as CheckBoxPreference).isChecked = billingManager!!.isPurchaseProTools
			billingManager!!.subscribe_proTools()
			false
		}
		findPreference<Preference>("restoreBilling")?.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference? ->
			billingManager!!.refresh()
			false
		}
		findPreference<Preference>("OpenSourceLicense")?.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference? ->
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
				Item("CarouselLayoutManager", "Apache License 2.0", "https://github.com/Azoft/CarouselLayoutManager"),
				Item("FloatingActionButton", "Apache License 2.0", "https://github.com/Clans/FloatingActionButton"),
				Item("TedPermission", "Apache License 2.0", "https://github.com/ParkSangGwon/TedPermission"),
				Item("RealtimeBlurView", "Apache License 2.0", "https://github.com/mmin18/RealtimeBlurView"),
				Item("Android In-App Billing v3 Library", "Apache License 2.0", "https://github.com/anjlab/android-inapp-billing-v3"),
				Item("Retrofit", "Apache License 2.0", "https://github.com/square/retrofit")
			)
			val listView = ListView(context)
			val data = ArrayList<DialogListItem>()
			for (i in list.indices) data.add(list[i].toListItem())
			listView.adapter = DialogListAdapter(data)
			listView.onItemClickListener = AdapterView.OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
				startActivity(
					Intent(
						Intent.ACTION_VIEW,
						Uri.parse(list[position].url)
					)
				)
			}
			val builder = AlertDialog.Builder(context)
			builder.setTitle(getString(string.openSourceLicense))
			builder.setView(listView)
			builder.show()
			false
		}
		findPreference<Preference>("FCMToken")?.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference? ->
			try {
				Functions.putClipboard(activity!!, FirebaseInstanceId.getInstance().token!!)
				context?.toast(string.copied)
			} catch (ignore: Exception) {
			}
			false
		}
	}

	internal fun updateBilling() {
		(findPreference<Preference>("removeAds") as CheckBoxPreference).isChecked = billingManager!!.isPurchaseRemoveAds
		(findPreference<Preference>("proTools") as CheckBoxPreference).isChecked = billingManager!!.isPurchaseProTools
	}


	override fun onResume() {
		findPreference<Preference>("select_theme")?.summary = PreferenceManager.SelectedTheme.load(context!!)
		findPreference<Preference>("FCMToken")?.summary = FirebaseInstanceId.getInstance().token
		val systemLocale: Locale = activity?.application?.resources?.configuration?.locale!!
		val displayCountry: String = systemLocale.displayCountry //국가출력

		val country: String = systemLocale.country // 국가 코드 출력 ex) KR

		val language: String = systemLocale.language // 언어 코드 출력 ex) ko

		findPreference<Preference>("language")?.title = getString(string.language) + " (" + getString(string.languageCode) + ")"
		findPreference<Preference>("language")?.summary = "$displayCountry ($country) - $language"
		findPreference<Preference>("copyright")?.summary = String.format(getString(string.translatedBy), getString(string.translator))
		super.onResume()
	}

	override fun onDestroy() {
		super.onDestroy()
		billingManager!!.release()
	}
}