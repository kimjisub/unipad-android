package com.kimjisub.launchpad;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.widget.TextView;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by rlawl on 2016-02-02.
 * ReCreated by rlawl on 2016-04-23.
 */

public class Intro extends BaseActivity {
	
	Handler handler;
	
	static TextView TV_version;
	
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
							화면.isPremium = jo.getBoolean("autoRenewing");
							if (화면.isPremium)
								TV_version.setTextColor(0xFFffa726);
						} catch (JSONException e) {
							e.printStackTrace();
						}
					}
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		startActivity(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_intro);
		
		TV_version = (TextView) findViewById(R.id.빌드버전);
		try {
			TV_version.setText(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
		} catch (PackageManager.NameNotFoundException e) {
		}
		
		//구글플레이 결제
		Intent serviceIntent = new Intent("com.android.vending.billing.InAppBillingService.BIND");
		serviceIntent.setPackage("com.android.vending");
		bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
		
		
		new TedPermission(Intro.this)
			.setPermissionListener(new PermissionListener() {
				@Override
				public void onPermissionGranted() {
					(handler = new Handler()).postDelayed(runnable, 1500);
					화면.광고초기화(Intro.this);
				}
				
				@Override
				public void onPermissionDenied(ArrayList<String> deniedPermissions) {
					Toast.makeText(Intro.this, 언어(R.string.permissionDenied), Toast.LENGTH_SHORT).show();
					finish();
				}
				
				
			})
			.setPermissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
			.check();
	}
	
	Runnable runnable = new Runnable() {
		@Override
		public void run() {
			화면.패딩너비 = findViewById(R.id.패딩화면크기).getWidth();
			화면.패딩높이 = findViewById(R.id.패딩화면크기).getHeight();
			화면.너비 = findViewById(R.id.화면크기).getWidth();
			화면.높이 = findViewById(R.id.화면크기).getHeight();
			
			startActivity(new Intent(Intro.this, Main.class));
			overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
			
			finish();
			
			정보.설정.유니팩저장경로.불러오기(Intro.this);
			화면.광고(Intro.this);
		}
	};
	
	
	@Override
	public void onStop() {
		handler.removeCallbacks(runnable);
		finish();
		super.onStop();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		finishActivity(this);
		if (mService != null)
			unbindService(mServiceConn);
	}
	
	String 언어(int id) {
		return getResources().getString(id);
	}
}