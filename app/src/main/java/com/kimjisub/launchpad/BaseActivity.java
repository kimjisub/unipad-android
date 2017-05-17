package com.kimjisub.launchpad;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;

import com.tsengvn.typekit.Typekit;
import com.tsengvn.typekit.TypekitContextWrapper;

import java.util.ArrayList;

/**
 * Created by rlawl on 2017-02-11.
 */

public class BaseActivity extends AppCompatActivity {
	
	public static ArrayList<Activity> aList = new ArrayList<Activity>();
	public static com.kimjisub.launchpad.Setting setting = null;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		if (정보.설정.폰트설정.불러오기(BaseActivity.this)) {
			Typekit.getInstance()
				.addNormal(Typekit.createFromAsset(this, "Montserrat-Regular.ttf"))
				.addCustom1(Typekit.createFromAsset(this, "Quicksand-Bold.ttf"));
		} else {
			Typekit.getInstance()
				.addNormal(null)
				.addCustom1(Typekit.createFromAsset(this, "Quicksand-Bold.ttf"));
		}
	}
	
	static void startActivity(Activity activity) {
		aList.add(activity);
		activityLog(activity.getLocalClassName() + " start");
	}
	
	static void finishActivity(Activity activity) {
		boolean exist = false;
		int size = aList.size();
		for (int i = 0; i < size; i++) {
			if (aList.get(i) == activity) {
				aList.get(i).finish();
				aList.remove(i);
				exist = true;
				break;
			}
		}
		activityLog(activity.getLocalClassName() + " finish" + (exist ? "" : " error"));
	}
	
	static void restartApp(Activity activity) {
		
		int size = aList.size();
		for (int i = size - 1; i >= 0; i--) {
			aList.get(i).finish();
			aList.remove(i);
		}
		activity.startActivity(new Intent(activity, Intro.class));
		activityLog(activity.getLocalClassName() + " restart");
		
		Process.killProcess(Process.myPid());
	}
	
	static void activityLog(String log) {
		String str = "엑티비티 스텍 (" + log + ")";
		int size = aList.size();
		for (int i = 0; i < size; i++) {
			Activity activity = aList.get(i);
			str += "\n" + activity.getLocalClassName();
		}
		화면.log(str);
	}
	
	static void 재시작(final Context context) {
		new AlertDialog.Builder(context)
			.setTitle(언어(context, R.string.requireRestart))
			.setMessage(언어(context, R.string.doYouWantToRestartApp))
			.setPositiveButton(언어(context, R.string.restart), new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					BaseActivity.restartApp((Activity) context);
					dialog.dismiss();
				}
			})
			.setNegativeButton(언어(context, R.string.cancel), new AlertDialog.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
					((Activity) context).finish();
				}
			})
			.show();
	}
	
	
	static String 언어(Context context, int id) {
		return context.getResources().getString(id);
	}
	
	
	@Override
	protected void attachBaseContext(Context newBase) {
		
		super.attachBaseContext(TypekitContextWrapper.wrap(newBase));
		
	}
}
