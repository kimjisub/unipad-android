package com.kimjisub.launchpad.db.repository

import android.database.sqlite.SQLiteException
import androidx.lifecycle.LiveData
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.kimjisub.launchpad.db.dao.UnipackDao
import com.kimjisub.launchpad.db.ent.Unipack
import com.kimjisub.launchpad.tool.Log
import java.util.Date

class UnipackRepository(
	private val unipackDao: UnipackDao,
	private val reportWriteFailure: (SQLiteException) -> Unit = { FirebaseCrashlytics.getInstance().recordException(it) },
) {
	fun find(id: String): LiveData<Unipack> {
		return unipackDao.find(id)
	}

	fun getOrCreate(id: String): LiveData<Unipack> {
		recordOrSkip("create $id") {
			if (!unipackDao.exists(id)) {
				unipackDao.insert(Unipack.create(id))
			}
		}
		return unipackDao.find(id)
	}

	fun toggleBookmark(id: String) {
		recordOrSkip("toggle bookmark $id") {
			unipackDao.toggleBookmark(id)
		}
	}

	fun totalOpenCount(): LiveData<Long> {
		return unipackDao.totalOpenCount()
	}

	fun openCount(id: String): LiveData<Long> {
		return unipackDao.openCount(id)
	}

	fun lastOpenedAt(id: String): LiveData<Date> {
		return unipackDao.lastOpenedAt(id)
	}

	fun recordOpen(id: String) {
		recordOrSkip("record open $id") {
			unipackDao.addOpenCount(id)
			unipackDao.setLastOpenedAt(id, Date())
		}
	}

	// Returns true when no row is left for the id.
	fun delete(id: String): Boolean {
		unipackDao.delete(id)
		return !unipackDao.exists(id)
	}

	// The play count, bookmark and row are extras: a full or failing disk skips them and is
	// reported, instead of closing the app from a background coroutine.
	private inline fun recordOrSkip(what: String, write: () -> Unit) {
		try {
			write()
		} catch (e: SQLiteException) {
			Log.err("UnipackRepository: skipped $what", e)
			reportWriteFailure(e)
		}
	}
}
