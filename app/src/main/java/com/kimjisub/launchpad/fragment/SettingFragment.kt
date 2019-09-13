package com.kimjisub.launchpad.fragment

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.preference.CheckBoxPreference
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.anjlab.android.iab.v3.TransactionDetails
import com.google.firebase.iid.FirebaseInstanceId
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.R.*
import com.kimjisub.launchpad.activity.BaseActivity
import com.kimjisub.launchpad.activity.ThemeActivity
import com.kimjisub.launchpad.manager.BillingManager
import com.kimjisub.launchpad.manager.Constant
import com.kimjisub.launchpad.manager.PreferenceManager
import java.util.*

class SettingFragment : PreferenceFragmentCompat() {
	private var billingManager: BillingManager? = null

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
			val titleList = intArrayOf(string.officialHomepage,
					string.officialFacebook,
					string.facebookCommunity,
					string.naverCafe,
					string.discord,
					string.kakaotalk,
					string.email)
			val summaryList = intArrayOf(string.officialHomepage_,
					string.officialFacebook_,
					string.facebookCommunity_,
					string.naverCafe_,
					string.discord_,
					string.kakaotalk_,
					string.email_)
			val iconList = intArrayOf(
					drawable.community_web,
					drawable.community_facebook,
					drawable.community_facebook_group,
					drawable.community_cafe,
					drawable.community_discord,
					drawable.community_kakaotalk,
					drawable.community_mail
			)
			val urlList = arrayOf(
					"https://unipad.kr",
					"https://www.facebook.com/playunipad",
					"https://www.facebook.com/groups/playunipad",
					"http://cafe.naver.com/unipad",
					"https://discord.gg/ESDgyNs",
					"http://qr.kakao.com/talk/R4p8KwFLXRZsqEjA1FrAnACDyfc-",
					"mailto:0226unipad@gmail.com"
			)
			val actionList = arrayOf(
					Intent.ACTION_VIEW,
					Intent.ACTION_VIEW,
					Intent.ACTION_VIEW,
					Intent.ACTION_VIEW,
					Intent.ACTION_VIEW,
					Intent.ACTION_VIEW,
					Intent.ACTION_VIEW,
					Intent.ACTION_SENDTO
			)
			val listT = arrayOfNulls<String?>(titleList.size)
			val listS = arrayOfNulls<String?>(summaryList.size)
			val listI = IntArray(iconList.size)
			for (i in listT.indices) {
				listT[i] = lang(titleList[i])
				listS[i] = lang(summaryList[i])
				listI[i] = iconList[i]
			}
			val listView = ListView(context)
			val data = ArrayList<ListItem>()
			for (i in listT.indices) data.add(ListItem(listT[i]!!, listS[i]!!, listI[i]))
			listView.adapter = ListAdapter(context!!, layout.item_setting, data)
			listView.onItemClickListener = AdapterView.OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
				startActivity(Intent(actionList[position], Uri.parse(urlList[position])))
			}
			val builder = AlertDialog.Builder(context)
			builder.setTitle(lang(string.community))
			builder.setView(listView)
			builder.show()
			false
		}
		findPreference<Preference>("donation")?.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference? ->
			val titleList = arrayOf(
					"Donate $1",
					"Donate $5",
					"Donate $10",
					"Donate $50")
			val summaryList = arrayOf(
					Constant.BILLING.DONATE_1,
					Constant.BILLING.DONATE_5,
					Constant.BILLING.DONATE_10,
					Constant.BILLING.DONATE_50,
					Constant.BILLING.DONATE_100
			)
			val urlList = arrayOf(
					Constant.BILLING.DONATE_1,
					Constant.BILLING.DONATE_5,
					Constant.BILLING.DONATE_10,
					Constant.BILLING.DONATE_50,
					Constant.BILLING.DONATE_100
			)
			val listView = ListView(context)
			val data = ArrayList<ListItem>()
			for (i in titleList.indices) data.add(ListItem(titleList[i], summaryList[i]))
			listView.adapter = ListAdapter(context!!, layout.item_setting, data)
			listView.onItemClickListener = AdapterView.OnItemClickListener { _: AdapterView<*>?, _: View?, position: Int, _: Long -> billingManager!!.purchase(urlList[position]) }
			val builder = AlertDialog.Builder(context)
			builder.setTitle(lang(string.donation))
			builder.setView(listView)
			builder.show()
			false
		}
		findPreference<Preference>("removeAds")?.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference ->
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
			val titleList = arrayOf(
					"CarouselLayoutManager",
					"FloatingActionButton",
					"TedPermission",
					"RealtimeBlurView",
					"Android In-App Billing v3 Library",
					"Retrofit",
					"UniPad DesignKit"
			)
			val summaryList = arrayOf(
					"Apache License 2.0",
					"Apache License 2.0",
					"Apache License 2.0",
					"Apache License 2.0",
					"Apache License 2.0",
					"Apache License 2.0",
					"Apache License 2.0"
			)
			val urlList = arrayOf(
					"https://github.com/Azoft/CarouselLayoutManager",
					"https://github.com/Clans/FloatingActionButton",
					"https://github.com/ParkSangGwon/TedPermission",
					"https://github.com/mmin18/RealtimeBlurView",
					"https://github.com/anjlab/android-inapp-billing-v3",
					"https://github.com/square/retrofit",
					"https://github.com/0226daniel/UniPad-DesignKit"
			)
			val listView = ListView(context)
			val data = ArrayList<ListItem>()
			for (i in titleList.indices) data.add(ListItem(titleList[i], summaryList[i]))
			listView.adapter = ListAdapter(context!!, layout.item_setting, data)
			listView.onItemClickListener = AdapterView.OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(urlList[position]))) }
			val builder = AlertDialog.Builder(context)
			builder.setTitle(lang(string.openSourceLicense))
			builder.setView(listView)
			builder.show()
			false
		}
		findPreference<Preference>("FCMToken")?.onPreferenceClickListener = Preference.OnPreferenceClickListener { preference: Preference? ->
			putClipboard(FirebaseInstanceId.getInstance().token)
			BaseActivity.showToast(context!!, string.copied)
			false
		}

	}

	internal fun updateBilling() {
		(findPreference<Preference>("removeAds") as CheckBoxPreference).isChecked = billingManager!!.isPurchaseRemoveAds
		(findPreference<Preference>("proTools") as CheckBoxPreference).isChecked = billingManager!!.isPurchaseProTools
	}

	private fun putClipboard(msg: String?) {
		val clipboardManager = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
		val clipData: ClipData? = ClipData.newPlainText("LABEL", msg)
		clipboardManager?.primaryClip = clipData
	}

	override fun onResume() {
		findPreference<Preference>("select_theme")?.summary = PreferenceManager.SelectedTheme.load(context)
		findPreference<Preference>("FCMToken")?.summary = FirebaseInstanceId.getInstance().token
		val systemLocale: Locale = activity?.application?.resources?.configuration?.locale!!
		val displayCountry: String = systemLocale.displayCountry //국가출력

		val country: String = systemLocale.country // 국가 코드 출력 ex) KR

		val language: String = systemLocale.language // 언어 코드 출력 ex) ko

		findPreference<Preference>("language")?.title = lang(string.language) + " (" + lang(string.languageCode) + ")"
		findPreference<Preference>("language")?.summary = "$displayCountry ($country) - $language"
		findPreference<Preference>("copyright")?.summary = String.format(lang(string.translatedBy), lang(string.translator))
		super.onResume()
	}

	override fun onDestroy() {
		super.onDestroy()
		billingManager!!.release()
	}

	internal fun lang(id: Int): String {
		return BaseActivity.lang(context, id)
	}

	data class ListItem(
			val title: String,
			val subtitle: String,
			val iconResId: Int? = null)

	internal inner class ListAdapter(context: Context, private val layout: Int, private val data: ArrayList<ListItem>) : BaseAdapter() {
		private val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
		override fun getCount() = data.size

		override fun getItem(position: Int): String {
			return data[position].title!!
		}

		override fun getItemId(position: Int): Long {
			return position.toLong()
		}

		override fun getView(position: Int, convertView: View, parent: ViewGroup): View {
			var view = convertView
			if (view == null) view = inflater.inflate(layout, parent, false)
			val item = data[position]
			val title: TextView = view.findViewById(R.id.title)
			val summary: TextView = view.findViewById(R.id.summary)
			val icon: ImageView = view.findViewById(R.id.icon)
			title.text = item.title
			summary.text = item.subtitle
			if (item.iconResId != null) {
				icon.background = ContextCompat.getDrawable(context!!, item.iconResId)
				icon.scaleType = ImageView.ScaleType.FIT_CENTER
			} else icon.visibility = View.GONE
			return view
		}

	}
}