package com.kimjisub.launchpad.manage;

import android.util.Log;

public class Tools {
	public static void log(String msg) {
		if (msg == null)
			msg = "(null)";
		Log.e("com.kimjisub.log", msg);
	}

	public static void logActivity(String msg) {
		if (msg == null)
			msg = "(null)";
		Log.e("com.kimjisub.activity", msg);
	}
	public static void logRecv(String msg) {
		if (msg == null)
			msg = "(null)";
		Log.e("com.kimjisub.recv", msg);
	}

	public static void logErr(String msg) {
		if (msg == null)
			msg = "(null)";
		Log.e("com.kimjisub.err", msg);
	}
}
