package com.kimjisub.launchpad.manager

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit

class PreferenceManager(
	val context: Context,
) {
	private val pref: SharedPreferences = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)

	var launchpadConnectMethod: Int
		get() {
			return pref.getInt(KEY_LAUNCHPAD_CONNECT_METHOD, 0)
		}
		set(value) {
			pref.edit {
				putInt(KEY_LAUNCHPAD_CONNECT_METHOD, value)
			}
		}

	var selectedTheme: String
		get() {
			return pref.getString(KEY_SELECTED_THEME, context.packageName)
				?: context.packageName
		}
		set(value) {
			pref.edit {
				putString(KEY_SELECTED_THEME, value)
			}
		}

	var prevStoreCount: Long
		get() {
			return pref.getLong(KEY_PREV_STORE_COUNT, 0)
		}
		set(value) {
			pref.edit {
				putLong(KEY_PREV_STORE_COUNT, value)
			}
		}

	var sortMethod: Int
		get() {
			return pref.getInt(KEY_SORT_METHOD, 4)
		}
		set(value) {
			pref.edit {
				putInt(KEY_SORT_METHOD, value)
			}
		}

	var sortOrder: Boolean
		get() {
			return pref.getBoolean(KEY_SORT_ORDER, true)
		}
		set(value) {
			pref.edit {
				putBoolean(KEY_SORT_ORDER, value)
			}
		}

	var downloadStoragePath: String?
		get() = pref.getString(KEY_DOWNLOAD_STORAGE_PATH, null)
		set(value) {
			pref.edit {
				if (value != null) putString(KEY_DOWNLOAD_STORAGE_PATH, value)
				else remove(KEY_DOWNLOAD_STORAGE_PATH)
			}
		}

	var backupSafUri: String?
		get() = pref.getString(KEY_BACKUP_SAF_URI, null)
		set(value) {
			pref.edit {
				if (value != null) putString(KEY_BACKUP_SAF_URI, value)
				else remove(KEY_BACKUP_SAF_URI)
			}
		}

	companion object {
		private const val PREF_NAME = "data"
		private const val KEY_LAUNCHPAD_CONNECT_METHOD = "LaunchpadConnectMethod"
		private const val KEY_SELECTED_THEME = "SelectedTheme"
		private const val KEY_PREV_STORE_COUNT = "PrevStoreCount"
		private const val KEY_SORT_METHOD = "SortMethod"
		private const val KEY_SORT_ORDER = "SortOrder"
		private const val KEY_DOWNLOAD_STORAGE_PATH = "download_storage_path"
		private const val KEY_BACKUP_SAF_URI = "backup_saf_uri"
	}
}
