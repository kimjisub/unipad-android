package com.kimjisub.launchpad;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;

/**
 * Created by rlawl on 2016-04-23.
 * ReCreated by rlawl on 2016-04-23.
 */
public class 화면 {
	static int 패딩너비;
	static int 패딩높이;
	static int 너비;
	static int 높이;
	
	static InterstitialAd interstitialAd;
	static final String ADUNITID = "ca-app-pub-1077445788578961/6843593938";
	static final String DEVELOPERPAYLOAD = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkLmP+mnbAw6WKHih7dKPiLu4XFIu1+MsufPIp6yLRzMM48U+1k8juultRjvOX68O46LTboCnNlfjUWPA/2GhiuVofvDK2KmUXXWgXkihml9R5PiVX17JysoQvfHwPtuCXaBib6ng9vHOosBIG1mCs53pQJ7/0R94fb2BgQ2uX4QCBGZ4D+tOULBpOz5U1wtQhrIP5gpe8NQ9J/UGjz6hs1QXp5RBJVCYbXo/DZCzK1pIfG9dDUx1yDxWfSvspqdpwbL45O9p2hMvtUFL6jSzuC9bqenZEaqb0fj7YDdNI1e8wfmooyHAJJxvpV2FnspuYZ7nbG1wLoGW9xKtL5yjOwIDAQAB";
	static boolean isPremium = false;
	
	
	static void 광고(final Context context) {
		if (!isPremium) {
			long prevTime = 정보.설정.이전광고노출시간.불러오기(context);
			long currTime = System.currentTimeMillis();
			
			if ((currTime < prevTime) || currTime - prevTime >= 30000) {
				정보.설정.이전광고노출시간.저장하기(context, currTime);
				
				if (interstitialAd.isLoaded()) {
					interstitialAd.show();
					interstitialAd = new InterstitialAd(context);
					interstitialAd.setAdUnitId(ADUNITID);//유닛ID 설정
					interstitialAd.loadAd(new AdRequest.Builder().build());//광고 로딩
				} else {
					interstitialAd.setAdListener(new AdListener() {
						public void onAdLoaded() {
							interstitialAd.show();
							interstitialAd = new InterstitialAd(context);
							interstitialAd.setAdUnitId(ADUNITID);//유닛ID 설정
							interstitialAd.loadAd(new AdRequest.Builder().build());//광고 로딩
						}
					});
				}
			}
		}
	}
	
	static void initAd(Context context) {
		interstitialAd = new InterstitialAd(context);
		interstitialAd.setAdUnitId(ADUNITID);
		interstitialAd.loadAd(new AdRequest.Builder().build());
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
	
	static void log(String msg) {
		Log.d("com.kimjisub.log", msg);
	}
	
}
