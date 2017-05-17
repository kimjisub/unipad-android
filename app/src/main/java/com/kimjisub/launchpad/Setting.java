package com.kimjisub.launchpad;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.vending.billing.IInAppBillingService;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by rlawl on 2017-01-16.
 */

public class Setting extends PreferenceActivity {
	
	
	static IInAppBillingService mService;
	static ServiceConnection mServiceConn = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			mService = null;
		}
		
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mService = IInAppBillingService.Stub.asInterface(service);
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		BaseActivity.startActivity(this);
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.setting);
		
		
		//구글플레이 결제
		Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
		serviceIntent.setPackage("com.android.vending");
		bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
		
		findPreference("select_theme").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				startActivity(new Intent(Setting.this, Theme.class));
				return false;
			}
		});
		
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				findPreference("default_font").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					@Override
					public boolean onPreferenceChange(Preference preference, Object newValue) {
						BaseActivity.재시작(Setting.this);
						return true;
					}
				});
			}
		}, 300);
		
		findPreference("unipack_SDCard").setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				BaseActivity.재시작(Setting.this);
				return true;
			}
		});
		
		findPreference("community").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				int[] RlistT = {R.string.officialHomepage,
					R.string.officialFacebook,
					R.string.facebookCommunity,
					R.string.naverCafe,
					R.string.kakaotalk,
					R.string.email};
				int[] RlistS = {R.string.officialHomepage_,
					R.string.officialFacebook_,
					R.string.facebookCommunity_,
					R.string.naverCafe_,
					R.string.kakaotalk_,
					R.string.email_};
				
				final String[] listT = new String[RlistT.length];
				final String[] listS = new String[RlistS.length];
				for (int i = 0; i < listT.length; i++) {
					listT[i] = 언어(RlistT[i]);
					listS[i] = 언어(RlistS[i]);
				}
				
				
				ListView listView = new ListView(Setting.this);
				ArrayList<communityItem> data = new ArrayList<>();
				for (int i = 0; i < listT.length; i++)
					data.add(new communityItem(listT[i], listS[i]));
				
				listView.setAdapter(new mAdapter(Setting.this, R.layout.setting_community_item, data));
				listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
						String[] urls = {
							"http://unipad.kr",
							"https://www.facebook.com/playunipad",
							"https://www.facebook.com/groups/unipadcommunity",
							"http://cafe.naver.com/unipad",
							"http://qr.kakao.com/talk/R4p8KwFLXRZsqEjA1FrAnACDyfc-",
							"mailto:0226unipad@gmail.com"
						};
						String[] actions = {
							Intent.ACTION_VIEW,
							Intent.ACTION_VIEW,
							Intent.ACTION_VIEW,
							Intent.ACTION_VIEW,
							Intent.ACTION_VIEW,
							Intent.ACTION_SENDTO
						};
						startActivity(new Intent(actions[position], Uri.parse(urls[position])));
					}
				});
				
				AlertDialog.Builder builder = new AlertDialog.Builder(Setting.this);
				builder.setTitle(언어(R.string.community));
				builder.setView(listView);
				builder.show();
				return false;
			}
		});
		
		findPreference("removeAds").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				
				try {
					Bundle buyIntentBundle = mService.getBuyIntent(3, getPackageName(), "premium", "inapp", 화면.developerPayload);
					PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");
					
					startIntentSenderForResult(pendingIntent.getIntentSender(), 1001, new Intent(), 0, 0, 0);
					
					
				} catch (RemoteException e) {
					e.printStackTrace();
				} catch (IntentSender.SendIntentException e) {
					e.printStackTrace();
				}
				return false;
			}
		});
		
	}
	
	
	public class communityItem {
		private String title;
		private String summuary;
		
		
		public communityItem(String title, String summuary) {
			setTitle(title);
			setSummuary(summuary);
		}
		
		
		public void setTitle(String title) {
			this.title = title;
		}
		
		public void setSummuary(String summuary) {
			this.summuary = summuary;
		}
		
		public String getTitle() {
			return title;
		}
		
		public String getSummuary() {
			return summuary;
		}
		
	}
	
	class mAdapter extends BaseAdapter {
		private LayoutInflater inflater;
		private ArrayList<communityItem> data;
		private int layout;
		
		
		public mAdapter(Context context, int layout, ArrayList<communityItem> data) {
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
			
			communityItem item = data.get(position);
			
			TextView title = (TextView) convertView.findViewById(R.id.title);
			TextView summary = (TextView) convertView.findViewById(R.id.summary);
			
			title.setText(item.getTitle());
			summary.setText(item.getSummuary());
			return convertView;
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 1001) {
			int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
			String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
			String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");
			
			if (resultCode == RESULT_OK) {
				try {
					JSONObject jo = new JSONObject(purchaseData);
					화면.isPremium = jo.getBoolean("autoRenewing");
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	@Override
	protected void onResume() {
		findPreference("select_theme").setSummary(정보.설정.selectedTheme.불러오기(this));
		if (화면.isPremium)
			findPreference("removeAds").setSummary(언어(R.string.using));
		super.onResume();
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		BaseActivity.finishActivity(this);
		if (mService != null)
			unbindService(mServiceConn);
	}
	
	
	String 언어(int id) {
		return getResources().getString(id);
	}
}
