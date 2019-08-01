package com.kimjisub.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import static android.content.Context.MODE_PRIVATE;

public class PreferenceManager {

	final static String DATA = "data";

	public static class LaunchpadConnectMethod {
		final static String TAG = "LaunchpadConnectMethod";

		public static void save(Context context, int value) {
			SharedPreferences pref = context.getSharedPreferences(DATA, MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putInt(TAG, value);
			editor.apply();
		}

		public static int load(Context context) {
			SharedPreferences pref = context.getSharedPreferences(DATA, MODE_PRIVATE);
			return pref.getInt(TAG, 0);
		}
	}

	public static class FileExplorerPath {
		final static String TAG = "FileExplorerPath";

		public static void save(Context context, String value) {
			SharedPreferences pref = context.getSharedPreferences(DATA, MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putString(TAG, value);
			editor.apply();
		}

		public static String load(Context context) {
			SharedPreferences pref = context.getSharedPreferences(DATA, MODE_PRIVATE);
			String url = pref.getString(TAG, System.getenv("SECONDARY_STORAGE") + "/Download");
			if (!new java.io.File(url).isDirectory())
				url = Environment.getExternalStorageDirectory().getPath();
			if (!new java.io.File(url).isDirectory())
				url = "/";

			return url;
		}
	}

	public static class PrevAdsShowTime {
		final static String TAG = "PrevAdsShowTime";

		public static void save(Context context, long value) {
			SharedPreferences pref = context.getSharedPreferences(DATA, MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putLong(TAG, value);
			editor.apply();
		}

		public static long load(Context context) {
			SharedPreferences pref = context.getSharedPreferences(DATA, MODE_PRIVATE);
			return pref.getLong(TAG, 0);
		}
	}

	public static class SelectedTheme {
		final static String TAG = "SelectedTheme";

		public static void save(Context context, String value) {
			SharedPreferences pref = context.getSharedPreferences(DATA, MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putString(TAG, value);
			editor.apply();
		}

		public static String load(Context context) {
			SharedPreferences pref = context.getSharedPreferences(DATA, MODE_PRIVATE);
			return pref.getString(TAG, "com.kimjisub.launchpad");
		}
	}

	public static class PrevStoreCount {
		final static String TAG = "PrevStoreCount";

		public static void save(Context context, long value) {
			SharedPreferences pref = context.getSharedPreferences(DATA, MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putLong(TAG, value);
			editor.apply();
		}

		public static long load(Context context) {
			SharedPreferences pref = context.getSharedPreferences(DATA, MODE_PRIVATE);
			return pref.getLong(TAG, 0);
		}
	}
}
