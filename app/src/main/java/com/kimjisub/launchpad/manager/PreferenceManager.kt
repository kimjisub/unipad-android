package com.kimjisub.launchpad.manager

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.os.Environment
import androidx.core.content.edit
import java.io.File

class PreferenceManager(
	val context: Context,
) {
	private val name = "data"
	private val pref: SharedPreferences = context.getSharedPreferences(name, MODE_PRIVATE)

	private val launchpadConnectMethodTag = "LaunchpadConnectMethod"
	var launchpadConnectMethod: Int
		get() {
			return pref.getInt(launchpadConnectMethodTag, 0)
		}
		set(value) {
			pref.edit {
				putInt(launchpadConnectMethodTag, value)
			}
		}

	private val fileExplorerPathTag = "FileExplorerPath"
	var fileExplorerPath: String
		get() {
			var url: String = pref.getString(
				fileExplorerPathTag,
				"${System.getenv("SECONDARY_STORAGE")}/Download"
			) ?: ""
			if (!File(url).isDirectory) url = Environment.getExternalStorageDirectory().path
			if (!File(url).isDirectory) url = "/"
			return url
		}
		set(value) {
			pref.edit {
				putString(fileExplorerPathTag, value)
			}
		}

	private val storageActiveTag = "storage_active"
	var storageActive: Set<String>
		get() {
			return pref.getStringSet(storageActiveTag, setOf()) ?: setOf()
		}
		set(value) {
			pref.edit {
				putStringSet(storageActiveTag, value)
			}
		}

	private val mainStorageTag = "MainStorage"
	var mainStorage: String
		get() {
			return pref.getString(mainStorageTag, "") ?: ""
		}
		set(value) {
			pref.edit {
				putString(mainStorageTag, value)
			}
		}

	private val prevAdsShowTimeTag = "PrevAdsShowTime"
	var prevAdsShowTime: Long
		get() {
			return pref.getLong(prevAdsShowTimeTag, 0)
		}
		set(value) {
			pref.edit {
				putLong(prevAdsShowTimeTag, value)
			}
		}

	private val selectedThemeTag = "SelectedTheme"
	var selectedTheme: String
		get() {
			return pref.getString(selectedThemeTag, "com.kimjisub.launchpad")
				?: "com.kimjisub.launchpad"
		}
		set(value) {
			pref.edit {
				putString(selectedThemeTag, value)
			}
		}

	private val prevStoreCountTag = "PrevStoreCount"
	var prevStoreCount: Long
		get() {
			return pref.getLong(prevStoreCountTag, 0)
		}
		set(value) {
			pref.edit {
				putLong(prevStoreCountTag, value)
			}
		}

	private val sortMethodTag = "SortMethod"
	var sortMethod: Int
		get() {
			return pref.getInt(sortMethodTag, 4)
		}
		set(value) {
			pref.edit {
				putInt(sortMethodTag, value)
			}
		}

	private val sortOrderTag = "SortOrder"
	var sortOrder: Boolean
		get() {
			return pref.getBoolean(sortOrderTag, true)
		}
		set(value) {
			pref.edit {
				putBoolean(sortOrderTag, value)
			}
		}

	private val downloadCouponCountTag = "DownloadCouponCount"
	var downloadCouponCount: Int
		get() {
			return pref.getInt(downloadCouponCountTag, 0)
		}
		set(value) {
			pref.edit {
				putInt(downloadCouponCountTag, value)
			}
		}

	private val playCouponCountTag = "PlayCouponCount"
	var playCouponCount: Int
		get() {
			return pref.getInt(playCouponCountTag, 0)
		}
		set(value) {
			pref.edit {
				putInt(playCouponCountTag, value)
			}
		}
}