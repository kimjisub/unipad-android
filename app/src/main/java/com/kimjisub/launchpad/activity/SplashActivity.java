package com.kimjisub.launchpad.activity;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.anjlab.android.iab.v3.TransactionDetails;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.TedPermission;
import com.kimjisub.launchpad.BuildConfig;
import com.kimjisub.launchpad.R;
import com.kimjisub.launchpad.databinding.ActivitySplashBinding;
import com.kimjisub.launchpad.manager.BillingManager;

import java.util.List;

public class SplashActivity extends BaseActivity {
	ActivitySplashBinding b;

	BillingManager billingManager;

	// Timer
	Handler handler;
	Runnable runnable;

	void initVar() {
		// View
		b.version.setText(BuildConfig.VERSION_NAME);

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
		b = setContentViewBind(R.layout.activity_splash);
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
					b.version.setTextColor(color(R.color.orange));
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
	public void onStop() {
		super.onStop();

		handler.removeCallbacks(runnable);
		finish();
	}
}
