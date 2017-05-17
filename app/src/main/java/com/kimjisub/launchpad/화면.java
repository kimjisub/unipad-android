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
	
	static InterstitialAd 전면광고;
	static final String AdUnitId = "ca-app-pub-1077445788578961/6843593938";
	static String developerPayload = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkLmP+mnbAw6WKHih7dKPiLu4XFIu1+MsufPIp6yLRzMM48U+1k8juultRjvOX68O46LTboCnNlfjUWPA/2GhiuVofvDK2KmUXXWgXkihml9R5PiVX17JysoQvfHwPtuCXaBib6ng9vHOosBIG1mCs53pQJ7/0R94fb2BgQ2uX4QCBGZ4D+tOULBpOz5U1wtQhrIP5gpe8NQ9J/UGjz6hs1QXp5RBJVCYbXo/DZCzK1pIfG9dDUx1yDxWfSvspqdpwbL45O9p2hMvtUFL6jSzuC9bqenZEaqb0fj7YDdNI1e8wfmooyHAJJxvpV2FnspuYZ7nbG1wLoGW9xKtL5yjOwIDAQAB";
	static boolean isPremium = false;
	
	
	static void 광고(final Context context) {
		if (!isPremium) {
			long 이전광고노출 = 정보.설정.이전광고노출시간.불러오기(context);
			long 현재시간 = System.currentTimeMillis();
			
			if ((현재시간 < 이전광고노출) || 현재시간 - 이전광고노출 >= 30000) {
				정보.설정.이전광고노출시간.저장하기(context, 현재시간);
				
				if (전면광고.isLoaded()) {
					전면광고.show();
					전면광고 = new InterstitialAd(context);
					전면광고.setAdUnitId(AdUnitId);//유닛ID 설정
					전면광고.loadAd(new AdRequest.Builder().build());//광고 로딩
				} else {
					전면광고.setAdListener(new AdListener() {
						public void onAdLoaded() {
							전면광고.show();
							전면광고 = new InterstitialAd(context);
							전면광고.setAdUnitId(AdUnitId);//유닛ID 설정
							전면광고.loadAd(new AdRequest.Builder().build());//광고 로딩
						}
					});
				}
			}
		}
	}
	
	static void 광고초기화(Context context) {
		전면광고 = new InterstitialAd(context);
		전면광고.setAdUnitId(AdUnitId);//유닛ID 설정
		전면광고.loadAd(new AdRequest.Builder().build());//광고 로딩
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
	
	static void log(String 메시지) {
		Log.d("com.kimjisub.log", 메시지);
	}
	
}
