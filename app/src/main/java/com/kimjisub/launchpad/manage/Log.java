package com.kimjisub.launchpad.manage;

public class Log {
	public static final boolean DEBUG = true;

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

	public static void sqlite(String msg) {
		printLog("com.kimjisub._sqlite", msg);
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

		String space = "";
		for (int i = 0; i < 30 - tag.length(); i++)
			space += " ";

		String trace1 = trace(Thread.currentThread().getStackTrace(), 5);
		String trace2 = trace(Thread.currentThread().getStackTrace(), 4);
		String detailMsg = String.format("%s%-50s%-40s%-40s", space, msg, trace1, trace2);

		android.util.Log.e(tag, DEBUG ? detailMsg : msg);
	}

	public static String trace(final StackTraceElement e[], final int level) {
		if (e != null && e.length >= level) {
			final StackTraceElement s = e[level];

			if (s != null)
				return e[level].getMethodName() + "(" + e[level].getFileName() + ":" + e[level].getLineNumber() + ")";
		}
		return null;
	}
}
