package com.kimjisub.launchpad.manage;

public class Log {
	public static void log(String msg) {
		printLog("com.kimjisub._log", msg);
	}
	
	public static void test(String msg) {
		printLog("com.kimjisub._test", msg);
	}
	
	public static void network(String msg) {
		printLog("com.kimjisub._network", msg);
	}
	
	public static void activity(String msg) {
		printLog("com.kimjisub._activity", msg);
	}
	
	public static void firebase(String msg) {
		printLog("com.kimjisub._firebase", msg);
	}
	
	public static void vungle(String msg) {
		printLog("com.kimjisub._vungle", msg);
	}
	
	public static void admob(String msg) {
		printLog("com.kimjisub._admob", msg);
	}
	
	public static void midi(String msg) {
		printLog("com.kimjisub._midi", msg);
	}
	
	public static void midiDetail(String msg) {
		printLog("com.kimjisub._mididetail", msg);
	}
	
	public static void driver(String msg) {
		printLog("com.kimjisub._driver", msg);
	}
	
	public static void driverCycle(String msg) {
		printLog("com.kimjisub._cycle", msg);
	}
	
	public static void err(String msg) {
		printLog("com.kimjisub._err", msg);
	}
	
	private static void printLog(String tag, String msg) {
		if (msg == null)
			msg = "(null)";
		android.util.Log.e(tag, msg);
	}
}
