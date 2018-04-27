package com.kimjisub.launchpad;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.TextView;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;
import com.kimjisub.launchpad.manage.SaveSetting.IsUsingSDCard;
import com.kimjisub.launchpad.manage.UIManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class Intro extends BaseActivity {

	static TextView TV_version;

	Handler handler;

	static IInAppBillingService mService;
	static ServiceConnection mServiceConn = new ServiceConnection() {
		@Override
		public void onServiceDisconnected(ComponentName name) {
			mService = null;
		}

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mService = IInAppBillingService.Stub.asInterface(service);
			try {
				Bundle ownedItems = mService.getPurchases(3, "com.kimjisub.launchpad", "subs", null);
				int response = ownedItems.getInt("RESPONSE_CODE");
				if (response == 0) {
					ArrayList<String> purchaseDataList = ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");

					for (int i = 0; i < purchaseDataList.size(); ++i) {
						String purchaseData = purchaseDataList.get(i);

						try {
							JSONObject jo = new JSONObject(purchaseData);
							UIManager.isPremium = jo.getBoolean("autoRenewing");
							if (UIManager.isPremium)
								TV_version.setTextColor(0xFFffa726);
						} catch (JSONException e) {
							e.printStackTrace();
						}
					}
				}
			} catch (RemoteException | NullPointerException e) {
				e.printStackTrace();
			}
		}
	};


	void initVar() {
		TV_version = findViewById(R.id.version);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_intro);
		initVar();

		try {
			TV_version.setText(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
		}

		try {
			//구글플레이 결제
			Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
			serviceIntent.setPackage("com.android.vending");
			bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
		}catch(Exception ignore){
		}


		new TedPermission(Intro.this)
			.setPermissionListener(new PermissionListener() {
				@Override
				public void onPermissionGranted() {
					UIManager.initAds(Intro.this);
					(handler = new Handler()).postDelayed(runnable, 300);//1500);
				}

				@Override
				public void onPermissionDenied(ArrayList<String> deniedPermissions) {
					Toast.makeText(Intro.this, lang(R.string.permissionDenied), Toast.LENGTH_SHORT).show();
					finish();
				}


			})
			.setPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
			.check();
	}

	Runnable runnable = new Runnable() {
		@Override
		public void run() {
			startActivity(new Intent(Intro.this, Main.class));
			overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

			IsUsingSDCard.load(Intro.this);
			UIManager.showAds(Intro.this);

			finish();
		}
	};


	@Override
	public void onStop() {
		try {
			handler.removeCallbacks(runnable);
		} catch (RuntimeException ignore) {
		}

		finish();
		
		super.onStop();
	}

	@Override
	protected void onResume() {
		initVar();
		
		super.onResume();
	}

	@Override
	protected void onDestroy() {
		if (mService != null)
			unbindService(mServiceConn);
		
		super.onDestroy();
	}
}