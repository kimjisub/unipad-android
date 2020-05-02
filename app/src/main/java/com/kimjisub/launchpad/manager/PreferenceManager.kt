package com.kimjisub.launchpad.manager

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Environment
import androidx.core.content.edit
import java.io.File

class PreferenceManager(
	val context: Context
) {
	private val name = "data"

	private val launchpadConnectMethodTag = "LaunchpadConnectMethod"
	var launchpadConnectMethod: Int
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(name, MODE_PRIVATE)
			return pref.getInt(launchpadConnectMethodTag, 0)
		}
		set(value) {
			context.getSharedPreferences(name, MODE_PRIVATE).edit {
				putInt(launchpadConnectMethodTag, value)
			}
		}

	private val fileExplorerPathTag = "FileExplorerPath"
	var fileExplorerPath: String
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(name, MODE_PRIVATE)
			var url: String = pref.getString(fileExplorerPathTag, "${System.getenv("SECONDARY_STORAGE")}/Download") ?: ""
			if (!File(url).isDirectory) url = Environment.getExternalStorageDirectory().path
			if (!File(url).isDirectory) url = "/"
			return url
		}
		set(value) {
			context.getSharedPreferences(name, MODE_PRIVATE).edit {
				putString(fileExplorerPathTag, value)
			}
		}

	private val prevAdsShowTimeTag = "PrevAdsShowTime"
	var prevAdsShowTime: Long
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(name, MODE_PRIVATE)
			return pref.getLong(prevAdsShowTimeTag, 0)
		}
		set(value) {
			context.getSharedPreferences(name, MODE_PRIVATE).edit {
				putLong(prevAdsShowTimeTag, value)
			}
		}

	private val selectedThemeTag = "SelectedTheme"
	var selectedTheme: String
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(name, MODE_PRIVATE)
			return pref.getString(selectedThemeTag, "com.kimjisub.launchpad") ?: "com.kimjisub.launchpad"
		}
		set(value) {
			context.getSharedPreferences(name, MODE_PRIVATE).edit {
				putString(selectedThemeTag, value)
			}
		}

	private val prevStoreCountTag = "PrevStoreCount"
	var prevStoreCount: Long
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(name, MODE_PRIVATE)
			return pref.getLong(prevStoreCountTag, 0)
		}
		set(value) {
			context.getSharedPreferences(name, MODE_PRIVATE).edit {
				putLong(prevStoreCountTag, value)
			}
		}

	private val defaultSortTag = "DefaultSort"
	var defaultSort: Int
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(name, MODE_PRIVATE)
			return pref.getInt(defaultSortTag, 1)
		}
		set(value) {
			context.getSharedPreferences(name, MODE_PRIVATE).edit {
				putInt(defaultSortTag, value)
			}
		}
}