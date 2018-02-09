package com.kimjisub.launchpad;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Process;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import java.util.ArrayList;

import static com.kimjisub.launchpad.manage.Tools.logActivity;

public class BaseActivity extends AppCompatActivity {

	public static ArrayList<Activity> activityList = new ArrayList();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		logActivity("onCreate " + this.getLocalClassName());
		super.onCreate(savedInstanceState);
		startActivity(this);
	}

	@Override
	protected void onStart() {
		logActivity("onStart " + this.getLocalClassName());
		super.onStart();
	}

	@Override
	protected void onResume() {
		logActivity("onResume " + this.getLocalClassName());
		super.onResume();
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
	}

	@Override
	protected void onPause() {
		logActivity("onPause " + this.getLocalClassName());
		super.onPause();
	}

	@Override
	protected void onStop() {
		logActivity("onStop " + this.getLocalClassName());
		super.onStop();
	}

	@Override
	protected void onRestart() {
		logActivity("onRestart " + this.getLocalClassName());
		super.onRestart();
	}

	@Override
	protected void onDestroy() {
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
		activity.startActivity(new Intent(activity, Intro.class));
		printActivityLog(activity.getLocalClassName() + " requestRestart");

		Process.killProcess(Process.myPid());
	}

	static void printActivityLog(String log) {
		String str = "ACTIVITY STACK - " + log;
		int size = activityList.size();
		for (int i = 0; i < size; i++) {
			Activity activity = activityList.get(i);
			str += ", " + activity.getLocalClassName();
		}
		logActivity(str);
	}

	static void requestRestart(final Context context) {
		new AlertDialog.Builder(context)
			.setTitle(lang(context, R.string.requireRestart))
			.setMessage(lang(context, R.string.doYouWantToRestartApp))
			.setPositiveButton(lang(context, R.string.restart), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					restartApp((Activity) context);
					dialog.dismiss();
				}
			})
			.setNegativeButton(lang(context, R.string.cancel), new AlertDialog.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					((Activity) context).finish();
				}
			})
			.show();
	}


	public String lang(int id) {
		return (BaseActivity.this).getResources().getString(id);
	}

	public static String lang(Context context, int id) {
		return context.getResources().getString(id);
	}
}
