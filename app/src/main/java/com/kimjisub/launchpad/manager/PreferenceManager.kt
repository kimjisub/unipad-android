package com.kimjisub.launchpad.manager

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.os.Environment
import java.io.File

object PreferenceManager {
	//todo change to kotlin getter setter style
	private val DATA = "data"

	object LaunchpadConnectMethod {
		private val TAG = "LaunchpadConnectMethod"
		fun save(context: Context, value: Int) {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			val editor: Editor = pref.edit()
			editor.putInt(TAG, value)
			editor.apply()
		}

		fun load(context: Context): Int {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			return pref.getInt(TAG, 0)
		}
	}

	object FileExplorerPath {
		private val TAG = "FileExplorerPath"
		fun save(context: Context, value: String) {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			val editor: Editor = pref.edit()
			editor.putString(TAG, value)
			editor.apply()
		}

		fun load(context: Context): String {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			var url: String = pref.getString(TAG, "${System.getenv("SECONDARY_STORAGE")}/Download") ?: ""
			if (!File(url).isDirectory) url = Environment.getExternalStorageDirectory().path
			if (!File(url).isDirectory) url = "/"
			return url
		}
	}

	object PrevAdsShowTime {
		private val TAG = "PrevAdsShowTime"
		fun save(context: Context, value: Long) {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			val editor: Editor = pref.edit()
			editor.putLong(TAG, value)
			editor.apply()
		}

		fun load(context: Context): Long {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			return pref.getLong(TAG, 0)
		}
	}

	object SelectedTheme {
		private val TAG = "SelectedTheme"
		fun save(context: Context, value: String) {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			val editor: Editor = pref.edit()
			editor.putString(TAG, value)
			editor.apply()
		}

		fun load(context: Context): String {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			return pref.getString(TAG, "com.kimjisub.launchpad") ?: "com.kimjisub.launchpad"
		}
	}

	object PrevStoreCount {
		private val TAG = "PrevStoreCount"
		fun save(context: Context, value: Long) {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			val editor: Editor = pref.edit()
			editor.putLong(TAG, value)
			editor.apply()
		}

		fun load(context: Context): Long {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			return pref.getLong(TAG, 0)
		}
	}
}