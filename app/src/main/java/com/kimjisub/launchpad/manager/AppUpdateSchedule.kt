package com.kimjisub.launchpad.manager

import android.content.Context
import android.content.Context.MODE_PRIVATE
import androidx.core.content.edit
import kotlin.reflect.KMutableProperty0

/**
 * Decides when the pack list may bring up a new version on its own. Checking by hand is never
 * limited by it. The marks outlive restarts and are not tied to a version, so a newer build
 * appearing during a rest does not end it. A clock moved backwards restarts the rest from now.
 */
class AppUpdateSchedule(
	private val store: Store,
	private val now: () -> Long = System::currentTimeMillis,
) {
	interface Store {
		var lastAutoCheckAt: Long?
		var declinedAt: Long?
		var installPostponedAt: Long?

		/** The person accepted a download whose end has not been seen yet. */
		var downloadRequested: Boolean
	}

	var downloadRequested: Boolean
		get() = store.downloadRequested
		set(value) {
			store.downloadRequested = value
		}

	fun isAutoCheckDue(): Boolean = !isResting(store::lastAutoCheckAt, CHECK_INTERVAL_MS)

	fun markAutoCheck() {
		store.lastAutoCheckAt = now()
	}

	fun mayOfferDownload(): Boolean = !isResting(store::declinedAt, DECLINE_REST_MS)

	fun markDeclined() {
		store.declinedAt = now()
	}

	fun mayOfferInstall(): Boolean = !isResting(store::installPostponedAt, INSTALL_REST_MS)

	fun markInstallPostponed() {
		store.installPostponedAt = now()
	}

	private fun isResting(mark: KMutableProperty0<Long?>, restMs: Long): Boolean {
		val since = mark.get() ?: return false
		val current = now()
		if (current < since) {
			mark.set(current)
			return true
		}
		return current - since < restMs
	}

	companion object {
		private const val HOUR_MS = 60L * 60 * 1000
		const val CHECK_INTERVAL_MS = 24 * HOUR_MS
		const val DECLINE_REST_MS = 7 * 24 * HOUR_MS
		const val INSTALL_REST_MS = 24 * HOUR_MS
	}
}

class PreferenceAppUpdateStore(context: Context) : AppUpdateSchedule.Store {
	private val pref = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE)

	override var lastAutoCheckAt: Long?
		get() = getTime(KEY_LAST_AUTO_CHECK)
		set(value) = putTime(KEY_LAST_AUTO_CHECK, value)

	override var declinedAt: Long?
		get() = getTime(KEY_DECLINED)
		set(value) = putTime(KEY_DECLINED, value)

	override var installPostponedAt: Long?
		get() = getTime(KEY_INSTALL_POSTPONED)
		set(value) = putTime(KEY_INSTALL_POSTPONED, value)

	override var downloadRequested: Boolean
		get() = pref.getBoolean(KEY_DOWNLOAD_REQUESTED, false)
		set(value) = pref.edit { putBoolean(KEY_DOWNLOAD_REQUESTED, value) }

	private fun getTime(key: String): Long? = if (pref.contains(key)) pref.getLong(key, 0) else null

	private fun putTime(key: String, value: Long?) = pref.edit {
		if (value != null) putLong(key, value) else remove(key)
	}

	private companion object {
		const val PREF_NAME = "app_update"
		const val KEY_LAST_AUTO_CHECK = "last_auto_check_at"
		const val KEY_DECLINED = "declined_at"
		const val KEY_INSTALL_POSTPONED = "install_postponed_at"
		const val KEY_DOWNLOAD_REQUESTED = "download_requested"
	}
}
