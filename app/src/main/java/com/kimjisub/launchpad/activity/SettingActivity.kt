package com.kimjisub.launchpad.activity

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.*
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.SkuDetails
import com.google.firebase.messaging.FirebaseMessaging
import com.kimjisub.launchpad.R
import com.kimjisub.launchpad.adapter.DialogListAdapter
import com.kimjisub.launchpad.adapter.DialogListItem
import com.kimjisub.launchpad.databinding.ActivitySettingBinding
import com.kimjisub.launchpad.manager.Functions
import com.kimjisub.launchpad.manager.PreferenceManager
import com.kimjisub.launchpad.manager.billing.BillingModule
import com.kimjisub.launchpad.manager.billing.Sku
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
		private lateinit var bm: BillingModule
		//private lateinit var bm: DeprecatedBillingManager

		private var mSkuDetails = listOf<SkuDetails>()
			set(value) {
				field = value
				//setSkuDetailsView()
			}

		private var isPro = false
			set(value) {
				field = value
				proPreference.isChecked = value
			}

		private var currentSubscription: Purchase? = null
			set(value) {
				field = value
				// updateSubscriptionState()
			}

		// Preferences
		private lateinit var selectThemePreference: Preference
		private lateinit var storageLocationPreference: Preference
		private lateinit var proPreference: CheckBoxPreference
		private lateinit var restoreBillingPreference: Preference
		private lateinit var communityPreference: Preference
		private lateinit var openSourceLicensePreference: Preference
		private lateinit var fcmTokenPreference: Preference
		private lateinit var languagePreference: Preference
		private lateinit var copyrightPreference: Preference


		override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
			addPreferencesFromResource(R.xml.setting)
			settingActivity = activity as SettingActivity
			p = settingActivity.p
			initBilling()
			/*bm = DeprecatedBillingManager(
				requireActivity(),
				object : BillingProcessor.IBillingHandler {
					override fun onProductPurchased(
						productId: String,
						details: TransactionDetails?
					) {
						updateBilling()
					}

					override fun onPurchaseHistoryRestored() {}
					override fun onBillingError(errorCode: Int, error: Throwable?) {}
					override fun onBillingInitialized() {
						updateBilling()
					}
				})
			bm.initialize()*/

			initPreference()
			addPreferenceListener()
		}

		private fun initPreference() {
			selectThemePreference = findPreference("selectTheme")!!
			storageLocationPreference = findPreference("storageLocation")!!
			proPreference = findPreference("pro")!!
			restoreBillingPreference = findPreference("restoreBilling")!!
			communityPreference = findPreference("community")!!
			openSourceLicensePreference = findPreference("openSourceLicense")!!
			fcmTokenPreference = findPreference("fcmToken")!!
			languagePreference = findPreference("language")!!
			copyrightPreference = findPreference("copyright")!!
		}

		private fun initBilling() {
			bm = BillingModule(requireActivity(), lifecycleScope, object : BillingModule.Callback {
				override fun onBillingModulesIsReady() {
					bm.querySkuDetail(BillingClient.SkuType.SUBS, Sku.PRO) { skuDetails ->
						mSkuDetails = skuDetails
					}

					bm.checkSubscribed {
						currentSubscription = it
					}
				}

				override fun onSuccess(purchase: Purchase) {
					when (purchase.sku) {
						Sku.PRO -> {
							isPro = true
						}
						/*Sku.BUY_1000 -> {
							// 크리스탈 1000개를 충전합니다.
							val currentCrystal = storage.getInt(PREF_KEY_CRYSTAL)
							storage.put(PREF_KEY_CRYSTAL, currentCrystal + 1000)
							updateCrystalView()
						}*/
					}
				}

				override fun onFailure(errorCode: Int) {
					when (errorCode) {
						BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
							toast("이미 구입한 상품입니다.")
						}
						BillingClient.BillingResponseCode.USER_CANCELED -> {
							toast("구매를 취소하셨습니다.")
						}
						BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
							toast("Google Billing 서비스를 사용할 수 없습니다.")
						}
						else -> {
							toast("error: $errorCode")
						}
					}
				}
			})
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
			// bm.release()
		}

		private fun addPreferenceListener() {
			selectThemePreference.onPreferenceClickListener =
				Preference.OnPreferenceClickListener {
					requireContext().start<ThemeActivity>()
					false
				}


			storageLocationPreference.onPreferenceClickListener =
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
							storageLocationPreference.summary =
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

			storageLocationPreference.onPreferenceChangeListener =
				Preference.OnPreferenceChangeListener { _, newValue ->
					false
				}

			//		.onPreferenceClickListener =
			//			Preference.OnPreferenceClickListener {
			//
			//				false
			//			}
			communityPreference.onPreferenceClickListener =
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
			proPreference.onPreferenceClickListener =
				Preference.OnPreferenceClickListener { preference: Preference ->
					(preference as CheckBoxPreference).isChecked = isPro
					mSkuDetails.find { it.sku == Sku.PRO }?.let { skuDetail ->
						bm.purchase(skuDetail)
					} ?: also {
						toast("상품을 찾을 수 없습니다.")
					}
					false
				}
			restoreBillingPreference.onPreferenceClickListener =
				Preference.OnPreferenceClickListener {
					// bm.loadOwnedPurchasesFromGoogle()
					false
				}
			openSourceLicensePreference.onPreferenceClickListener =
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
			fcmTokenPreference.onPreferenceClickListener =
				Preference.OnPreferenceClickListener { preference: Preference? ->
					try {
						Functions.putClipboard(
							requireContext(),
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
				settingActivity.uniPackWorkspace.absolutePath

			val systemLocale: Locale = activity?.application?.resources?.configuration?.locale!!
			val displayCountry: String = systemLocale.displayCountry //국가출력

			val country: String = systemLocale.country // 국가 코드 출력 ex) KR

			val language: String = systemLocale.language // 언어 코드 출력 ex) ko

			languagePreference.title =
				getString(R.string.language) + " (" + getString(R.string.languageCode) + ")"
			languagePreference.summary =
				"$displayCountry ($country) - $language"
			copyrightPreference.summary =
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


		companion object {
			fun newInstance(rootKey: String = "root") =
				SettingsFragment().apply {
					arguments = Bundle().apply { putString(ARG_PREFERENCE_ROOT, rootKey) }
				}
		}
	}

}

