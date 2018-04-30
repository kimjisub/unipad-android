package com.kimjisub.launchpad.manage;

import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.kimjisub.launchpad.BaseActivity;
import com.kimjisub.launchpad.R;
import com.kimjisub.launchpad.manage.SaveSetting.PrevAdsShowTime;

public class UIManager {
	public static int[] Scale = new int[4];
	
	public static final int PaddingWidth = 0;
	public static final int PaddingHeight = 1;
	public static final int Width = 2;
	public static final int Height = 3;
	
	private static InterstitialAd interstitialAd;
	
	
	public static void showAds(final Context context) {
		
		if (!Billing.isPremium) {
			long prevTime = PrevAdsShowTime.load(context);
			long currTime = System.currentTimeMillis();
			
			if ((currTime < prevTime) || currTime - prevTime >= 30000) {
				PrevAdsShowTime.save(context, currTime);
				
				if (interstitialAd.isLoaded()) {
					interstitialAd.show();
					interstitialAd = new InterstitialAd(context);
					interstitialAd.setAdUnitId(Billing.ADUNITID);
					interstitialAd.loadAd(new AdRequest.Builder().build());
				} else {
					interstitialAd.setAdListener(new AdListener() {
						public void onAdLoaded() {
							interstitialAd.show();
							interstitialAd = new InterstitialAd(context);
							interstitialAd.setAdUnitId(Billing.ADUNITID);
							interstitialAd.loadAd(new AdRequest.Builder().build());
						}
					});
				}
			}
		}
	}
	
	public static void initAds(Context context) {
		if (!Billing.isPremium) {
			interstitialAd = new InterstitialAd(context);
			interstitialAd.setAdUnitId(Billing.ADUNITID);
			interstitialAd.loadAd(new AdRequest.Builder().build());
		}
	}
	
	public static void showDialog(Context context, String title, String content) {
		new AlertDialog.Builder(context)
			.setTitle(title)
			.setMessage(content)
			.setPositiveButton(BaseActivity.lang(context, R.string.accept), null)
			.show();
	}
	
	public static int pxToDp(Context context, int pixel) {
		float dp = 0;
		try {
			DisplayMetrics metrics = context.getResources().getDisplayMetrics();
			dp = pixel / (metrics.densityDpi / 160f);
		} catch (Exception e) {
		}
		return (int) dp;
	}
	
	public static int dpToPx(Context context, float dp) {
		DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		float px = dp * (metrics.densityDpi / 160f);
		return Math.round(px);
	}
}
