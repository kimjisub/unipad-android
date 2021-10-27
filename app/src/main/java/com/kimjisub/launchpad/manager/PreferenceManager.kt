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
			var url: String = pref.getString(
				fileExplorerPathTag,
				"${System.getenv("SECONDARY_STORAGE")}/Download"
			) ?: ""
			if (!File(url).isDirectory) url = Environment.getExternalStorageDirectory().path
			if (!File(url).isDirectory) url = "/"
			return url
		}
		set(value) {
			context.getSharedPreferences(name, MODE_PRIVATE).edit {
				putString(fileExplorerPathTag, value)
			}
		}

	private val activeStorageTag = "ActiveStorage"
	var activeStorage: String
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(name, MODE_PRIVATE)
			return pref.getString(activeStorageTag, "") ?: ""
		}
		set(value) {
			context.getSharedPreferences(name, MODE_PRIVATE).edit {
				putString(activeStorageTag, value)
			}
		}
	private val mainStorageTag = "MainStorage"
	var mainStorage: String
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(name, MODE_PRIVATE)
			return pref.getString(mainStorageTag, "") ?: ""
		}
		set(value) {
			context.getSharedPreferences(name, MODE_PRIVATE).edit {
				putString(mainStorageTag, value)
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
			return pref.getString(selectedThemeTag, "com.kimjisub.launchpad")
				?: "com.kimjisub.launchpad"
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

	private val sortMethodTag = "SortMethod"
	var sortMethod: Int
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(name, MODE_PRIVATE)
			return pref.getInt(sortMethodTag, 4)
		}
		set(value) {
			context.getSharedPreferences(name, MODE_PRIVATE).edit {
				putInt(sortMethodTag, value)
			}
		}

	private val sortTypeTag = "SortType"
	var sortType: Boolean
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(name, MODE_PRIVATE)
			return pref.getBoolean(sortTypeTag, true)
		}
		set(value) {
			context.getSharedPreferences(name, MODE_PRIVATE).edit {
				putBoolean(sortTypeTag, value)
			}
		}

	private val downloadCouponCountTag = "DownloadCouponCount"
	var downloadCouponCount: Int
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(name, MODE_PRIVATE)
			return pref.getInt(downloadCouponCountTag, 0)
		}
		set(value) {
			context.getSharedPreferences(name, MODE_PRIVATE).edit {
				putInt(downloadCouponCountTag, value)
			}
		}

	private val playCouponCountTag = "PlayCouponCount"
	var playCouponCount: Int
		get() {
			val pref: SharedPreferences = context.getSharedPreferences(name, MODE_PRIVATE)
			return pref.getInt(playCouponCountTag, 0)
		}
		set(value) {
			context.getSharedPreferences(name, MODE_PRIVATE).edit {
				putInt(playCouponCountTag, value)
			}
		}
}