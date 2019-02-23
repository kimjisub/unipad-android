package com.kimjisub.launchpad;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.widget.TextView;

import com.anjlab.android.iab.v3.TransactionDetails;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;
import com.kimjisub.launchpad.utils.BillingManager;
import com.kimjisub.launchpad.utils.Log;

import java.util.List;

public class SplashActivity extends BaseActivity {

	BillingManager billingManager;

	// View
	TextView TV_version;

	// Timer
	Handler handler;
	Runnable runnable;

	void initVar() {
		// View
		TV_version = findViewById(R.id.version);
		TV_version.setText(BuildConfig.VERSION_NAME);

		// Timer
		handler = new Handler();
		runnable = () -> {
			finish();
			startActivity(new Intent(SplashActivity.this, MainActivity.class));
		};
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_splash);
		initVar();

		billingManager = new BillingManager(SplashActivity.this, new BillingManager.BillingEventListener() {
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
				if (billingManager.isPurchaseRemoveAds() || billingManager.isPurchaseProTools())
					TV_version.setTextColor(color(R.color.orange));
			}
		});

		TedPermission.with(this)
				.setPermissionListener(new PermissionListener() {
					@Override
					public void onPermissionGranted() {
						handler.postDelayed(runnable, 3000);
					}

					@Override
					public void onPermissionDenied(List<String> deniedPermissions) {
						finish();
					}
				})
				.setRationaleMessage(R.string.permissionRequire)
				.setDeniedMessage(R.string.permissionDenied)
				.setPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
				.check();

	}

	@Override
	public void onPause() {
		super.onPause();

		handler.removeCallbacks(runnable);
		finish();
	}
}
