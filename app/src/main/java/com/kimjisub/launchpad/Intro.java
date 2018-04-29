package com.kimjisub.launchpad;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;
import com.kimjisub.launchpad.manage.Billing;
import com.kimjisub.launchpad.manage.SaveSetting.IsUsingSDCard;
import com.kimjisub.launchpad.manage.UIManager;

import org.json.JSONObject;

import java.util.ArrayList;

public class Intro extends BaseActivity {
	
	static TextView TV_version;
	
	Handler handler;
	
	Billing billing;
	
	
	void initVar() {
		TV_version = findViewById(R.id.version);
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_intro);
		initVar();
		
		billing = new Billing(this).setOnEventListener(new Billing.OnEventListener() {
			@Override
			public void onServiceDisconnected(Billing v) {
			
			}
			
			@Override
			public void onServiceConnected(Billing v) {
				if (Billing.isPremium)
					TV_version.setTextColor(0xFFffa726);
			}
			
			@Override
			public void onPurchaseDone(Billing v, JSONObject jo) {
			
			}
		}).start();
		
		try {
			TV_version.setText(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
		} catch (PackageManager.NameNotFoundException e) {
			e.printStackTrace();
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
		super.onDestroy();
		billing.onDestroy();
	}
}