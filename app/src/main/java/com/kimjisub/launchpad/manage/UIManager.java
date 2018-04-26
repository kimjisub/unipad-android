package com.kimjisub.launchpad.manage;

import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.LinearLayout;

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

	public static final String DEVELOPERPAYLOAD = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkLmP+mnbAw6WKHih7dKPiLu4XFIu1+MsufPIp6yLRzMM48U+1k8juultRjvOX68O46LTboCnNlfjUWPA/2GhiuVofvDK2KmUXXWgXkihml9R5PiVX17JysoQvfHwPtuCXaBib6ng9vHOosBIG1mCs53pQJ7/0R94fb2BgQ2uX4QCBGZ4D+tOULBpOz5U1wtQhrIP5gpe8NQ9J/UGjz6hs1QXp5RBJVCYbXo/DZCzK1pIfG9dDUx1yDxWfSvspqdpwbL45O9p2hMvtUFL6jSzuC9bqenZEaqb0fj7YDdNI1e8wfmooyHAJJxvpV2FnspuYZ7nbG1wLoGW9xKtL5yjOwIDAQAB";
	public static final String ADUNITID = "ca-app-pub-1077445788578961/6843593938";
	public static boolean isPremium = false;

	private static InterstitialAd interstitialAd;


	public static void showAds(final Context context) {
		
		/*if (!isPremium) {
			long prevTime = PrevAdsShowTime.load(context);
			long currTime = System.currentTimeMillis();

			if ((currTime < prevTime) || currTime - prevTime >= 30000) {
				PrevAdsShowTime.save(context, currTime);

				if (interstitialAd.isLoaded()) {
					interstitialAd.show();
					interstitialAd = new InterstitialAd(context);
					interstitialAd.setAdUnitId(ADUNITID);
					interstitialAd.loadAd(new AdRequest.Builder().build());
				} else {
					interstitialAd.setAdListener(new AdListener() {
						public void onAdLoaded() {
							interstitialAd.show();
							interstitialAd = new InterstitialAd(context);
							interstitialAd.setAdUnitId(ADUNITID);
							interstitialAd.loadAd(new AdRequest.Builder().build());
						}
					});
				}
			}
		}*/
	}

	public static void initAds(Context context) {
		if (!isPremium) {
			interstitialAd = new InterstitialAd(context);
			interstitialAd.setAdUnitId(ADUNITID);
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
