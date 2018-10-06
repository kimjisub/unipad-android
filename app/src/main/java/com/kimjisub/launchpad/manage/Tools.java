package com.kimjisub.launchpad.manage;

import android.util.Log;

public class Tools {
	public static void log(String msg) {
		if (msg == null)
			msg = "(null)";
		Log.e("com.kimjisub._log", msg);
	}
	
	public static void logActivity(String msg) {
		if (msg == null)
			msg = "(null)";
		Log.e("com.kimjisub._activity", msg);
	}
	
	public static void logFirebase(String msg) {
		if (msg == null)
			msg = "(null)";
		Log.e("com.kimjisub._firebase", msg);
	}
	
	public static void logAds(String msg) {
		if (msg == null)
			msg = "(null)";
		Log.e("com.kimjisub._ads", msg);
	}
	
	public static void logRecv(String msg) {
		if (msg == null)
			msg = "(null)";
		Log.e("com.kimjisub._recv", msg);
	}
	
	public static void logSig(String msg) {
		if (msg == null)
			msg = "(null)";
		Log.e("com.kimjisub._sig", msg);
	}
	
	public static void logErr(String msg) {
		if (msg == null)
			msg = "(null)";
		Log.e("com.kimjisub._err", msg);
	}
}
