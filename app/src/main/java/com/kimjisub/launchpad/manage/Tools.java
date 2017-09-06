package com.kimjisub.launchpad.manage;

import android.content.Context;
import android.util.Log;

/**
 * Created by kimjisub ON 2017. 7. 14..
 */

public class Tools {
	public static void log(String msg) {
		Log.d("com.kimjisub.log", msg);
	}
	
	public static void logSig(String msg) {
		Log.d("com.kimjisub.sig", msg);
	}
	
	public static void logRecv(String msg) {
		Log.d("com.kimjisub.recv", msg);
	}
	
	public static String lang(Context context, int id) {
		return context.getResources().getString(id);
	}
}
