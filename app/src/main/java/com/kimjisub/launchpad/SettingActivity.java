package com.kimjisub.launchpad;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceActivity;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.anjlab.android.iab.v3.TransactionDetails;
import com.google.firebase.iid.FirebaseInstanceId;
import com.kimjisub.launchpad.manage.BillingCertification;
import com.kimjisub.launchpad.manage.SettingManager;

import java.util.ArrayList;
import java.util.Locale;

import static com.kimjisub.launchpad.manage.Constant.BILLING.DONATE_1;
import static com.kimjisub.launchpad.manage.Constant.BILLING.DONATE_10;
import static com.kimjisub.launchpad.manage.Constant.BILLING.DONATE_100;
import static com.kimjisub.launchpad.manage.Constant.BILLING.DONATE_5;
import static com.kimjisub.launchpad.manage.Constant.BILLING.DONATE_50;

public class SettingActivity extends PreferenceActivity {

	BillingCertification billingCertification;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		BaseActivity.startActivity(this);
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.setting);

		billingCertification = new BillingCertification(SettingActivity.this, new BillingCertification.BillingEventListener() {
			@Override
			public void onProductPurchased(@NonNull String productId, @Nullable TransactionDetails details) {
			}

			@Override
			public void onPurchaseHistoryRestored() {
			}

			@Override
			public void onBillingError(int errorCode, @Nullable Throwable error) {
			}

			@Override
			public void onBillingInitialized() {
			}

			@Override
			public void onRefresh() {
				updateBilling();
			}
		});

		findPreference("select_theme").setOnPreferenceClickListener(preference -> {
			startActivity(new Intent(SettingActivity.this, ThemeActivity.class));
			return false;
		});

		findPreference("use_sd_card").setOnPreferenceChangeListener((preference, newValue) -> {
			BaseActivity.requestRestart(SettingActivity.this);
			return true;
		});

		findPreference("community").setOnPreferenceClickListener(preference -> {
			int[] titleList = {R.string.officialHomepage,
					R.string.officialFacebook,
					R.string.facebookCommunity,
					R.string.naverCafe,
					R.string.discord,
					R.string.kakaotalk,
					R.string.email};
			int[] summaryList = {R.string.officialHomepage_,
					R.string.officialFacebook_,
					R.string.facebookCommunity_,
					R.string.naverCafe_,
					R.string.discord_,
					R.string.kakaotalk_,
					R.string.email_};

			int[] iconList = {
					R.drawable.community_web,
					R.drawable.community_facebook,
					R.drawable.community_facebook_group,
					R.drawable.community_cafe,
					R.drawable.community_discord,
					R.drawable.community_kakaotalk,
					R.drawable.community_mail
			};
			String[] urlList = {
					"https://unipad.kr",
					"https://www.facebook.com/playunipad",
					"https://www.facebook.com/groups/playunipad",
					"http://cafe.naver.com/unipad",
					"https://discord.gg/ESDgyNs",
					"http://qr.kakao.com/talk/R4p8KwFLXRZsqEjA1FrAnACDyfc-",
					"mailto:0226unipad@gmail.com"
			};
			String[] actionList = {
					Intent.ACTION_VIEW,
					Intent.ACTION_VIEW,
					Intent.ACTION_VIEW,
					Intent.ACTION_VIEW,
					Intent.ACTION_VIEW,
					Intent.ACTION_VIEW,
					Intent.ACTION_VIEW,
					Intent.ACTION_SENDTO
			};

			final String[] listT = new String[titleList.length];
			final String[] listS = new String[summaryList.length];
			final int[] listI = new int[iconList.length];
			for (int i = 0; i < listT.length; i++) {
				listT[i] = lang(titleList[i]);
				listS[i] = lang(summaryList[i]);
				listI[i] = iconList[i];
			}


			ListView listView = new ListView(SettingActivity.this);
			ArrayList<mItem> data = new ArrayList<>();
			for (int i = 0; i < listT.length; i++)
				data.add(new mItem(listT[i], listS[i], listI[i]));

			listView.setAdapter(new mAdapter(SettingActivity.this, R.layout.setting_item, data));
			listView.setOnItemClickListener((parent, view, position, id) -> startActivity(new Intent(actionList[position], Uri.parse(urlList[position]))));

			AlertDialog.Builder builder = new AlertDialog.Builder(SettingActivity.this);
			builder.setTitle(lang(R.string.community));
			builder.setView(listView);
			builder.show();
			return false;
		});

		findPreference("donation").setOnPreferenceClickListener(preference -> {
			String[] titleList = {
					"Donate $1",
					"Donate $5",
					"Donate $10",
					"Donate $50",
					//"Donate $100"
			};
			String[] summaryList = {
					DONATE_1,
					DONATE_5,
					DONATE_10,
					DONATE_50,
					DONATE_100
			};
			String[] urlList = {
					DONATE_1,
					DONATE_5,
					DONATE_10,
					DONATE_50,
					DONATE_100
			};

			ListView listView = new ListView(SettingActivity.this);
			ArrayList<mItem> data = new ArrayList<>();
			for (int i = 0; i < titleList.length; i++)
				data.add(new mItem(titleList[i], summaryList[i]));

			listView.setAdapter(new mAdapter(SettingActivity.this, R.layout.setting_item, data));
			listView.setOnItemClickListener((parent, view, position, id) -> billingCertification.purchase(urlList[position]));

			AlertDialog.Builder builder = new AlertDialog.Builder(SettingActivity.this);
			builder.setTitle(lang(R.string.donation));
			builder.setView(listView);
			builder.show();

			return false;
		});

		findPreference("removeAds").setOnPreferenceClickListener(preference -> {
			((CheckBoxPreference) preference).setChecked(BillingCertification.isPurchaseRemoveAds());
			billingCertification.subscribe_removeAds();
			return false;
		});

		findPreference("proTools").setOnPreferenceClickListener(preference -> {
			((CheckBoxPreference) preference).setChecked(BillingCertification.isPurchaseProTools());
			billingCertification.subscribe_proTools();
			return false;
		});

		findPreference("restoreBilling").setOnPreferenceClickListener(preference -> {
			billingCertification.refresh();
			BaseActivity.requestRestart(SettingActivity.this);
			return false;
		});

		findPreference("OpenSourceLicense").setOnPreferenceClickListener(preference -> {
			String[] titleList = {
					"CarouselLayoutManager",
					"FloatingActionButton",
					"TedPermission",
					"RealtimeBlurView",
					"Android In-App Billing v3 Library",
					"Retrofit",
					"UniPad DesignKit"
			};
			String[] summaryList = {
					"Apache License 2.0",
					"Apache License 2.0",
					"Apache License 2.0",
					"Apache License 2.0",
					"Apache License 2.0",
					"Apache License 2.0",
					"Apache License 2.0"
			};
			String[] urlList = {
					"https://github.com/Azoft/CarouselLayoutManager",
					"https://github.com/Clans/FloatingActionButton",
					"https://github.com/ParkSangGwon/TedPermission",
					"https://github.com/mmin18/RealtimeBlurView",
					"https://github.com/anjlab/android-inapp-billing-v3",
					"https://github.com/square/retrofit",
					"https://github.com/0226daniel/UniPad-DesignKit"
			};

			ListView listView = new ListView(SettingActivity.this);
			ArrayList<mItem> data = new ArrayList<>();
			for (int i = 0; i < titleList.length; i++)
				data.add(new mItem(titleList[i], summaryList[i]));

			listView.setAdapter(new mAdapter(SettingActivity.this, R.layout.setting_item, data));
			listView.setOnItemClickListener((parent, view, position, id) -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlList[position]))));

			AlertDialog.Builder builder = new AlertDialog.Builder(SettingActivity.this);
			builder.setTitle(lang(R.string.openSourceLicense));
			builder.setView(listView);
			builder.show();
			return false;
		});

		findPreference("FCMToken").setOnPreferenceClickListener(preference -> {
			putClipboard(FirebaseInstanceId.getInstance().getToken());
			BaseActivity.showToast(SettingActivity.this, R.string.copied);
			return false;
		});

		//findPreference("proTools").animation

	}

	void updateBilling() {
		((CheckBoxPreference) findPreference("removeAds")).setChecked(BillingCertification.isPurchaseRemoveAds());
		((CheckBoxPreference) findPreference("proTools")).setChecked(BillingCertification.isPurchaseProTools());
	}

	void putClipboard(String msg) {
		ClipboardManager clipboardManager = (ClipboardManager) SettingActivity.this.getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clipData = ClipData.newPlainText("LABEL", msg);
		assert clipboardManager != null;
		clipboardManager.setPrimaryClip(clipData);
	}

	@Override
	protected void onResume() {
		findPreference("select_theme").setSummary(SettingManager.SelectedTheme.load(SettingActivity.this));
		findPreference("use_sd_card").setSummary(SettingManager.IsUsingSDCard.getPath(SettingActivity.this));
		findPreference("FCMToken").setSummary(FirebaseInstanceId.getInstance().getToken());

		Locale systemLocale = getApplicationContext().getResources().getConfiguration().locale;
		String displayCountry = systemLocale.getDisplayCountry(); //국가출력
		String country = systemLocale.getCountry(); // 국가 코드 출력 ex) KR
		String language = systemLocale.getLanguage(); // 언어 코드 출력 ex) ko

		findPreference("language").setTitle(lang(R.string.language) + " (" + lang(R.string.languageCode) + ")");
		findPreference("language").setSummary(displayCountry + " (" + country + ") - " + language);
		findPreference("copyright").setSummary(String.format(lang(R.string.translatedBy), lang(R.string.translator)));

		super.onResume();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		BaseActivity.finishActivity(this);
		billingCertification.release();
	}

	String lang(int id) {
		return BaseActivity.lang(SettingActivity.this, id);
	}

	public class mItem {
		private String title;
		private String summary;
		private int icon;
		private boolean isIcon = false;


		public mItem(String title, String summary, int icon) {
			setTitle(title);
			setSummary(summary);
			setIcon(icon);
		}

		public mItem(String title, String summary) {
			setTitle(title);
			setSummary(summary);
		}

		// setter

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getSummary() {
			return summary;
		}


		// getter

		public void setSummary(String summary) {
			this.summary = summary;
		}

		public int getIcon() {
			return icon;
		}

		public boolean isIcon() {
			return isIcon;
		}

		public void setIcon(int icon) {
			this.icon = icon;
			isIcon = true;
		}

	}

	class mAdapter extends BaseAdapter {
		private LayoutInflater inflater;
		private ArrayList<mItem> data;
		private int layout;


		public mAdapter(Context context, int layout, ArrayList<mItem> data) {
			this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			this.data = data;
			this.layout = layout;
		}

		@Override
		public int getCount() {
			return data.size();
		}

		@Override
		public String getItem(int position) {
			return data.get(position).getTitle();
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(final int position, View convertView, ViewGroup parent) {
			if (convertView == null)
				convertView = inflater.inflate(layout, parent, false);

			mItem item = data.get(position);

			TextView title = convertView.findViewById(R.id.title);
			TextView summary = convertView.findViewById(R.id.summary);
			ImageView icon = convertView.findViewById(R.id.icon);

			title.setText(item.getTitle());
			summary.setText(item.getSummary());
			if (item.isIcon()) {
				icon.setBackground(BaseActivity.drawable(getApplicationContext(), item.getIcon()));
				icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
			} else
				icon.setVisibility(View.GONE);
			return convertView;
		}
	}
}
