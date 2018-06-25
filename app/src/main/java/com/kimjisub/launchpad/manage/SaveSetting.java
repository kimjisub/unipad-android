package com.kimjisub.launchpad.manage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import static android.content.Context.MODE_PRIVATE;
import static android.preference.PreferenceManager.getDefaultSharedPreferences;
import static com.kimjisub.launchpad.manage.Tools.log;

public class SaveSetting {

	public static class LaunchpadConnectMethod {

		public static void save(Context context, int value) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putInt("LaunchpadConnectMethod", value);
			editor.apply();
		}

		public static int load(Context context) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			return pref.getInt("LaunchpadConnectMethod", 0);
		}
	}

	public static class FileExplorerPath {

		public static void save(Context context, String value) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putString("FileExplorerPath", value);
			editor.apply();
		}

		public static String load(Context context) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			String url = pref.getString("FileExplorerPath", System.getenv("SECONDARY_STORAGE") + "/Download");
			if (!new java.io.File(url).isDirectory())
				url = Environment.getExternalStorageDirectory().getPath();
			if (!new java.io.File(url).isDirectory())
				url = "/";

			return url;
		}
	}

	public static class IsUsingSDCard {

		private static String URL;

		public static void save(Context context, boolean value) {
			SharedPreferences pref = getDefaultSharedPreferences(context);
			SharedPreferences.Editor editor = pref.edit();
			editor.putBoolean("use_sd_card", value);
			editor.apply();
		}

		public static boolean load(Context context) {
			SharedPreferences pref = getDefaultSharedPreferences(context);
			Boolean isSDCard = pref.getBoolean("use_sd_card", false);

			URL = Environment.getExternalStorageDirectory().getPath() + "/Unipad";
			if (isSDCard) {
				if (FileManager.isSDCardAvalable())
					URL = FileManager.getExternalSDCardPath() + "/Unipad";
				else {
					save(context, false);
					return load(context);
				}
			}
			log("UnipackRootURL : " + URL);
			return isSDCard;
		}

		public static String URL(Context context) {
			load(context);
			return URL;
		}
	}

	public static class PrevAdsShowTime {

		public static void save(Context context, long value) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putLong("PrevAdsShowTime", value);
			editor.apply();
		}

		public static long load(Context context) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			return pref.getLong("PrevAdsShowTime", 0);
		}
	}

	public static class SelectedTheme {

		public static void save(Context context, String value) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putString("SelectedTheme", value);
			editor.apply();
		}

		public static String load(Context context) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			return pref.getString("SelectedTheme", "com.kimjisub.launchpad.theme");
		}
	}

	public static class PrevStoreCount {

		public static void save(Context context, long value) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			SharedPreferences.Editor editor = pref.edit();
			editor.putLong("PrevStoreCount", value);
			editor.apply();
		}

		public static long load(Context context) {
			SharedPreferences pref = context.getSharedPreferences("data", MODE_PRIVATE);
			return pref.getLong("PrevStoreCount", 0);
		}
	}

}
