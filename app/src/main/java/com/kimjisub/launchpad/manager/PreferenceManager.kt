package com.kimjisub.launchpad.manager

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Environment
import androidx.core.content.edit
import java.io.File

class PreferenceManager (
	val context: Context
){
	private val DATA = "data"

	private val LaunchpadConnectMethodTag = "LaunchpadConnectMethod"
	var LaunchpadConnectMethod: Int
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			return pref.getInt(LaunchpadConnectMethodTag, 0)
		}
		set(value) {
			context.getSharedPreferences(DATA, MODE_PRIVATE).edit {
				putInt(LaunchpadConnectMethodTag, value)
			}
		}

	private val FileExplorerPathTag = "FileExplorerPath"
	var FileExplorerPath: String
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			var url: String = pref.getString(FileExplorerPathTag, "${System.getenv("SECONDARY_STORAGE")}/Download") ?: ""
			if (!File(url).isDirectory) url = Environment.getExternalStorageDirectory().path
			if (!File(url).isDirectory) url = "/"
			return url
		}
		set(value) {
			context.getSharedPreferences(DATA, MODE_PRIVATE).edit {
				putString(FileExplorerPathTag, value)
			}
		}

	private val PrevAdsShowTimeTag = "PrevAdsShowTime"
	var PrevAdsShowTime: Long
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			return pref.getLong(PrevAdsShowTimeTag, 0)
		}
		set(value) {
			context.getSharedPreferences(DATA, MODE_PRIVATE).edit {
				putLong(PrevAdsShowTimeTag, value)
			}
		}

	private val SelectedThemeTag = "SelectedTheme"
	var SelectedTheme: String
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			return pref.getString(SelectedThemeTag, "com.kimjisub.launchpad") ?: "com.kimjisub.launchpad"
		}
		set(value) {
			context.getSharedPreferences(DATA, MODE_PRIVATE).edit {
				putString(SelectedThemeTag, value)
			}
		}

	private val PrevStoreCountTag = "PrevStoreCount"
	var PrevStoreCount: Long
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(DATA, MODE_PRIVATE)
			return pref.getLong(PrevStoreCountTag, 0)
		}
		set(value) {
			context.getSharedPreferences(DATA, MODE_PRIVATE).edit {
				putLong(PrevStoreCountTag, value)
			}
		}
}