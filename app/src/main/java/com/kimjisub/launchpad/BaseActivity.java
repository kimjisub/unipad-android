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
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.kimjisub.launchpad.utils.Log;
import com.kimjisub.launchpad.utils.SettingManager;
import com.vungle.warren.InitCallback;
import com.vungle.warren.Vungle;

import java.util.ArrayList;

import static com.kimjisub.launchpad.utils.Constant.ADMOB;
import static com.kimjisub.launchpad.utils.Constant.ADSCOOLTIME;
import static com.kimjisub.launchpad.utils.Constant.VUNGLE;

public abstract class BaseActivity extends AppCompatActivity {

	// ============================================================================================= Scale

	public static int Scale_Width = 0;
	public static int Scale_Height = 0;
	public static int Scale_PaddingWidth = 0;
	public static int Scale_PaddingHeight = 0;
	public static ArrayList<Activity> activityList = new ArrayList<>();

	// ============================================================================================= Ads Cooltime
	private static InterstitialAd interstitialAd;

	public static void showToast(Context context, String msg) {
		Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
	}

	// ============================================================================================= vungle

	public static void showToast(Context context, int resId) {
		Toast.makeText(context, resId, Toast.LENGTH_SHORT).show();
	}

	// ============================================================================================= Admob

	public static void showDialog(Context context, String title, String content) {
		new AlertDialog.Builder(context)
				.setTitle(title)
				.setMessage(content)
				.setPositiveButton(lang(context, R.string.accept), null)
				.show();
	}

	public static String lang(Context context, int id) {
		return context.getResources().getString(id);
	}

	public static int color(Context context, int id) {
		return context.getResources().getColor(id);
	}

	public static Drawable drawable(Context context, int id) {
		return context.getResources().getDrawable(id);
	}

	// ============================================================================================= Show

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
		activity.startActivity(new Intent(activity, MainActivity.class));
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
		Log.activity(str + "]");
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

	// ============================================================================================= Function
	
	/*public int pxToDp(int pixel) {
		float dp = 0;
		try {
			DisplayMetrics metrics = getResources().getDisplayMetrics();
			dp = pixel / (metrics.densityDpi / 160f);
		} catch (Exception ignored) {
		}
		return (int) dp;
	}*/

	void rescanScale(LinearLayout LL_scale, LinearLayout LL_paddingScale) {
		Scale_Width = LL_scale.getWidth();
		Scale_Height = LL_scale.getHeight();
		Scale_PaddingWidth = LL_paddingScale.getWidth();
		Scale_PaddingHeight = LL_paddingScale.getHeight();
	}

	// ============================================================================================= Get Resources

	public boolean checkAdsCooltime() {
		long prevTime = SettingManager.PrevAdsShowTime.load(this);
		long currTime = System.currentTimeMillis();

		return (currTime < prevTime) || currTime - prevTime >= ADSCOOLTIME;
	}

	public void updateAdsCooltime() {
		long currTime = System.currentTimeMillis();
		SettingManager.PrevAdsShowTime.save(this, currTime);
	}

	public void initVungle() {
		if (!Vungle.isInitialized()) {
			Log.vungle("isInitialized() == false");
			Log.vungle("init start");
			Vungle.init(VUNGLE.APPID, getApplicationContext(), new InitCallback() {
				@Override
				public void onSuccess() {
					// Initialization has succeeded and SDK is ready to load an ad or play one if there
					// is one pre-cached already
					Log.vungle("init onSuccess()");
				}

				@Override
				public void onError(Throwable throwable) {
					// Initialization error occurred - throwable.getLocalizedMessage() contains error message
					Log.vungle("init onError() == " + throwable.getLocalizedMessage());
				}

				@Override
				public void onAutoCacheAdAvailable(String placementId) {
					// Callback to notify when an ad becomes available for the auto-cached placement
					// NOTE: This callback works only for the auto-cached placement. Otherwise, please use
					// LoadAdCallback with loadAd API for loading placements.
					Log.vungle("init onAutoCacheAdAvailable()");
				}
			});
		} else {
			Log.vungle("isInitialized() == true");
		}
	}

	public void showAdmob() {
		Log.admob("showAdmob");

		if (interstitialAd.isLoaded()) {
			Log.admob("isLoaded");
			interstitialAd.show();
			loadAdmob();
			Log.admob("show!");
		} else {
			Log.admob("! isLoaded (set listener)");
			interstitialAd.setAdListener(new AdListener() {
				public void onAdLoaded() {
					interstitialAd.show();
					loadAdmob();
					Log.admob("show!");
				}
			});
		}
	}

	public void initAdmob() {
		Log.admob("initAdmob");
		loadAdmob();
	}

	void loadAdmob() {
		interstitialAd = new InterstitialAd(this);
		interstitialAd.setAdUnitId(ADMOB.MAIN_START);
		interstitialAd.loadAd(new AdRequest.Builder()
				.addTestDevice("36C3684AAD25CDF5A6360640B20DC084")
				.build());
	}

	// ============================================================================================= Activity

	public void showToast(String msg) {
		showToast(this, msg);
	}

	public void showToast(int resId) {
		showToast(this, resId);
	}

	public void showDialog(String title, String content) {
		showDialog(this, title, content);
	}

	public int dpToPx(float dp) {
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		float px = dp * (metrics.densityDpi / 160f);
		return Math.round(px);
	}

	public String lang(int id) {
		return lang(this, id);
	}

	public int color(int id) {
		return color(this, id);
	}

	public Drawable drawable(int id) {
		return drawable(this, id);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.activity("onCreate " + this.getLocalClassName());
		super.onCreate(savedInstanceState);
		startActivity(this);
	}

	@Override
	public void onStart() {
		Log.activity("onStart " + this.getLocalClassName());
		super.onStart();
	}

	@Override
	public void onResume() {
		Log.activity("onResume " + this.getLocalClassName());
		super.onResume();
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		initVungle();
	}

	@Override
	public void onPause() {
		Log.activity("onPause " + this.getLocalClassName());
		super.onPause();
	}

	@Override
	public void onStop() {
		Log.activity("onStop " + this.getLocalClassName());
		super.onStop();
	}

	@Override
	public void onRestart() {
		Log.activity("onRestart " + this.getLocalClassName());
		super.onRestart();
	}

	@Override
	public void onDestroy() {
		Log.activity("onDestroy " + this.getLocalClassName());
		super.onDestroy();
		finishActivity(this);
	}
}
