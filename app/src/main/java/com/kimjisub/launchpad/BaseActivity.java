package com.kimjisub.launchpad;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Process;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.widget.LinearLayout;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.kimjisub.launchpad.manage.Billing;
import com.kimjisub.launchpad.manage.SettingManager;

import java.util.ArrayList;

import static com.kimjisub.launchpad.manage.Constant.ADSCOOLTIME;
import static com.kimjisub.launchpad.manage.Constant.ADUNITID;
import static com.kimjisub.launchpad.manage.Tools.logActivity;
import static com.kimjisub.launchpad.manage.Tools.logAds;

public abstract class BaseActivity extends AppCompatActivity {
	
	//========================================================================================== Scale
	
	public static int Scale_Width = 0;
	public static int Scale_Height = 0;
	public static int Scale_PaddingWidth = 0;
	public static int Scale_PaddingHeight = 0;
	
	void rescanScale(LinearLayout LL_scale, LinearLayout LL_paddingScale) {
		Scale_Width = LL_scale.getWidth();
		Scale_Height = LL_scale.getHeight();
		Scale_PaddingWidth = LL_paddingScale.getWidth();
		Scale_PaddingHeight = LL_paddingScale.getHeight();
	}
	
	// ========================================================================================= vungle
	
	/*final VunglePub vunglePub = VunglePub.getInstance();
	
	public void initVungle(){
		final String app_id = "your Vungle App ID";
		
		// Publisher SDK 초기화
		vunglePub.init(this, app_id);
	}*/
	
	// ========================================================================================= Admob
	
	private static InterstitialAd interstitialAd;
	
	public void showAdmob() {
		if (!Billing.isPremium) {
			long prevTime = SettingManager.PrevAdsShowTime.load(BaseActivity.this);
			long currTime = System.currentTimeMillis();
			
			if ((currTime < prevTime) || currTime - prevTime >= ADSCOOLTIME) {
				SettingManager.PrevAdsShowTime.save(this, currTime);
				logAds("showAdmob");
				
				if (interstitialAd.isLoaded()) {
					logAds("isLoaded");
					interstitialAd.show();
					loadAdmob();
					logAds("show!");
				} else {
					logAds("! isLoaded (set listener)");
					interstitialAd.setAdListener(new AdListener() {
						public void onAdLoaded() {
							interstitialAd.show();
							loadAdmob();
							logAds("show!");
						}
					});
				}
			}
		}
	}
	
	public void initAdmob() {
		if (!Billing.isPremium) {
			logAds("initAdmob");
			loadAdmob();
		}
	}
	
	void loadAdmob() {
		interstitialAd = new InterstitialAd(BaseActivity.this);
		interstitialAd.setAdUnitId(ADUNITID);
		interstitialAd.loadAd(new AdRequest.Builder()
			//.addTestDevice("36C3684AAD25CDF5A6360640B20DC084")
			.build());
	}
	
	//========================================================================================== Function
	
	public void showDialog(String title, String content) {
		new AlertDialog.Builder(BaseActivity.this)
			.setTitle(title)
			.setMessage(content)
			.setPositiveButton(lang(R.string.accept), null)
			.show();
	}
	
	public static void showDialog(Context context, String title, String content) {
		new AlertDialog.Builder(context)
			.setTitle(title)
			.setMessage(content)
			.setPositiveButton(lang(context, R.string.accept), null)
			.show();
	}
	
	/*public int pxToDp(int pixel) {
		float dp = 0;
		try {
			DisplayMetrics metrics = getResources().getDisplayMetrics();
			dp = pixel / (metrics.densityDpi / 160f);
		} catch (Exception ignored) {
		}
		return (int) dp;
	}*/
	
	public int dpToPx(float dp) {
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		float px = dp * (metrics.densityDpi / 160f);
		return Math.round(px);
	}
	
	//========================================================================================== Get Resources
	
	public String lang(int id) {
		return getResources().getString(id);
	}
	
	public static String lang(Context context, int id) {
		return context.getResources().getString(id);
	}
	
	public int color(int id) {
		return getResources().getColor(id);
	}
	
	public static int color(Context context, int id) {
		return context.getResources().getColor(id);
	}
	
	public Drawable drawable(int id) {
		return getResources().getDrawable(id);
	}
	
	public static Drawable drawable(Context context, int id) {
		return context.getResources().getDrawable(id);
	}
	
	//========================================================================================== Activity
	
	public static ArrayList<Activity> activityList = new ArrayList<>();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		logActivity("onCreate " + this.getLocalClassName());
		super.onCreate(savedInstanceState);
		startActivity(this);
	}
	
	@Override
	public void onStart() {
		logActivity("onStart " + this.getLocalClassName());
		super.onStart();
	}
	
	@Override
	public void onResume() {
		logActivity("onResume " + this.getLocalClassName());
		super.onResume();
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
	}
	
	@Override
	public void onPause() {
		logActivity("onPause " + this.getLocalClassName());
		super.onPause();
	}
	
	@Override
	public void onStop() {
		logActivity("onStop " + this.getLocalClassName());
		super.onStop();
	}
	
	@Override
	public void onRestart() {
		logActivity("onRestart " + this.getLocalClassName());
		super.onRestart();
	}
	
	@Override
	public void onDestroy() {
		logActivity("onDestroy " + this.getLocalClassName());
		super.onDestroy();
		finishActivity(this);
	}
	
	
	static void startActivity(Activity activity) {
		activityList.add(activity);
		printActivityLog(activity.getLocalClassName() + " start");
	}
	
	static void finishActivity(Activity activity) {
		boolean exist = false;
		int size = activityList.size();
		for (int i = 0; i < size; i++) {
			if (activityList.get(i) == activity) {
				activityList.get(i).finish();
				activityList.remove(i);
				exist = true;
				break;
			}
		}
		printActivityLog(activity.getLocalClassName() + " finish" + (exist ? "" : " error"));
	}
	
	static void restartApp(Activity activity) {
		
		int size = activityList.size();
		for (int i = size - 1; i >= 0; i--) {
			activityList.get(i).finish();
			activityList.remove(i);
		}
		activity.startActivity(new Intent(activity, Main.class));
		printActivityLog(activity.getLocalClassName() + " requestRestart");
		
		Process.killProcess(Process.myPid());
	}
	
	static void printActivityLog(String log) {
		StringBuilder str = new StringBuilder("ACTIVITY STACK - " + log + "[");
		int size = activityList.size();
		for (int i = 0; i < size; i++) {
			Activity activity = activityList.get(i);
			str.append(", ").append(activity.getLocalClassName());
		}
		logActivity(str + "]");
	}
	
	static void requestRestart(final Context context) {
		new AlertDialog.Builder(context)
			.setTitle(lang(context, R.string.requireRestart))
			.setMessage(lang(context, R.string.doYouWantToRestartApp))
			.setPositiveButton(lang(context, R.string.restart), (dialog, which) -> {
				restartApp((Activity) context);
				dialog.dismiss();
			})
			.setNegativeButton(lang(context, R.string.cancel), (dialog, which) -> {
				dialog.dismiss();
				((Activity) context).finish();
			})
			.show();
	}
}
